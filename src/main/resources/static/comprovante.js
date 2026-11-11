import { api, init, money, el, message } from "./common.js";
document.querySelector("#print").onclick = () => window.print();
async function start() {
  await init();
  const id = new URLSearchParams(location.search).get("pedido");
  if (!id) throw new Error("Pedido não informado.");
  const o = await api("/cashier/orders/" + encodeURIComponent(id) + "/receipt");
  const panel = document.querySelector("#receipt");
  panel.append(el("h2", "Mesa " + o.tableNumber), el("p", "Pedido: " + o.id));
  for (const i of o.items)
    panel.append(
      el("p", `${i.quantity} × ${i.name} — ${money(i.unitPrice * i.quantity)}`),
    );
  panel.append(
    el("h2", "Total pago: " + money(o.total)),
    el("p", "Transação: " + o.paymentId),
    el("p", "Confirmado em: " + new Date(o.paidAt).toLocaleString("pt-BR")),
  );
  if (o.paymentId.startsWith("DEMO-"))
    panel.prepend(el("strong", "DEMONSTRAÇÃO — SEM VALOR FINANCEIRO"));
}
start().catch(message);
