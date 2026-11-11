package br.com.mesapedido.controller;

import br.com.mesapedido.dto.ApiDtos.*;
import br.com.mesapedido.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PublicController {
  private final OrderService orders;
  private final MenuService menu;
  private final boolean demo;

  public PublicController(
      OrderService o, MenuService m, @Value("${app.payment-mode}") String mode) {
    orders = o;
    menu = m;
    demo = "demo".equals(mode);
  }

  @GetMapping("/session")
  public Map<String, Object> session(CsrfToken token) {
    return Map.of("csrf", token.getToken(), "header", token.getHeaderName(), "demo", demo);
  }

  @GetMapping("/menu")
  public List<ProductView> menu() {
    return menu.list();
  }

  @GetMapping("/tables/{token}")
  public Map<String, Object> table(@PathVariable String token) {
    var t = orders.table(token);
    return Map.of("number", t.id);
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedOrder create(@Valid @RequestBody CreateOrder request) {
    return orders.create(request);
  }

  @GetMapping("/orders/{id}")
  public OrderView get(@PathVariable UUID id, @RequestHeader("X-Order-Token") String token) {
    return orders.get(id, token);
  }

  @PostMapping("/orders/{id}/checkout")
  public Checkout checkout(@PathVariable UUID id, @RequestHeader("X-Order-Token") String token) {
    return orders.checkout(id, token);
  }

  @PostMapping("/orders/{id}/sync")
  public OrderView sync(@PathVariable UUID id, @RequestHeader("X-Order-Token") String token) {
    return orders.sync(id, token);
  }

  @PostMapping("/orders/{id}/simulate")
  public OrderView simulate(@PathVariable UUID id, @RequestHeader("X-Order-Token") String token) {
    return orders.simulate(id, token);
  }
}
