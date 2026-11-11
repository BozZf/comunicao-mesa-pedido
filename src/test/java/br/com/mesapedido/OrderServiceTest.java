package br.com.mesapedido;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.mesapedido.domain.*;
import br.com.mesapedido.dto.ApiDtos.*;
import br.com.mesapedido.exception.BusinessException;
import br.com.mesapedido.payment.*;
import br.com.mesapedido.repository.*;
import br.com.mesapedido.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
  @Mock OrderRepository orders;
  @Mock ProductRepository products;
  @Mock TableRepository tables;
  @Mock PaymentGateway gateway;
  @InjectMocks OrderService service;
  CustomerOrder order;

  @BeforeEach
  void setup() {
    order = new CustomerOrder();
    order.id = UUID.randomUUID();
    order.accessToken = "secret";
    order.total = new BigDecimal("84.00");
    order.status = OrderStatus.AGUARDANDO_PAGAMENTO;
  }

  private void locked() {
    when(orders.lock(order.id)).thenReturn(Optional.of(order));
  }

  private CreateOrder request() {
    return new CreateOrder(
        "table-token", UUID.randomUUID(), List.of(new ItemRequest(1L, 2)), "sem cebola");
  }

  private void table() {
    var t = new DiningTable();
    t.id = 7;
    when(tables.findByTokenAndActiveTrue("table-token")).thenReturn(Optional.of(t));
  }

  @Test
  void calculatesPriceOnServerAndSnapshotsItems() {
    table();
    var p = new Product();
    p.id = 1L;
    p.name = "Pizza";
    p.price = new BigDecimal("42.00");
    p.available = true;
    when(products.findById(1L)).thenReturn(Optional.of(p));
    var result = service.create(request());
    assertEquals(new BigDecimal("84.00"), result.order().total());
    assertEquals(7, result.order().tableNumber());
    assertEquals(OrderStatus.AGUARDANDO_PAGAMENTO, result.order().status());
    assertNotNull(result.accessToken());
    verify(orders).saveAndFlush(any());
    p.price = BigDecimal.ONE;
    assertEquals(new BigDecimal("42.00"), result.order().items().getFirst().unitPrice());
  }

  @Test
  void rejectsInactiveTable() {
    assertThrows(BusinessException.class, () -> service.create(request()));
    verifyNoInteractions(products);
  }

  @Test
  void rejectsUnavailableProduct() {
    table();
    assertThrows(BusinessException.class, () -> service.create(request()));
    verify(orders, never()).saveAndFlush(any());
  }

  @Test
  void rejectsDuplicateRequest() {
    table();
    var request = request();
    when(orders.findByRequestKey(request.requestKey())).thenReturn(Optional.of(order));
    assertThrows(BusinessException.class, () -> service.create(request));
    verifyNoInteractions(products);
  }

  @Test
  void rejectsDuplicateProductLines() {
    table();
    var p = new Product();
    p.available = true;
    p.price = BigDecimal.TEN;
    when(products.findById(1L)).thenReturn(Optional.of(p));
    assertThrows(
        BusinessException.class,
        () ->
            service.create(
                new CreateOrder(
                    "table-token",
                    UUID.randomUUID(),
                    List.of(new ItemRequest(1L, 1), new ItemRequest(1L, 1)),
                    null)));
  }

  @Test
  void rejectsWrongCustomerToken() {
    locked();
    assertThrows(BusinessException.class, () -> service.get(order.id, "wrong"));
    verifyNoInteractions(gateway);
  }

  @Test
  void acceptsOnlyVerifiedExactAmount() {
    locked();
    when(gateway.approved(order))
        .thenReturn(
            Optional.of(
                new PaymentGateway.Confirmation(
                    "123", new BigDecimal("84.00"), "BRL", order.id.toString())));
    var result = service.sync(order.id, "secret");
    assertEquals(OrderStatus.PAGO, result.status());
    assertEquals("123", result.paymentId());
    assertNotNull(result.paidAt());
    service.sync(order.id, "secret");
    verify(gateway, times(1)).approved(order);
  }

  @Test
  void rejectsMismatchedAmount() {
    locked();
    when(gateway.approved(order))
        .thenReturn(
            Optional.of(
                new PaymentGateway.Confirmation(
                    "123", BigDecimal.ONE, "BRL", order.id.toString())));
    assertEquals(OrderStatus.AGUARDANDO_PAGAMENTO, service.sync(order.id, "secret").status());
  }

  @Test
  void rejectsMismatchedReference() {
    locked();
    when(gateway.approved(order))
        .thenReturn(
            Optional.of(new PaymentGateway.Confirmation("123", order.total, "BRL", "other")));
    service.sync(order.id, "secret");
    assertNull(order.paidAt);
  }

  @Test
  void rejectsWrongCurrency() {
    locked();
    when(gateway.approved(order))
        .thenReturn(
            Optional.of(
                new PaymentGateway.Confirmation("123", order.total, "USD", order.id.toString())));
    service.sync(order.id, "secret");
    assertNull(order.paidAt);
  }

  @Test
  void pendingPaymentNeverEntersQueue() {
    when(orders.findTop100ByStatusNotOrderByCreatedAtDesc(OrderStatus.AGUARDANDO_PAGAMENTO))
        .thenReturn(List.of());
    assertTrue(service.queue().isEmpty());
  }

  @Test
  void deniesDemoInRealMode() {
    when(gateway.demo()).thenReturn(false);
    assertThrows(BusinessException.class, () -> service.simulate(order.id, "secret"));
    verifyNoInteractions(orders);
  }

  @Test
  void allowsOnlySequentialStatusChanges() {
    locked();
    assertThrows(BusinessException.class, () -> service.change(order.id, OrderStatus.EM_PREPARO));
    order.status = OrderStatus.PAGO;
    assertEquals(OrderStatus.EM_PREPARO, service.change(order.id, OrderStatus.EM_PREPARO).status());
    assertThrows(BusinessException.class, () -> service.change(order.id, OrderStatus.ENTREGUE));
    service.change(order.id, OrderStatus.PRONTO);
    service.change(order.id, OrderStatus.ENTREGUE);
    assertThrows(BusinessException.class, () -> service.change(order.id, OrderStatus.PAGO));
  }

  @Test
  void reusesCheckoutUrl() {
    locked();
    when(gateway.checkout(order)).thenReturn("https://checkout.example");
    assertEquals("https://checkout.example", service.checkout(order.id, "secret").url());
    service.checkout(order.id, "secret");
    verify(gateway, times(1)).checkout(order);
  }

  @Test
  void reconcilesWithoutCustomerBrowser() {
    locked();
    when(gateway.approved(order))
        .thenReturn(
            Optional.of(
                new PaymentGateway.Confirmation("123", order.total, "BRL", order.id.toString())));
    service.reconcile(order.id);
    assertEquals(OrderStatus.PAGO, order.status);
  }
}
