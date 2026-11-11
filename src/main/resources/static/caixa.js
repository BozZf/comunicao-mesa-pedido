import { api, init, money, statuses, el, message } from "./common.js";
const $ = (s) => document.querySelector(s),
  next = { PAGO: "EM_PREPARO", EM_PREPARO: "PRONTO", PRONTO: "ENTREGUE" };
let busy = false;
async function refresh() {
  if (busy) return;
  busy = true;
  try {
    const orders = await api("/cashier/orders");
    const list = $("#orders");
    list.replaceChildren();
    for (const o of orders.filter(
      (o) => $("#completed").checked || o.status !== "ENTREGUE",
    )) {
      const c = el("article", undefined, "ticket");
      c.append(
        el("span", statuses[o.status], "eyebrow"),
        el("h2", `Mesa ${o.tableNumber} · ${money(o.total)}`),
        el(
          "small",
          `Pedido ${o.id.slice(0, 8)} · ${new Date(o.createdAt).toLocaleString("pt-BR")}`,
        ),
      );
      const ul = el("ul");
      for (const i of o.items)
        ul.append(
          el(
            "li",
            `${i.quantity} × ${i.name} — ${money(i.unitPrice * i.quantity)}`,
          ),
        );
      c.append(ul);
      if (o.notes) c.append(el("pre", o.notes));
      c.append(
        el("small", `Pagamento confirmado: ${o.paymentId}`),
        el(
          "p",
          `Confirmado em ${new Date(o.paidAt).toLocaleString("pt-BR")}`,
          "fine",
        ),
      );
      const receipt = el("a", "Ver comprovante");
      receipt.href = "/comprovante.html?pedido=" + o.id;
      receipt.target = "_blank";
      receipt.rel = "noopener";
      c.append(receipt);
      if (next[o.status]) {
        const b = el("button", statuses[next[o.status]], "primary");
        b.onclick = async () => {
          b.disabled = true;
          try {
            await api(`/cashier/orders/${o.id}/status`, "PATCH", {
              status: next[o.status],
            });
            await refresh();
          } catch (e) {
            message(e);
          } finally {
            b.disabled = false;
          }
        };
        c.append(b);
      }
      list.append(c);
    }
    if (!list.children.length)
      list.append(el("p", "Nenhum pedido nesta fila."));
    $("#updated").textContent =
      "Atualizado às " + new Date().toLocaleTimeString("pt-BR");
  } finally {
    busy = false;
  }
}
async function tables() {
  const ts = await api("/cashier/tables");
  $("#tables").replaceChildren();
  for (const t of ts) {
    const card = el("article", undefined, "table-card");
    const img = el("img");
    img.src = t.qr;
    img.alt = "QR Code da mesa " + t.number;
    const a = el("a", "Abrir cardápio desta mesa");
    a.href = t.url;
    a.target = "_blank";
    a.rel = "noopener";
    card.append(el("h2", "Mesa " + t.number), img, a);
    $("#tables").append(card);
  }
}
$("#refresh").onclick = () => refresh().catch(message);
$("#completed").onchange = () => refresh().catch(message);
$("#tables-toggle").onclick = () => {
  $("#table-section").hidden = !$("#table-section").hidden;
  tables().catch(message);
};
$("#add-table").onclick = async () => {
  try {
    await api("/cashier/tables/" + Number($("#number").value), "POST");
    await tables();
    message("");
  } catch (e) {
    message(e);
  }
};
$("#print").onclick = () => window.print();
init().then(refresh).catch(message);
setInterval(() => {
  if (document.visibilityState === "visible") refresh().catch(message);
}, 5000);
