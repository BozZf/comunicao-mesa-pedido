package br.com.mesapedido.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import br.com.mesapedido.domain.CustomerOrder;
import br.com.mesapedido.exception.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MercadoPagoGatewayTest {
  MockRestServiceServer server;
  MercadoPagoGateway gateway;
  CustomerOrder order;

  @BeforeEach
  void setup() {
    var builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
    server = MockRestServiceServer.bindTo(builder).build();
    gateway = new MercadoPagoGateway(builder.build(), "test-token", "https://pizzaria.example");
    order = new CustomerOrder();
    order.id = UUID.randomUUID();
    order.tableNumber = 1;
    order.total = new BigDecimal("42.00");
  }

  @Test
  void createsPreferenceWithServerPriceAndReference() {
    server
        .expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Idempotency-Key", order.id.toString()))
        .andExpect(jsonPath("$.external_reference").value(order.id.toString()))
        .andExpect(jsonPath("$.items[0].unit_price").value(42))
        .andExpect(jsonPath("$.items[0].currency_id").value("BRL"))
        .andRespond(
            withSuccess(
                "{\"init_point\":\"https://www.mercadopago.com.br/checkout/test\"}",
                MediaType.APPLICATION_JSON));
    assertTrue(gateway.checkout(order).startsWith("https://"));
    server.verify();
  }

  @Test
  void acceptsApprovedMatchingPayment() {
    expect("approved", "BRL", "42.00", order.id.toString());
    var payment = gateway.approved(order).orElseThrow();
    assertEquals("123", payment.id());
    assertEquals(0, order.total.compareTo(payment.amount()));
    server.verify();
  }

  @Test
  void ignoresPending() {
    expect("pending", "BRL", "42.00", order.id.toString());
    assertTrue(gateway.approved(order).isEmpty());
  }

  @Test
  void ignoresWrongReference() {
    expect("approved", "BRL", "42.00", "other");
    assertTrue(gateway.approved(order).isEmpty());
  }

  @Test
  void ignoresWrongAmount() {
    expect("approved", "BRL", "0.01", order.id.toString());
    assertTrue(gateway.approved(order).isEmpty());
  }

  @Test
  void ignoresWrongCurrency() {
    expect("approved", "USD", "42.00", order.id.toString());
    assertTrue(gateway.approved(order).isEmpty());
  }

  @Test
  void rejectsMissingConfiguration() {
    var g = new MercadoPagoGateway(RestClient.create(), "", "http://localhost");
    assertThrows(BusinessException.class, () -> g.checkout(order));
  }

  @Test
  void rejectsNonHttpsCheckout() {
    server
        .expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
        .andRespond(
            withSuccess("{\"init_point\":\"http://unsafe.example\"}", MediaType.APPLICATION_JSON));
    assertThrows(BusinessException.class, () -> gateway.checkout(order));
  }

  void expect(String status, String currency, String amount, String reference) {
    server
        .expect(
            requestTo(
                org.hamcrest.Matchers.containsString(
                    "/v1/payments/search?external_reference=" + order.id)))
        .andRespond(
            withSuccess(
                "{\"results\":[{\"id\":123,\"status\":\""
                    + status
                    + "\",\"currency_id\":\""
                    + currency
                    + "\",\"transaction_amount\":"
                    + amount
                    + ",\"external_reference\":\""
                    + reference
                    + "\"}]}",
                MediaType.APPLICATION_JSON));
  }
}
