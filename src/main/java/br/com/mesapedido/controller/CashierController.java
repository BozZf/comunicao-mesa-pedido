package br.com.mesapedido.controller;

import br.com.mesapedido.dto.ApiDtos.*;
import br.com.mesapedido.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cashier")
public class CashierController {
  private final OrderService orders;
  private final TableService tables;

  public CashierController(OrderService o, TableService t) {
    orders = o;
    tables = t;
  }

  @GetMapping("/orders")
  public List<OrderView> list() {
    return orders.queue();
  }

  @PatchMapping("/orders/{id}/status")
  public OrderView change(@PathVariable UUID id, @Valid @RequestBody ChangeStatus request) {
    return orders.change(id, request.status());
  }

  @GetMapping("/orders/{id}/receipt")
  public OrderView receipt(@PathVariable UUID id) {
    return orders.receipt(id);
  }

  @GetMapping("/tables")
  public List<Map<String, Object>> tables() {
    return tables.list();
  }

  @PostMapping("/tables/{number}")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> create(@PathVariable int number) {
    var t = tables.create(number);
    return Map.of("number", t.id);
  }

  @GetMapping(value = "/tables/{number}/qr", produces = "image/png")
  public byte[] qr(@PathVariable int number) throws Exception {
    return tables.qr(number);
  }
}
