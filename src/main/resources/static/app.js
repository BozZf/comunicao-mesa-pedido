import { api, init, money, statuses, el, message } from "./common.js";
const names = {
    PIZZA: "Pizzas",
    BEBIDA: "Bebidas",
    ACOMPANHAMENTO: "Acompanhamentos",
    SOBREMESA: "Sobremesas",
  },
  icons = { PIZZA: "🍕", BEBIDA: "🥤", ACOMPANHAMENTO: "🥔", SOBREMESA: "🍫" };
let products = [],
  cart = new Map(),
  category = "PIZZA",
  tableToken = new URLSearchParams(location.search).get("mesa"),
  saved,
  ready = false,
  busy = false;
try {
  saved = JSON.parse(localStorage.getItem("mesa-order") || "null");
} catch {
  localStorage.removeItem("mesa-order");
}
const $ = (s) => document.querySelector(s);
function draw() {
  const nav = $("#categories");
  nav.replaceChildren();
  for (const [k, v] of Object.entries(names)) {
    const b = el("button", v, k === category ? "active" : "");
    b.onclick = () => {
      category = k;
      draw();
    };
    nav.append(b);
  }
  const menu = $("#menu");
  menu.replaceChildren();
  for (const p of products.filter((p) => p.category === category)) {
    const card = el("article", undefined, "product");
    card.append(
      el("div", icons[p.category], "art"),
      el("h3", p.name),
      el("p", p.description),
    );
    const bottom = el("div", undefined, "bottom"),
      add = el("button", "+ Adicionar");
    add.disabled = busy;
    add.onclick = () => {
      cart.set(p.id, Math.min(20, (cart.get(p.id) || 0) + 1));
      draw();
    };
    bottom.append(el("strong", money(p.price)), add);
    card.append(bottom);
    menu.append(card);
  }
  const list = $("#cart");
  list.replaceChildren();
  let total = 0;
  if (!cart.size)
    list.append(
      el("p", "Comece escolhendo algo gostoso no cardápio.", "empty"),
    );
  for (const [id, q] of cart) {
    const p = products.find((x) => x.id === id);
    total += p.price * q;
    const row = el("div", undefined, "cart-line"),
      control = el("div", undefined, "quantity");
    row.append(el("strong", p.name));
    for (const [sign, delta] of [
      ["−", -1],
      ["+", 1],
    ]) {
      const b = el("button", sign);
      b.setAttribute(
        "aria-label",
        `${delta === 1 ? "Adicionar" : "Remover"} ${p.name}`,
      );
      b.disabled = busy;
      b.onclick = () => {
        const n = q + delta;
        if (n > 20) return;
        if (n <= 0) cart.delete(id);
        else cart.set(id, n);
        draw();
      };
      if (delta === 1) control.append(el("span", String(q)));
      control.append(b);
    }
    control.append(el("span", money(p.price * q)));
    row.append(control);
    list.append(row);
  }
  $("#total").textContent = money(total);
  $("#submit").disabled = !ready || !cart.size || busy;
}
async function track(sync = false) {
  if (!saved) return;
  const o = await api(
    `/orders/${saved.id}${sync ? "/sync" : ""}`,
    sync ? "POST" : "GET",
    undefined,
    saved.token,
  );
  const panel = $("#tracking");
  panel.hidden = false;
  panel.replaceChildren(
    el("span", "ACOMPANHE POR AQUI", "eyebrow"),
    el("h2", `${statuses[o.status]} · Mesa ${o.tableNumber}`),
    el("p", `Pedido ${o.id.slice(0, 8)} · ${money(o.total)}`),
  );
  for (const i of o.items) panel.append(el("div", `${i.quantity} × ${i.name}`));
  if (o.status === "AGUARDANDO_PAGAMENTO") {
    const pay = el("button", "Pagar com Pix ou cartão");
    pay.onclick = () => payOrder().catch(message);
    const check = el("button", "Já paguei · verificar");
    check.onclick = () => track(true).catch(message);
    panel.append(pay, check);
  } else {
    panel.append(
      el(
        "p",
        `Registro de pagamento: ${o.paymentId} • ${new Date(o.paidAt).toLocaleString("pt-BR")}`,
      ),
    );
  }
}
async function payOrder() {
  const c = await api(
    `/orders/${saved.id}/checkout`,
    "POST",
    undefined,
    saved.token,
  );
  if (c.demo) {
    const panel = $("#tracking");
    let b = $("#simulate");
    if (!b) {
      b = el("button", "DEMO: simular pagamento aprovado");
      b.id = "simulate";
      b.onclick = async () => {
        try {
          await api(
            `/orders/${saved.id}/simulate`,
            "POST",
            undefined,
            saved.token,
          );
          await track();
        } catch (e) {
          message(e);
        }
      };
      panel.append(b);
    }
  } else {
    const u = new URL(c.url);
    if (u.protocol !== "https:")
      throw new Error("Endereço de pagamento inválido.");
    location.assign(u.href);
  }
}
$("#submit").onclick = async () => {
  busy = true;
  message("");
  draw();
  try {
    const key = crypto.randomUUID();
    const result = await api("/orders", "POST", {
      tableToken,
      requestKey: key,
      items: [...cart].map(([productId, quantity]) => ({
        productId,
        quantity,
      })),
      notes: $("#notes").value,
    });
    saved = { id: result.order.id, token: result.accessToken };
    localStorage.setItem("mesa-order", JSON.stringify(saved));
    cart.clear();
    $("#notes").value = "";
    await track();
    await payOrder();
  } catch (e) {
    message(e);
  } finally {
    busy = false;
    draw();
  }
};
async function start() {
  const s = await init();
  $("#demo").hidden = !s.demo;
  products = await api("/menu");
  if (tableToken) {
    const t = await api("/tables/" + encodeURIComponent(tableToken));
    $("#table").textContent = "Mesa " + String(t.number).padStart(2, "0");
    ready = true;
  } else
    message("Para pedir, leia o QR Code da sua mesa. A equipe pode ajudar.");
  draw();
  if (saved) await track(true);
}
start().catch(message);
setInterval(() => {
  if (saved && document.visibilityState === "visible") track().catch(message);
}, 10000);
