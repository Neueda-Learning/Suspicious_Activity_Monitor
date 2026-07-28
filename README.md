# AML Alert Triage

UK-context corporate AML alert triage demo (Spring Boot 3.3 / Java 17 / PostgreSQL 16).

> The score determines the order. The evidence determines the decision. AI helps connect the two.

## Run

```bash
./run.sh
```

The script pins the JDK and waits for Postgres. Do not skip it: Lombok cannot run under JDK 23,
so on a machine whose default JDK is 23 a plain `mvn compile` fails with a wall of
`cannot find symbol` errors. `run.sh` selects JDK 17 explicitly.

Open:

- UI: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Bonus screen API: `POST /api/screen`

## Resetting between demo runs

```bash
curl -X POST http://localhost:8080/api/demo/reset
```

Truncates and re-seeds in one transaction. Required before re-running the sanctions
list-update flow, which is only meaningful against a list that has not been synced yet.

## Optional LLM

Ollama at `localhost:11434` with model `qwen3:4b`. If unavailable, AI drafts silently fall back to the template (`fallbackUsed=true`).

## Demo notes

- Actor fixed as `sarah.chen`; user management is deliberately out of scope
- Sanctions data is a bulk extract of the OpenSanctions `gb_fcdo_sanctions` dataset
  (UK sanctions data originally published by FCDO), cached locally as `list-v1.json` /
  `list-v2.json`. OpenSanctions data is CC BY-NC 4.0 — attribution required, non-commercial
  use only. Screening resolves against the local index; no third party is called in the
  payment path.
- External FX (Frankfurter) and country (REST Countries) data are cached at startup; request paths stay offline
- All customers, accounts and transactions are synthetic and generated at runtime

## Scope

AML monitoring and sanctions screening are modelled as two separate control chains: screening is
synchronous and holds the payment, monitoring is asynchronous and raises alerts. A sanctions signal
never contributes to the AML priority score.

The priority score orders the queue; it is not a determination about any customer. Escalation
creates a CRR review task and an interim monitoring flag — it never changes a customer's risk
rating, which requires an authorised reviewer.

Not implemented: RBAC, maker-checker approval, external reporting beyond internal escalation,
list-update rescreening of historical transactions, and threshold tuning governance.
