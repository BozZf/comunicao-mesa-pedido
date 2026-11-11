package br.com.mesapedido.service;

import br.com.mesapedido.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payment-mode", havingValue = "mercadopago")
public class PaymentReconciler {
  private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);
  private final OrderRepository orders;
  private final OrderService service;

  public PaymentReconciler(OrderRepository o, OrderService s) {
    orders = o;
    service = s;
  }

  @Scheduled(
      fixedDelayString = "${app.reconcile-delay:30000}",
      initialDelayString = "${app.reconcile-delay:30000}")
  public void reconcile() {
    for (var id : orders.pendingPayments()) {
      try {
        service.reconcile(id);
      } catch (Exception e) {
        log.warn("Falha ao conciliar pagamento do pedido {}; nova tentativa no próximo ciclo", id);
      }
    }
  }
}
