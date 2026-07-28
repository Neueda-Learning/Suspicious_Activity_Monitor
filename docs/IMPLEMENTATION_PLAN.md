# AML Alert Triage — Implementation Plan

> **Scope**: UK context, GBP, corporate cross-border payments.
> **Deadline**: Friday PM. Build Mon–Thu, code freeze Thu 12:00.
> **Core line**: *The score determines the order. The evidence determines the decision. AI helps connect the two.*

---

## 0. Tech Stack

| Layer | Choice | Note |
|---|---|---|
| Language | Java 21 | |
| Framework | Spring Boot 3.3.x | web, data-jpa, validation |
| DB | PostgreSQL 16 (Docker Compose) | 若培训用的是别的，就用培训那个，别换 |
| Schema | Flyway | `V1__baseline.sql`，一次写完 |
| API docs | springdoc-openapi | `/swagger-ui.html` |
| Frontend | 原生 HTML + JS + Chart.js | **无构建步骤**，放 `src/main/resources/static` |
| HTTP client | Spring `RestClient` | |
| LLM | Ollama `localhost:11434` | 模板兜底优先实现 |

不使用：Kafka、Redis、微服务、React 构建链、任何版本管理框架。

---

## 1. Data Model — 8 张扁平表

所有历史证据以 **JSONB 快照**保存。不建版本表。

### 1.1 `customer`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigserial PK | |
| `customer_ref` | varchar UK | `CUS-000123` |
| `name` | varchar | |
| `segment` | varchar | `CORPORATE` / `RETAIL`（仅用 CORPORATE，保留扩展点） |
| `legal_form` | varchar | Ltd / PLC |
| `industry` | varchar | Trading / Logistics / Consultancy |
| `incorporation_country` | char(2) | ISO2, 多数 `GB` |
| `registration_date` | date | |
| `crr` | varchar | `LOW` / `MEDIUM` / `HIGH` — **输入字段，KYC 后生成，系统不自动改** |
| `monitoring_status` | varchar | `NORMAL` / `PENDING_REVIEW` ← Beat 3b 即时可见 |
| `created_at` | timestamptz | |

> **不存基线字段**。基线由规则引擎在评估时查询计算，并快照进 `alert.evidence_snapshot`。

### 1.2 `transaction`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigserial PK | |
| `txn_ref` | varchar UK | |
| `customer_id` | bigint FK | |
| `account_ref` | varchar | 账户级，R2 fan-out 需要 |
| `direction` | varchar | `INBOUND` / `OUTBOUND` |
| `amount` | numeric(18,2) | 原币 |
| `currency` | char(3) | |
| `amount_gbp` | numeric(18,2) | 归一化，用于所有规则计算 |
| `fx_rate_used` | numeric(18,8) | |
| `fx_rate_date` | date | **用交易当日汇率，不用最新汇率** |
| `counterparty_name` | varchar | |
| `counterparty_ref` | varchar | 用于判定 new counterparty |
| `counterparty_country` | char(2) | |
| `executed_at` | timestamptz | **所有时间窗规则的基准** |
| `created_at` | timestamptz | |

### 1.3 `alert`

一条规则命中 = 一条 alert。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigserial PK | |
| `customer_id` | bigint FK | |
| `case_id` | bigint FK NULL | 归并后回填 |
| `rule_code` | varchar | `R1`–`R4` |
| `rule_name` | varchar | |
| `strength` | numeric(4,3) | 0.000–1.000 |
| `points` | int | `round(weight × strength)` |
| `rule_params_snapshot` | jsonb | 当时的阈值/权重原样存下 |
| `evidence_snapshot` | jsonb | 支撑数字 + 涉及的 txn id 列表 |
| `window_start` / `window_end` | timestamptz | |
| `created_at` | timestamptz | |

`evidence_snapshot` 示例（R1）:
```json
{
  "windowInboundGbp": 84200.00,
  "baselineMedianGbp": 10024.00,
  "baselineSampleSize": 137,
  "baselineDays": 90,
  "ratio": 8.40,
  "transactionIds": [4411, 4412, 4413]
}
```

### 1.4 `case`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigserial PK | |
| `case_ref` | varchar UK | `CASE-2026-0042` |
| `customer_id` | bigint FK | |
| `priority_score` | int | **冻结保存**，不因后续阈值变化重算 |
| `priority_band` | varchar | `RED` ≥70 / `AMBER` 40–69 / `GREEN` <40 |
| `status` | varchar | `OPEN` / `CLOSED_NFA` / `CLOSED_FALSE_POSITIVE` / `ESCALATED_INTERNAL` |
| `crr_review_required` | boolean | 升级时置 true |
| `prior_recent_cases` | int | 该主体 90 天内已关闭案件数 |
| `assigned_to` | varchar | `sarah.chen` |
| `sla_due_at` | timestamptz | 队列"超期 2"要用 |
| `window_start` / `window_end` | timestamptz | 归并窗口 |
| `disposition_reason` | text | |
| `disposed_by` / `disposed_at` | | |
| `opened_at` | timestamptz | |

> `priority_band` 文案：**GREEN = Low Priority，不是 Safe**。

### 1.5 `audit_event` — 通用结构

| 字段 | 类型 |
|---|---|
| `id` | bigserial PK |
| `actor` | varchar |
| `occurred_at` | timestamptz |
| `action` | varchar |
| `entity_type` | varchar — `CASE` / `CUSTOMER` / `SANCTIONS_HIT` / `AI_DRAFT` / `SANCTIONS_LIST` |
| `entity_id` | bigint |
| `details_json` | jsonb |

`action` 取值：
```
CASE_OPENED  CASE_VIEWED  CASE_DISPOSED  CASE_ESCALATED
CUSTOMER_MONITORING_FLAGGED  CRR_REVIEW_CREATED
AI_DRAFT_GENERATED  AI_DRAFT_EDITED_AND_ACCEPTED
PAYMENT_SCREENED  PAYMENT_HELD  PAYMENT_RELEASED
SANCTIONS_HIT_RESOLVED  SANCTIONS_LIST_IMPORTED  CUSTOMERS_RESCREENED
```

### 1.6 `ai_draft`

| 字段 | 类型 |
|---|---|
| `id` | bigserial PK |
| `case_id` | bigint FK |
| `input_snapshot_json` | jsonb — 喂给模型的全部证据 |
| `raw_output` | text — **模型原始输出，不可改** |
| `analyst_final_text` | text — Sarah 修改后 |
| `model_metadata_json` | jsonb — 模型名、prompt 版本、温度、耗时、是否走了兜底 |
| `generated_at` / `edited_by` / `edited_at` | |
| `status` | `GENERATED` / `ACCEPTED` |

### 1.7 `sanctions_entry`

| 字段 | 类型 |
|---|---|
| `id` | bigserial PK |
| `source_unique_id` | varchar — UK Sanctions List Unique ID |
| `name` | varchar |
| `entity_type` | `INDIVIDUAL` / `ENTITY` |
| `aliases_json` | jsonb |
| `identifiers_json` | jsonb — DOB / nationality / passport / company number |
| `measures_json` | jsonb — asset freeze 等适用措施 |
| `source_updated_at` | timestamptz |
| `import_batch_id` | varchar |
| `active` | boolean |

### 1.8 `sanctions_hit`

| 字段 | 类型 |
|---|---|
| `id` | bigserial PK |
| `trigger_type` | `PAYMENT_SCREENING` / `LIST_UPDATE_RESCREEN` |
| `payment_txn_id` | bigint NULL |
| `customer_id` | bigint NULL |
| `screened_name` | varchar |
| `sanctions_entry_id` | bigint FK |
| `name_similarity` | numeric(4,3) |
| `status` | `POTENTIAL_MATCH` / `FALSE_MATCH` / `TARGET_MATCH` / `RELEASED` |
| `match_details_snapshot` | jsonb — 多属性逐项比对 |
| `sanctions_entry_snapshot` | jsonb — 命中时名单条目全文 |
| `resolution_rationale` | text — **必填才能 resolve** |
| `resolved_by` / `resolved_at` | |

`match_details_snapshot` 示例:
```json
{
  "name":        { "input": "Vladmir Petrov", "listed": "Vladimir Petrov", "similarity": 0.94, "algorithm": "jaro-winkler" },
  "dateOfBirth": { "input": "1971-03-02", "listed": "1971-03-02", "match": "EXACT" },
  "nationality": { "input": "RU", "listed": "RU", "match": "EXACT" },
  "passport":    { "input": null, "listed": "71********", "match": "NOT_PROVIDED" },
  "entityType":  { "input": "INDIVIDUAL", "listed": "INDIVIDUAL", "match": "EXACT" },
  "overall": "POTENTIAL_MATCH"
}
```

---

## 2. Rules — 权重与强度

权重之和 = 100，**天然封顶**。`points = round(weight × strength)`。

| Code | 名称 | Weight | 触发条件 | strength |
|---|---|---|---|---|
| **R1** | Amount deviation | **35** | 24h 流入总额 ÷ 90 天日流入中位数 ≥ 2.0 | `clamp((ratio-2)/8, 0, 1)` |
| **R2** | Rapid dispersal | **30** | 入账后 ≤24h 内转出 ≥60% 且对手方 ≥3 | `clamp(dispersalRatio,0,1) × clamp((24-hours)/24,0,1)` |
| **R3** | New counterparties | **20** | 窗口内出现 ≥2 个 90 天内无往来的对手方 | `clamp(newCount/5, 0, 1)` |
| **R4** | Higher-risk jurisdiction | **15** | 窗口内有转出至内部高风险辖区清单 | `clamp(2×valueToHighRisk/totalOutbound, 0, 1)` |

> ⚠️ **不要把 92 分写死进 PPT。** 最终分由模型算出，ACME 大概会落在 85–92。等周三种子数据调好之后，用**实际算出来的值**写进讲稿和幻灯片。

**归并（Consolidation）**
- Group key: `customer_id`
- Window: 滚动 24h
- 已有 `OPEN` case → 并入并重算 `priority_score`
- 已 `CLOSED` → 开新 case，`prior_recent_cases` = 该主体 90 天内已关闭案件数
- **同一 rule_code 在窗口内多次触发 → 取最高 strength，不累加**

高风险辖区清单：内部维护的静态表（源自 FATF 公开名单），`resources/high-risk-jurisdictions.json`。

---

## 3. REST API

### Case triage
```
GET    /api/cases?status=OPEN&sort=priority
       → { totals: { alerts, cases, assignedToMe, overdue }, items: [...] }

GET    /api/cases/{id}
       → case + customer + alerts[] + timeline[] + aiDraft + auditEvents[]

POST   /api/cases/{id}/disposition
       body { decision: CLOSED_NFA | CLOSED_FALSE_POSITIVE | ESCALATED_INTERNAL,
              rationale: string }   ← rationale 必填
       → 事务内完成：
         case.status / crr_review_required
         customer.monitoring_status = PENDING_REVIEW（仅 ESCALATED）
         audit_event × 2–3
```

### AI
```
POST   /api/cases/{id}/ai-draft        → 生成
PUT    /api/ai-drafts/{id}             body { analystFinalText } → ACCEPTED + audit
```

### Sanctions
```
POST   /api/payments                   同步筛查
       body { customerRef, beneficiaryName, beneficiaryCountry, amount, currency,
              beneficiaryDob?, beneficiaryNationality? }
       → { status: RELEASED | HELD, sanctionsHitId? }

GET    /api/sanctions-hits?status=POTENTIAL_MATCH
POST   /api/sanctions-hits/{id}/resolve
       body { outcome: FALSE_MATCH | TARGET_MATCH, rationale }   ← rationale 必填

POST   /api/sanctions/sync             加载 list-v2
       → { added, updated, customersRescreened, potentialMatchesCreated }
```

### Bonus（PDF 明确加分项）
```
POST   /api/screen                     对外开放，讲师可用 Postman 打
       body { name, country, dateOfBirth? }
       → { outcome, similarity, matchedEntryId?, measures? }
```

### P2
```
GET    /api/stats/rules                各规则触发次数与处置分布
```

---

## 4. 外部 API

> ⚠️ **PDF 硬性要求「至少一个外部 API 集成」。UK Sanctions List 走的是静态文件导入，不算 API 调用。所以下面两个必须真调。**

| 用途 | 服务 | Key | 调用方式 |
|---|---|---|---|
| **汇率归一化 → GBP** | **Frankfurter** `api.frankfurter.app/{date}?from=X&to=GBP` | 不需要 | 启动时 + `@Scheduled` 拉取，落 `fx_rate` 缓存表；**用交易当日历史汇率** |
| **国家信息富化** | **REST Countries** `restcountries.com/v3.1/alpha/{code}` | 不需要 | 启动时全量拉一次落库，供 UI 显示国家全名/地区 |
| 制裁名单 | UK Sanctions List（gov.uk 发布 CSV/XML） | — | **离线转成 `list-v1.json` / `list-v2.json` 提交进仓库** |
| 高风险辖区 | FATF 公开名单 | — | 静态 JSON |
| LLM | Ollama 本地 | — | `POST localhost:11434/api/generate` |

**铁律：演示时任何请求路径都不得触发外网调用。** 所有外部数据在启动/定时任务中预先落库。

**本地模型的说法**：案件叙述含客户身份与交易明细，本地部署确保数据不出内网 —— 不是为了省钱。

---

## 5. AI 契约

**输出必须是结构化 JSON**，不是自由文本：

```json
{
  "narrative": "3-4 句资金流向叙述",
  "confirmedObservations": [
    { "statement": "...", "evidenceIds": ["ALERT-101", "TXN-4411"] }
  ],
  "unexplainedQuestions": ["..."],
  "suggestedNextChecks": ["..."]
}
```

**硬性约束（写进 prompt 且在 PPT 上声明）** — AI 永远不能：
修改风险分数 · 自动关闭或升级案件 · 修改 CRR · 判定"这是洗钱" · 自动提交申报 · 修改规则。

**三重兜底**（按顺序实现）：
1. **先写模板拼装版本**，不接 LLM 也能出草稿 —— AI 是纯增量
2. 结构化 JSON 输出 + 严格限长
3. 超时/解析失败 → 静默回退模板，`model_metadata_json.fallbackUsed = true`

**演示动作**：让 AI 产出一句过度推断，Sarah 当场改掉。
> AI: *"This may constitute layering."*
> Sarah: *"This shows rapid dispersal of funds and requires further investigation."*

两条 audit event 记录全过程。这是全场最有力的一个动作。

---

## 6. 种子数据（周一必须完成，它阻塞所有人）

- **50 个企业客户**：GB 注册为主，含 CRR 分布、行业、开户日期
- **60 天 · 约 2000 笔**跨境汇款，金额取对数正态分布
- **ACME Trading 主线**（演示日前一天）：
  - 3 笔流入合计 £84,200
  - 48 分钟内 92% 转出给 5 个对手方，其中 4 个为新对手方
  - 1 笔流向高风险辖区
- **另外 4–6 个红/黄案件**，让队列不空
- **2 个接近 SLA** 的案件（队列顶部"超期 2"）
- **制裁名单 v1**（约 200 条，含 `Vladimir Petrov` 及别名）
- **制裁名单 v2** = v1 + 12 条新增，其中 1 条命中一个**长期无交易的存量客户**

---

## 7. 页面

| # | 页面 | 优先级 | 内容 |
|---|---|---|---|
| 1 | Case Queue | 🔴 P0 | 计数条（47 alerts · 20 cases · 12 assigned · 2 overdue）、排序、红黄绿 |
| 2 | Case Detail | 🔴 P0 | 客户卡片 · 分数分解（可展开证据）· 时间线 · AI 草稿区 · 处置区 · **审计记录内嵌** |
| 3 | Sanctions Review | 🔴 P0 | 多属性比对表 · resolve（必填理由） |
| 4 | Payment Submit | 🔴 P0 | 一个弹窗即可 |
| 5 | Rule Stats | 🟢 P2 | Chart.js 两张图 |

**火力集中在页面 2**，它承载 4 个 journey 阶段。其他可以糙。

**已删除**：批量关闭 · 规则生产修改 · 自动 CRR 变更。

---

## 8. 日程

| | 目标 | 完成标志 |
|---|---|---|
| **周一** | 骨架 + 数据 | pom / Flyway / 8 表建好 · **种子数据生成器跑通** · 前端能渲染案件列表（假数据也行） |
| **周二** | 核心链路 | 4 条规则 + 评分 + 归并 · 案件详情页 + 分数分解 + 时间线 · 制裁模糊匹配 + Payment Held |
| **周三** | 差异化 | AI（模板先行）· 名单同步重筛 · 处置 + PENDING_REVIEW 标记 · Swagger + `/api/screen` |
| **周四 12:00** | ⚠️ **代码冻结** | 之后只做：调种子数据 · **录屏** · 排练 ×3 · PPT 定稿 |

**周四下午一行代码都不写。**

---

## 9. 风险兜底

| 风险 | 措施 |
|---|---|
| LLM 现场翻车 | 模板兜底 + 限长 + 超时静默回退，界面无差别 |
| 外网不可用 | 全部外部数据预先落库，演示零外网 |
| 环境炸了 | **完整录屏**。demo 挂了立刻切，一句"我们先看录制版本"带过 |
| 超时 | 排练 3 次掐表，第一次必定超 |
| 数据不好看 | 周四下午专门调种子数据 |

---

## 10. 术语纪律

| ❌ 不要出现 | ✅ 用 |
|---|---|
| Fraud detected / criminal / guilty | Unusual activity identified |
| Auto-reject / Blocked | Payment held pending review |
| Sanctions match | **Potential** sanctions match |
| Green = Safe | Green = **Low Priority** |
| 案件"清白" | Closed – No Further Action |
| RLHF / 在线学习 | Human-in-the-loop · governed feedback |

页脚常驻一行：
> *The system does not determine guilt. It identifies unusual activity and provides investigators with explainable evidence.*
