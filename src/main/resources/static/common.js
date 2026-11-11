export const money = (n) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(
    n,
  );
export const statuses = {
  AGUARDANDO_PAGAMENTO: "Aguardando pagamento",
  PAGO: "Pagamento confirmado",
  EM_PREPARO: "Em preparo",
  PRONTO: "Pronto para servir",
  ENTREGUE: "Entregue",
};
let session;
export async function init() {
  session = await fetch("/api/session").then((r) => r.json());
  return session;
}
export async function api(path, method = "GET", body, token) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") headers[session.header] = session.csrf;
  if (token) headers["X-Order-Token"] = token;
  const r = await fetch("/api" + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok)
    throw new Error(data.message || `Não foi possível concluir (${r.status}).`);
  return data;
}
export function el(tag, text, cls) {
  const e = document.createElement(tag);
  if (text !== undefined) e.textContent = text;
  if (cls) e.className = cls;
  return e;
}
export function message(e) {
  document.querySelector("#message").textContent = e?.message || e || "";
}
