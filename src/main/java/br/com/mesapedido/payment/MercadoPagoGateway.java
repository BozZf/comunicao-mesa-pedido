package br.com.mesapedido.payment;

import br.com.mesapedido.domain.CustomerOrder;
import br.com.mesapedido.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.payment-mode", havingValue = "mercadopago")
public class MercadoPagoGateway implements PaymentGateway {
  private final RestClient client;
  private final String baseUrl, token;

  @org.springframework.beans.factory.annotation.Autowired
  public MercadoPagoGateway(
      @Value("${app.mp-token}") String token, @Value("${app.base-url}") String baseUrl) {
    this.token = token;
    this.baseUrl = baseUrl;
    var f =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    f.setReadTimeout(Duration.ofSeconds(15));
    client =
        RestClient.builder()
            .baseUrl("https://api.mercadopago.com")
            .requestFactory(f)
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
  }

  MercadoPagoGateway(RestClient client, String token, String baseUrl) {
    this.client = client;
    this.token = token;
    this.baseUrl = baseUrl;
  }

  private void configured() {
    if (token.isBlank() || !baseUrl.startsWith("https://"))
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Configure MP_ACCESS_TOKEN e APP_BASE_URL HTTPS para receber pagamentos.");
  }

  public String checkout(CustomerOrder o) {
    configured();
    String back = baseUrl + "/?pedido=" + o.id;
    var body =
        Map.of(
            "external_reference",
            o.id.toString(),
            "items",
            List.of(
                Map.of(
                    "id",
                    o.id.toString(),
                    "title",
                    "Pedido da mesa " + o.tableNumber,
                    "quantity",
                    1,
                    "currency_id",
                    "BRL",
                    "unit_price",
                    o.total)),
            "back_urls",
            Map.of("success", back, "failure", back, "pending", back),
            "auto_return",
            "approved",
            "payment_methods",
            Map.of("excluded_payment_types", List.of(Map.of("id", "ticket"))));
    JsonNode response =
        client
            .post()
            .uri("/checkout/preferences")
            .header("X-Idempotency-Key", o.id.toString())
            .body(body)
            .retrieve()
            .body(JsonNode.class);
    String url = response == null ? "" : response.path("init_point").asText();
    if (!url.startsWith("https://"))
      throw new BusinessException(HttpStatus.BAD_GATEWAY, "Checkout indisponível.");
    return url;
  }

  public Optional<Confirmation> approved(CustomerOrder o) {
    configured();
    JsonNode result =
        client
            .get()
            .uri(
                b ->
                    b.path("/v1/payments/search")
                        .queryParam("external_reference", o.id.toString())
                        .queryParam("status", "approved")
                        .queryParam("sort", "date_created")
                        .queryParam("criteria", "desc")
                        .build())
            .retrieve()
            .body(JsonNode.class);
    if (result != null)
      for (JsonNode p : result.path("results")) {
        if ("approved".equals(p.path("status").asText())
            && o.id.toString().equals(p.path("external_reference").asText())
            && "BRL".equals(p.path("currency_id").asText())
            && o.total.compareTo(p.path("transaction_amount").decimalValue()) == 0)
          return Optional.of(
              new Confirmation(
                  p.path("id").asText(),
                  p.path("transaction_amount").decimalValue(),
                  "BRL",
                  o.id.toString()));
      }
    return Optional.empty();
  }

  public boolean demo() {
    return false;
  }
}
