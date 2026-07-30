/* ==========================================================================
   Real API access for the UI. Replaces the mockup's mock-data.js — the pages
   call the same render functions, only the data source changed.
   ========================================================================== */

async function api(path, options = {}) {
  let res;
  try {
    res = await fetch(path, {
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options
    });
  } catch {
    // fetch only rejects when the server could not be reached at all
    throw new Error('Cannot reach the application. The server may still be starting — wait a moment and reload.');
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
  if (!res.ok) {
    const err = new Error((data && (data.error || data.message)) || res.statusText);
    err.status = res.status;
    throw err;
  }
  return data;
}

/**
 * Shows a failure in the page instead of a native dialog. A blocked or suppressed
 * alert() leaves an empty screen with no explanation — this always renders something.
 */
function showPageError(message) {
  const banner = document.createElement('div');
  banner.className = 'page-error';
  banner.innerHTML =
    '<strong>Something went wrong.</strong><span></span>' +
    '<button type="button" onclick="location.reload()">Reload</button>';
  banner.querySelector('span').textContent = message;
  const main = document.querySelector('main') || document.body;
  main.prepend(banner);
  console.error(message);
}

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
