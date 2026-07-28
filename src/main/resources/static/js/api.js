async function api(path, options = {}) {
  let res;
  try {
    res = await fetch(path, {
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
      ...options,
    });
  } catch {
    // fetch only rejects when the server could not be reached at all
    throw new Error("Cannot reach the application. The server may still be starting — wait a moment and reload.");
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
  if (!res.ok) {
    const msg = (data && (data.error || data.message)) || res.statusText;
    throw new Error(msg);
  }
  return data;
}

/**
 * Shows a failure in the page instead of a native dialog.
 *
 * A blocked or suppressed alert() leaves an empty screen with no explanation — the worst
 * possible failure mode in front of an audience. This always renders something readable.
 */
function showPageError(message) {
  const banner = document.createElement("div");
  banner.className = "page-error";
  banner.innerHTML =
    '<strong>Something went wrong.</strong><span></span>' +
    '<button type="button" onclick="location.reload()">Reload</button>';
  banner.querySelector("span").textContent = message;
  const main = document.querySelector("main") || document.body;
  main.prepend(banner);
  console.error(message);
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
