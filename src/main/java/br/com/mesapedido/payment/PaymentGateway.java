package br.com.mesapedido.payment;

import br.com.mesapedido.domain.CustomerOrder;
import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentGateway {
  record Confirmation(String id, BigDecimal amount, String currency, String reference) {}

  String checkout(CustomerOrder order);

  Optional<Confirmation> approved(CustomerOrder order);

  boolean demo();
}
