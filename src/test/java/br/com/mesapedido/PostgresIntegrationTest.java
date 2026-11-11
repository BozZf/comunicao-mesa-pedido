package br.com.mesapedido;

import static org.junit.jupiter.api.Assertions.*;

import br.com.mesapedido.domain.*;
import br.com.mesapedido.dto.ApiDtos.*;
import br.com.mesapedido.repository.*;
import br.com.mesapedido.service.OrderService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = "app.cashier-password=test-password-123")
@ActiveProfiles("demo")
@EnabledIfSystemProperty(named = "postgres.url", matches = ".+")
class PostgresIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> System.getProperty("postgres.url"));
    r.add("spring.datasource.username", () -> "test");
    r.add("spring.datasource.password", () -> "test");
  }

  @Autowired OrderService service;
  @Autowired TableRepository tables;

  @Test
  void migrationAndPaidOrderPersistOnPostgres() {
    var t = new DiningTable();
    t.id = 901;
    t.token = UUID.randomUUID().toString();
    t.active = true;
    tables.save(t);
    var created =
        service.create(
            new CreateOrder(t.token, UUID.randomUUID(), List.of(new ItemRequest(1L, 1)), null));
    service.simulate(created.order().id(), created.accessToken());
    assertEquals(
        OrderStatus.PAGO, service.get(created.order().id(), created.accessToken()).status());
  }
}
