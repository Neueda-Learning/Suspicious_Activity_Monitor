/* ==========================================================================
   Mock fixtures. Shapes match the existing DTOs exactly, so porting means
   replacing `MOCK.x` with `await api('/api/…')` and nothing else.

   The numbers are internally consistent: the ACME transactions below really
   do sum to the evidence figures, and the four alerts really do sum to the
   case priority score (30 + 24 + 14 + 9 = 77).
   ========================================================================== */

const DAY = 86400000, HOUR = 3600000, MIN = 60000;

/** Today at the given UTC hour/minute, offset by `dayDelta` days. */
function at(dayDelta, h, m = 0) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + dayDelta);
  d.setUTCHours(h, m, 0, 0);
  return d.toISOString();
}

const MOCK = {};

/* -------------------------------------------------------------------------
   Case queue  —  GET /api/cases?status=OPEN&sort=priority
   ------------------------------------------------------------------------- */
MOCK.queue = {
  totals: { alerts: 47, cases: 12, assignedToMe: 5, overdue: 2 },
  items: [
    { id: 1,  caseRef: 'CASE-2026-0142', customerRef: 'CUST-000001', customerName: 'ACME Trading Ltd',
      priorityScore: 77, priorityBand: 'RED',   status: 'OPEN', assignedTo: 's.whitfield',
      slaDueAt: at(0, 18, 0),  openedAt: at(0, 2, 0),  overdue: false, alertCount: 4 },
    { id: 2,  caseRef: 'CASE-2026-0139', customerRef: 'CUST-000014', customerName: 'Northgate Goods Ltd',
      priorityScore: 71, priorityBand: 'RED',   status: 'OPEN', assignedTo: 'd.okafor',
      slaDueAt: at(-1, 9, 0),  openedAt: at(-2, 2, 0), overdue: true,  alertCount: 3 },
    { id: 3,  caseRef: 'CASE-2026-0137', customerRef: 'CUST-000008', customerName: 'Atlas Commerce Ltd',
      priorityScore: 64, priorityBand: 'RED',   status: 'OPEN', assignedTo: 's.whitfield',
      slaDueAt: at(0, 21, 30), openedAt: at(-1, 2, 0), overdue: false, alertCount: 3 },
    { id: 4,  caseRef: 'CASE-2026-0136', customerRef: 'CUST-000022', customerName: 'Harbour Partners Ltd',
      priorityScore: 58, priorityBand: 'AMBER', status: 'OPEN', assignedTo: 'r.mensah',
      slaDueAt: at(-1, 14, 0), openedAt: at(-3, 2, 0), overdue: true,  alertCount: 2 },
    { id: 5,  caseRef: 'CASE-2026-0134', customerRef: 'CUST-000031', customerName: 'Summit Advisory Ltd',
      priorityScore: 52, priorityBand: 'AMBER', status: 'OPEN', assignedTo: 's.whitfield',
      slaDueAt: at(1, 11, 0),  openedAt: at(-1, 2, 0), overdue: false, alertCount: 2 },
    { id: 6,  caseRef: 'CASE-2026-0133', customerRef: 'CUST-000006', customerName: 'Pacific Markets Ltd',
      priorityScore: 49, priorityBand: 'AMBER', status: 'OPEN', assignedTo: 'd.okafor',
      slaDueAt: at(1, 16, 0),  openedAt: at(-1, 2, 0), overdue: false, alertCount: 2 },
    { id: 7,  caseRef: 'CASE-2026-0131', customerRef: 'CUST-000017', customerName: 'Bright Freight Ltd',
      priorityScore: 44, priorityBand: 'AMBER', status: 'OPEN', assignedTo: null,
      slaDueAt: at(2, 9, 0),   openedAt: at(0, 2, 0),  overdue: false, alertCount: 2 },
    { id: 8,  caseRef: 'CASE-2026-0130', customerRef: 'CUST-000042', customerName: 'Crown Solutions Ltd',
      priorityScore: 41, priorityBand: 'AMBER', status: 'OPEN', assignedTo: 's.whitfield',
      slaDueAt: at(2, 13, 0),  openedAt: at(0, 2, 0),  overdue: false, alertCount: 1 },
    { id: 9,  caseRef: 'CASE-2026-0128', customerRef: 'CUST-000011', customerName: 'Silver Commerce Ltd',
      priorityScore: 33, priorityBand: 'GREEN', status: 'OPEN', assignedTo: 'r.mensah',
      slaDueAt: at(3, 10, 0),  openedAt: at(-1, 2, 0), overdue: false, alertCount: 1 },
    { id: 10, caseRef: 'CASE-2026-0127', customerRef: 'CUST-000029', customerName: 'Vertex Exports Ltd',
      priorityScore: 28, priorityBand: 'GREEN', status: 'OPEN', assignedTo: 'd.okafor',
      slaDueAt: at(3, 15, 0),  openedAt: at(-1, 2, 0), overdue: false, alertCount: 1 },
    { id: 11, caseRef: 'CASE-2026-0125', customerRef: 'CUST-000003', customerName: 'Oak Partners Ltd',
      priorityScore: 24, priorityBand: 'GREEN', status: 'OPEN', assignedTo: 's.whitfield',
      slaDueAt: at(4, 11, 0),  openedAt: at(-2, 2, 0), overdue: false, alertCount: 1 },
    { id: 12, caseRef: 'CASE-2026-0124', customerRef: 'CUST-000038', customerName: 'Northern Peak Holdings Ltd',
      priorityScore: 21, priorityBand: 'GREEN', status: 'OPEN', assignedTo: null,
      slaDueAt: at(4, 17, 0),  openedAt: at(-2, 2, 0), overdue: false, alertCount: 1 },
  ]
};

/* -------------------------------------------------------------------------
   Case detail  —  GET /api/cases/{id}
   ------------------------------------------------------------------------- */
MOCK.caseDetail = {
  caseRecord: {
    id: 1, caseRef: 'CASE-2026-0142', status: 'OPEN', assignedTo: 's.whitfield',
    priorityScore: 77, priorityBand: 'RED',
    slaDueAt: at(0, 18, 0),
    windowStart: at(-1, 2, 0), windowEnd: at(0, 2, 0),
    priorRecentCases: 0, crrReviewRequired: false,
    openedBy: 'Monitoring run', openedAt: at(0, 2, 0)
  },
  customer: {
    id: 1, name: 'ACME Trading Ltd', customerRef: 'CUST-000001', crr: 'MEDIUM',
    industry: 'Trading', legalForm: 'PRIVATE_LIMITED',
    incorporationCountry: 'GB', incorporationCountryName: 'United Kingdom',
    monitoringStatus: 'PENDING_REVIEW'
  },
  focusTxnRef: 'PMT-2026-004417',

  alerts: [
    {
      id: 501, ruleCode: 'R1', ruleName: 'Amount deviation',
      strength: 0.86, points: 30, ruleParamsSnapshot: { weight: 35 },
      evidenceSnapshot: {
        windowInboundGbp: 54300, baselineMedianGbp: 6200,
        baselineInboundTxnCount: 214, baselineActiveDays: 88, ratio: 8.76
      }
    },
    {
      id: 502, ruleCode: 'R2', ruleName: 'Rapid dispersal',
      strength: 0.79, points: 24, ruleParamsSnapshot: { weight: 30 },
      evidenceSnapshot: {
        inboundGbp: 54300, outboundGbp: 43000, dispersalRatio: 0.79,
        counterpartyCount: 5, hoursToDisperse: 0.9
      }
    },
    {
      id: 503, ruleCode: 'R3', ruleName: 'New counterparties',
      strength: 0.70, points: 14, ruleParamsSnapshot: { weight: 20 },
      evidenceSnapshot: {
        newCount: 4,
        newCounterparties: ['CP-NEW-01', 'CP-NEW-02', 'CP-NEW-03', 'CP-NEW-04']
      }
    },
    {
      id: 504, ruleCode: 'R4', ruleName: 'Higher-risk jurisdiction',
      strength: 0.62, points: 9, ruleParamsSnapshot: { weight: 15 },
      evidenceSnapshot: {
        valueToHighRisk: 26000, totalOutbound: 43000,
        highRiskTransferCount: 1, highRiskCountries: ['NG']
      }
    }
  ],

  /* Inbound 18,400 + 21,900 + 14,000 = 54,300  ✓ matches R1/R2 evidence
     Outbound 9,200 + 26,000 + 3,400 + 2,600 + 1,800 = 43,000  ✓ matches R2/R4 */
  timeline: [
    { txnRef: 'PMT-2026-004402', executedAt: at(-1, 16, 10), direction: 'INBOUND',  amountGbp: 18400,
      counterpartyName: 'Meridian Supply Co', counterpartyCountry: 'DE', status: 'EXECUTED' },
    { txnRef: 'PMT-2026-004405', executedAt: at(-1, 16, 18), direction: 'INBOUND',  amountGbp: 21900,
      counterpartyName: 'Halden Group',       counterpartyCountry: 'SE', status: 'EXECUTED' },
    { txnRef: 'PMT-2026-004409', executedAt: at(-1, 16, 24), direction: 'INBOUND',  amountGbp: 14000,
      counterpartyName: 'Crestline Capital',  counterpartyCountry: 'LU', status: 'EXECUTED' },
    { txnRef: 'PMT-2026-004412', executedAt: at(-1, 16, 41), direction: 'OUTBOUND', amountGbp: 9200,
      counterpartyName: 'Kestrel Partners',   counterpartyCountry: 'US', status: 'EXECUTED' },
    { txnRef: 'PMT-2026-004417', executedAt: at(-1, 16, 52), direction: 'OUTBOUND', amountGbp: 26000,
      counterpartyName: 'Sahel Logistics NG', counterpartyCountry: 'NG', status: 'EXECUTED' },
    { txnRef: 'PMT-2026-004421', executedAt: at(-1, 16, 58), direction: 'OUTBOUND', amountGbp: 3400,
      counterpartyName: 'Lumen Trade BV',     counterpartyCountry: 'NL', status: 'EXECUTED' },
    { txnRef: 'PMT-2026-004424', executedAt: at(-1, 17, 1),  direction: 'OUTBOUND', amountGbp: 2600,
      counterpartyName: 'Delta Consulting',   counterpartyCountry: 'CY', status: 'EXECUTED' },
    { txnRef: 'PMT-2026-004428', executedAt: at(-1, 17, 5),  direction: 'OUTBOUND', amountGbp: 1800,
      counterpartyName: 'Vale Holdings',      counterpartyCountry: 'IE', status: 'EXECUTED' }
  ],

  aiDraft: null,

  auditEvents: [
    { occurredAt: at(-1, 16, 52), actor: 'payment-gateway', action: 'PAYMENT_SCREENED',
      detailsJson: { txnRef: 'PMT-2026-004417', outcome: 'NO_POTENTIAL_MATCH' } },
    { occurredAt: at(-1, 16, 52), actor: 'payment-gateway', action: 'PAYMENT_RELEASED',
      detailsJson: { txnRef: 'PMT-2026-004417', amountGbp: 26000 } },
    { occurredAt: at(0, 2, 0),   actor: 'monitoring-run',  action: 'CASE_OPENED',
      detailsJson: { caseRef: 'CASE-2026-0142', priorityScore: 77, rules: 'R1,R2,R3,R4' } },
    { occurredAt: at(0, 2, 0),   actor: 'monitoring-run',  action: 'CUSTOMER_MONITORING_FLAGGED',
      detailsJson: { customerRef: 'CUST-000001', status: 'PENDING_REVIEW' } },
    { occurredAt: at(0, 8, 41),  actor: 's.whitfield',     action: 'CASE_VIEWED',
      detailsJson: { caseRef: 'CASE-2026-0142' } }
  ]
};

/** The draft the "Generate" button resolves to. Shape mirrors AiDraftEntity. */
MOCK.aiDraftResponse = {
  id: 88,
  status: 'DRAFT',
  editedBy: null,
  editedAt: null,
  analystFinalText: null,
  modelMetadataJson: { model: 'llama3.1:8b', fallbackUsed: false, elapsedMs: 2140 },
  rawOutput: JSON.stringify({
    narrative:
      'Over a 55-minute window on the afternoon of the alert date, ACME Trading Ltd received ' +
      'three inbound credits totalling £54,300 — approximately 8.8× the customer\'s 90-day ' +
      'median daily inbound of £6,200. Within the same hour, £43,000 was dispersed across five ' +
      'outbound payments to five separate beneficiaries, four of which had no prior relationship ' +
      'with the account. The largest single outflow, £26,000 to Sahel Logistics NG, was directed ' +
      'to a higher-risk jurisdiction. The pattern — concentrated inflow followed immediately by ' +
      'fragmented outflow to first-time beneficiaries — is consistent with pass-through activity ' +
      'and is not explained by the customer\'s recorded trading profile.',
    confirmedObservations: [
      { statement: 'Inbound value in the 24-hour window reached £54,300 against a 90-day median daily inbound of £6,200 (ratio 8.76×).',
        evidenceIds: ['R1-ALERT-501', 'PMT-2026-004402', 'PMT-2026-004405', 'PMT-2026-004409'] },
      { statement: '£43,000 (79% of the inbound value) left the account within 0.9 hours, split across five beneficiaries.',
        evidenceIds: ['R2-ALERT-502', 'PMT-2026-004412', 'PMT-2026-004417'] },
      { statement: 'Four of the five outbound beneficiaries had no transaction history with this customer.',
        evidenceIds: ['R3-ALERT-503'] },
      { statement: 'A single £26,000 transfer went to Nigeria, a higher-risk jurisdiction on the configured list.',
        evidenceIds: ['R4-ALERT-504', 'PMT-2026-004417'] },
      { statement: 'The Sahel Logistics NG payment passed sanctions screening with no potential list match before execution.',
        evidenceIds: ['PMT-2026-004417', 'CP-NEW-99'] }
    ],
    unexplainedQuestions: [
      'What commercial relationship links ACME Trading Ltd to the four first-time beneficiaries?',
      'Is the concentration of three large credits on a single afternoon consistent with any declared contract or settlement cycle?',
      'Why was 79% of the inbound value moved on before the end of the same business hour?'
    ],
    suggestedNextChecks: [
      'Request invoices or contracts supporting the £26,000 payment to Sahel Logistics NG.',
      'Compare the three inbound remitters against the customer\'s declared trading counterparties.',
      'Review whether a comparable inflow-then-dispersal pattern appears earlier in the 90-day history.',
      'Confirm whether the CRR of MEDIUM remains appropriate given the jurisdiction exposure.'
    ]
  })
};

/* -------------------------------------------------------------------------
   Live demo  —  GET /api/demo/scenario
   ------------------------------------------------------------------------- */
MOCK.demoStates = {
  READY: { step: 'READY' },

  PAYMENT_A_RELEASED: {
    step: 'PAYMENT_A_RELEASED',
    screeningOutcome: 'NO_POTENTIAL_MATCH',
    anchorPayment: { txnRef: 'PMT-2026-004417', status: 'EXECUTED', executedAt: at(-1, 16, 52) }
  },

  ACTIVITY_READY: {
    step: 'ACTIVITY_READY',
    screeningOutcome: 'NO_POTENTIAL_MATCH',
    anchorPayment: { txnRef: 'PMT-2026-004417', status: 'EXECUTED', executedAt: at(-1, 16, 52) },
    activity: {
      transactionCount: 8, windowMinutes: 55, inboundGbp: 54300, outboundGbp: 43000,
      dispersalRatio: 0.79, newCounterpartyCount: 4
    }
  },

  CASE_RAISED: {
    step: 'CASE_RAISED',
    simulatedAsOf: at(0, 2, 0),
    screeningOutcome: 'NO_POTENTIAL_MATCH',
    anchorPayment: { txnRef: 'PMT-2026-004417', status: 'EXECUTED', executedAt: at(-1, 16, 52) },
    activity: {
      transactionCount: 8, windowMinutes: 55, inboundGbp: 54300, outboundGbp: 43000,
      dispersalRatio: 0.79, newCounterpartyCount: 4
    },
    monitoring: { customersEvaluated: 60, suppressedOpenCase: 11, casesRaised: 1 },
    raisedCase: {
      id: 1, caseRef: 'CASE-2026-0142', priorityBand: 'RED', priorityScore: 77,
      focusTxnRef: 'PMT-2026-004417'
    }
  },

  PAYMENT_B_HELD: {
    step: 'PAYMENT_B_HELD',
    simulatedAsOf: at(0, 2, 0),
    screeningOutcome: 'NO_POTENTIAL_MATCH',
    anchorPayment: { txnRef: 'PMT-2026-004417', status: 'EXECUTED', executedAt: at(-1, 16, 52) },
    activity: {
      transactionCount: 8, windowMinutes: 55, inboundGbp: 54300, outboundGbp: 43000,
      dispersalRatio: 0.79, newCounterpartyCount: 4
    },
    monitoring: { customersEvaluated: 60, suppressedOpenCase: 11, casesRaised: 1 },
    raisedCase: {
      id: 1, caseRef: 'CASE-2026-0142', priorityBand: 'RED', priorityScore: 77,
      focusTxnRef: 'PMT-2026-004417'
    },
    sanctionsHold: {
      txnRef: 'PMT-2026-004503', status: 'HELD', hitId: 7,
      nameSimilarity: 0.94, dateOfBirthMatch: 'EXACT', nationalityMatch: 'EXACT'
    }
  }
};

/* -------------------------------------------------------------------------
   Sanctions  —  GET /api/sanctions-hits?status=POTENTIAL_MATCH
   ------------------------------------------------------------------------- */
MOCK.hits = [
  {
    id: 7, triggerType: 'PAYMENT_SCREENING', screenedName: 'Vladimir Petroff',
    nameSimilarity: 0.94, status: 'POTENTIAL_MATCH', txnRef: 'PMT-2026-004503',
    screenedAt: at(0, 9, 12),
    matchDetailsSnapshot: {
      name:        { input: 'Vladimir Petroff', listed: 'Vladimir Petrov', similarity: 0.94, match: 'PARTIAL' },
      dateOfBirth: { input: '1971-03-02', listed: '1971-03-02', match: 'EXACT' },
      nationality: { input: 'RU', listed: 'RU', match: 'EXACT' },
      passport:    { input: null, listed: '71********', match: 'NOT_PROVIDED' },
      overall:     { score: 0.94 }
    },
    sanctionsEntrySnapshot: {
      sourceUniqueId: 'OS-DEMO-VLADIMIR-PETROV',
      name: 'Vladimir Petrov',
      entityType: 'INDIVIDUAL',
      aliases: ['V. Petrov', 'Владимир Петров'],
      identifiers: { dateOfBirth: '1971-03-02', nationality: 'RU', passport: '71********' },
      measures: [
        { type: 'asset_freeze', source: 'OpenSanctions / UK FCDO' },
        { type: 'travel_ban',   source: 'OpenSanctions / UK FCDO' }
      ],
      sourceUpdatedAt: at(-201, 14, 15)
    }
  },
  {
    id: 6, triggerType: 'PAYMENT_SCREENING', screenedName: 'V. Milonov',
    nameSimilarity: 0.81, status: 'POTENTIAL_MATCH', txnRef: 'PMT-2026-004488',
    screenedAt: at(-1, 11, 4),
    matchDetailsSnapshot: {
      name:        { input: 'V. Milonov', listed: 'Vitaly Valentinovich MILONOV', similarity: 0.81, match: 'PARTIAL' },
      dateOfBirth: { input: '1980-06-14', listed: '1974-01-23', match: 'MISMATCH' },
      nationality: { input: 'RU', listed: 'RU', match: 'EXACT' },
      passport:    { input: null, listed: null, match: 'NOT_PROVIDED' },
      overall:     { score: 0.81 }
    },
    sanctionsEntrySnapshot: {
      sourceUniqueId: 'Q4293669',
      name: 'Vitaly Valentinovich MILONOV',
      entityType: 'INDIVIDUAL',
      aliases: ['Милонов Виталий Валентинович'],
      identifiers: { dateOfBirth: '1974-01-23', nationality: 'RU' },
      measures: [{ type: 'asset_freeze', source: 'OpenSanctions / UK FCDO' }],
      sourceUpdatedAt: at(-201, 14, 15)
    }
  },
  {
    id: 5, triggerType: 'CUSTOMER_ONBOARDING', screenedName: 'K. Zatolokin',
    nameSimilarity: 0.76, status: 'POTENTIAL_MATCH', txnRef: null,
    screenedAt: at(-2, 15, 38),
    matchDetailsSnapshot: {
      name:        { input: 'K. Zatolokin', listed: 'Kirill Andreevich Zatolokin', similarity: 0.76, match: 'PARTIAL' },
      dateOfBirth: { input: null, listed: '1992-04-30', match: 'NOT_PROVIDED' },
      nationality: { input: 'RU', listed: 'RU', match: 'EXACT' },
      passport:    { input: null, listed: null, match: 'NOT_PROVIDED' },
      overall:     { score: 0.76 }
    },
    sanctionsEntrySnapshot: {
      sourceUniqueId: 'NK-ZTJBJFWBVXKMLbWwvXpVwW',
      name: 'Kirill Andreevich Zatolokin',
      entityType: 'INDIVIDUAL',
      aliases: ['downlow', 'Кирилл Андреевич Затолокин'],
      identifiers: { dateOfBirth: '1992-04-30', nationality: 'RU' },
      measures: [
        { type: 'asset_freeze',  source: 'OpenSanctions / UK FCDO' },
        { type: 'travel_ban',    source: 'OpenSanctions / UK FCDO' },
        { type: 'trust_services', source: 'OpenSanctions / UK FCDO' }
      ],
      sourceUpdatedAt: at(-201, 14, 15)
    }
  }
];

/* -------------------------------------------------------------------------
   Stats  —  GET /api/stats/rules
   ------------------------------------------------------------------------- */
MOCK.stats = {
  ruleTriggers: { R1: 38, R2: 26, R3: 19, R4: 11 },
  dispositionDistribution: {
    CLOSED_FALSE_POSITIVE: 31, CLOSED_NFA: 24, OPEN: 12, ESCALATED_INTERNAL: 9
  }
};

/** Rule display names, matching RuleEngineService. */
const RULE_NAMES = {
  R1: 'Amount deviation',
  R2: 'Rapid dispersal',
  R3: 'New counterparties',
  R4: 'Higher-risk jurisdiction'
};

const DISPOSITION_LABELS = {
  CLOSED_NFA: 'Closed – No Further Action',
  CLOSED_FALSE_POSITIVE: 'Closed – False Positive',
  ESCALATED_INTERNAL: 'Escalated – Internal',
  OPEN: 'Still open'
};

/** Simulates network latency so loading states are visible in the mockup. */
function mockFetch(value, ms = 420) {
  return new Promise(resolve => setTimeout(() => resolve(structuredClone(value)), ms));
}
