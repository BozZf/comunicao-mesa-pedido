package br.com.mesapedido.service;

import br.com.mesapedido.domain.*;
import br.com.mesapedido.dto.ApiDtos.*;
import br.com.mesapedido.exception.BusinessException;
import br.com.mesapedido.payment.PaymentGateway;
import br.com.mesapedido.repository.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {
  private final OrderRepository orders;
  private final ProductRepository products;
  private final TableRepository tables;
  private final PaymentGateway gateway;

  public OrderService(OrderRepository o, ProductRepository p, TableRepository t, PaymentGateway g) {
    orders = o;
    products = p;
    tables = t;
    gateway = g;
  }

  public DiningTable table(String token) {
    return tables
        .findByTokenAndActiveTrue(token)
        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Mesa não encontrada ou inativa."));
  }

  public CreatedOrder create(CreateOrder request) {
    var table = table(request.tableToken());
    var existing = orders.findByRequestKey(request.requestKey());
    if (existing.isPresent()) {
      var o = existing.get();
      if (!Objects.equals(o.tableNumber, table.id))
        throw error(HttpStatus.CONFLICT, "Chave já utilizada em outra mesa.");
      throw error(
          HttpStatus.CONFLICT, "Pedido já enviado. Consulte o pedido salvo neste dispositivo.");
    }
    var o = new CustomerOrder();
    o.id = UUID.randomUUID();
    o.requestKey = request.requestKey();
    o.accessToken = UUID.randomUUID().toString() + UUID.randomUUID();
    o.tableNumber = table.id;
    o.status = OrderStatus.AGUARDANDO_PAGAMENTO;
    o.createdAt = Instant.now();
    o.notes = request.notes();
    o.total = BigDecimal.ZERO;
    Set<Long> unique = new HashSet<>();
    for (var line : request.items()) {
      if (!unique.add(line.productId()))
        throw error(HttpStatus.BAD_REQUEST, "Agrupe as quantidades do mesmo produto.");
      var p =
          products
              .findById(line.productId())
              .filter(x -> x.available)
              .orElseThrow(() -> error(HttpStatus.CONFLICT, "Produto indisponível."));
      var i = new OrderItem();
      i.productId = p.id;
      i.name = p.name;
      i.quantity = line.quantity();
      i.unitPrice = p.price;
      o.items.add(i);
      o.total = o.total.add(p.price.multiply(BigDecimal.valueOf(i.quantity)));
    }
    orders.saveAndFlush(o);
    return new CreatedOrder(OrderView.of(o), o.accessToken);
  }

  private CustomerOrder locked(UUID id) {
    return orders.lock(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
  }

  private void authorize(CustomerOrder o, String token) {
    if (token == null
        || !MessageDigest.isEqual(
            o.accessToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
      throw error(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
  }

  public OrderView get(UUID id, String token) {
    var o = locked(id);
    authorize(o, token);
    return OrderView.of(o);
  }

  public Checkout checkout(UUID id, String token) {
    var o = locked(id);
    authorize(o, token);
    if (o.status != OrderStatus.AGUARDANDO_PAGAMENTO)
      throw error(HttpStatus.CONFLICT, "Pedido já pago.");
    if (o.checkoutUrl == null) o.checkoutUrl = gateway.checkout(o);
    return new Checkout(o.checkoutUrl, gateway.demo());
  }

  public OrderView sync(UUID id, String token) {
    var o = locked(id);
    authorize(o, token);
    if (o.status == OrderStatus.AGUARDANDO_PAGAMENTO)
      gateway
          .approved(o)
          .ifPresent(
              p -> {
                if (o.id.toString().equals(p.reference())
                    && "BRL".equals(p.currency())
                    && o.total.compareTo(p.amount()) == 0) paid(o, p.id());
              });
    return OrderView.of(o);
  }

  public void reconcile(UUID id) {
    var o = locked(id);
    if (o.status == OrderStatus.AGUARDANDO_PAGAMENTO)
      gateway
          .approved(o)
          .ifPresent(
              p -> {
                if (o.id.toString().equals(p.reference())
                    && "BRL".equals(p.currency())
                    && o.total.compareTo(p.amount()) == 0) paid(o, p.id());
              });
  }

  public OrderView simulate(UUID id, String token) {
    if (!gateway.demo()) throw error(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
    var o = locked(id);
    authorize(o, token);
    if (o.status == OrderStatus.AGUARDANDO_PAGAMENTO) paid(o, "DEMO-" + o.id);
    return OrderView.of(o);
  }

  private void paid(CustomerOrder o, String paymentId) {
    o.paymentId = paymentId;
    o.paidAt = Instant.now();
    o.status = OrderStatus.PAGO;
  }

  public OrderView receipt(UUID id) {
    var o = locked(id);
    if (o.paidAt == null) throw error(HttpStatus.CONFLICT, "Pagamento ainda não confirmado.");
    return OrderView.of(o);
  }

  public List<OrderView> queue() {
    return orders
        .findTop100ByStatusNotOrderByCreatedAtDesc(OrderStatus.AGUARDANDO_PAGAMENTO)
        .stream()
        .map(OrderView::of)
        .toList();
  }

  public OrderView change(UUID id, OrderStatus next) {
    var o = locked(id);
    boolean valid =
        (o.status == OrderStatus.PAGO && next == OrderStatus.EM_PREPARO)
            || (o.status == OrderStatus.EM_PREPARO && next == OrderStatus.PRONTO)
            || (o.status == OrderStatus.PRONTO && next == OrderStatus.ENTREGUE);
    if (!valid) throw error(HttpStatus.CONFLICT, "Transição de status inválida.");
    o.status = next;
    return OrderView.of(o);
  }

  private BusinessException error(HttpStatus s, String message) {
    return new BusinessException(s, message);
  }
}
