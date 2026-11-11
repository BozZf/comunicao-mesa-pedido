package br.com.mesapedido;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.mesapedido.domain.*;
import br.com.mesapedido.repository.*;
import com.fasterxml.jackson.databind.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.cashier-password=test-password-123"
    })
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TableRepository tables;
  String tableToken;

  @BeforeEach
  void table() {
    var t = new DiningTable();
    t.id = 10;
    t.token = UUID.randomUUID().toString();
    t.active = true;
    tables.save(t);
    tableToken = t.token;
  }

  String body(int quantity) {
    return "{\"tableToken\":\""
        + tableToken
        + "\",\"requestKey\":\""
        + UUID.randomUUID()
        + "\",\"items\":[{\"productId\":1,\"quantity\":"
        + quantity
        + "}],\"notes\":\"sem cebola\"}";
  }

  @Test
  void fullFlowPaymentReceiptQueueAndTransitions() throws Exception {
    var created =
        mvc.perform(
                post("/api/orders").with(csrf()).contentType("application/json").content(body(2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.order.total").value(84))
            .andReturn();
    var node = json.readTree(created.getResponse().getContentAsString());
    String id = node.path("order").path("id").asText(), token = node.path("accessToken").asText();
    mvc.perform(get("/api/orders/" + id).header("X-Order-Token", "wrong"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/cashier/orders").with(httpBasic("caixa", "test-password-123")))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(id))));
    mvc.perform(post("/api/orders/" + id + "/checkout").with(csrf()).header("X-Order-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demo").value(true));
    mvc.perform(post("/api/orders/" + id + "/simulate").with(csrf()).header("X-Order-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAGO"))
        .andExpect(jsonPath("$.paymentId").value("DEMO-" + id));
    mvc.perform(get("/api/cashier/orders").with(httpBasic("caixa", "test-password-123")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(id)));
    mvc.perform(
            get("/api/cashier/orders/" + id + "/receipt")
                .with(httpBasic("caixa", "test-password-123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value("DEMO-" + id));
    for (String state : new String[] {"EM_PREPARO", "PRONTO", "ENTREGUE"})
      mvc.perform(
              patch("/api/cashier/orders/" + id + "/status")
                  .with(csrf())
                  .with(httpBasic("caixa", "test-password-123"))
                  .contentType("application/json")
                  .content("{\"status\":\"" + state + "\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value(state));
  }

  @Test
  void realBrowserCsrfCookieRoundTrip() throws Exception {
    var session = mvc.perform(get("/api/session")).andExpect(status().isOk()).andReturn();
    var csrf = json.readTree(session.getResponse().getContentAsString());
    var cookie = session.getResponse().getCookie("XSRF-TOKEN");
    org.junit.jupiter.api.Assertions.assertNotNull(cookie);
    mvc.perform(
            post("/api/orders")
                .cookie(cookie)
                .header(csrf.path("header").asText(), csrf.path("csrf").asText())
                .contentType("application/json")
                .content(body(1)))
        .andExpect(status().isCreated());
  }

  @Test
  void validatesQuantitiesAndCsrf() throws Exception {
    for (int q : new int[] {0, -1, 21})
      mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(body(q)))
          .andExpect(status().isBadRequest());
    mvc.perform(post("/api/orders").contentType("application/json").content(body(1)))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsForgedPriceAndEmptyItems() throws Exception {
    mvc.perform(
            post("/api/orders")
                .with(csrf())
                .contentType("application/json")
                .content(body(1).replace("\"quantity\":1", "\"quantity\":1,\"price\":0.01")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/orders")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"tableToken\":\""
                        + tableToken
                        + "\",\"requestKey\":\""
                        + UUID.randomUUID()
                        + "\",\"items\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void duplicateSubmissionReturnsConflict() throws Exception {
    String body = body(1);
    mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void cashierRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/cashier/orders")).andExpect(status().isUnauthorized());
    mvc.perform(get("/caixa.html")).andExpect(status().isUnauthorized());
  }

  @Test
  void generatesPngQrAndValidatesTableNumber() throws Exception {
    mvc.perform(get("/api/cashier/tables/10/qr").with(httpBasic("caixa", "test-password-123")))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
    mvc.perform(
            post("/api/cashier/tables/0")
                .with(csrf())
                .with(httpBasic("caixa", "test-password-123")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void servesResponsiveClientAndSecurityPolicy() throws Exception {
    mvc.perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("viewport")));
    mvc.perform(get("/api/session"))
        .andExpect(jsonPath("$.csrf").isNotEmpty())
        .andExpect(jsonPath("$.demo").value(true));
  }
}
