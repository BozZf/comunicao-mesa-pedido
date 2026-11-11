package br.com.mesapedido.payment;

import br.com.mesapedido.domain.CustomerOrder;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payment-mode", havingValue = "demo")
public class DemoPaymentGateway implements PaymentGateway {
  public String checkout(CustomerOrder o) {
    return "";
  }

  public Optional<Confirmation> approved(CustomerOrder o) {
    return Optional.empty();
  }

  public boolean demo() {
    return true;
  }
}
