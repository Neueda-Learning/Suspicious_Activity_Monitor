async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
  if (!res.ok) {
    const msg = (data && (data.error || data.message)) || res.statusText;
    throw new Error(msg);
  }
  return data;
}

function bandLabel(band) {
  if (band === "GREEN") return "Low Priority";
  return band;
}

function fmtMoney(v) {
  if (v == null) return "—";
  return new Intl.NumberFormat("en-GB", { style: "currency", currency: "GBP" }).format(Number(v));
}

function fmtTs(v) {
  if (!v) return "—";
  return new Date(v).toLocaleString("en-GB", { timeZone: "UTC" }) + " UTC";
}
