package br.com.mesapedido.dto;

import br.com.mesapedido.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class ApiDtos {
  private ApiDtos() {}

  public record ItemRequest(@NotNull @Positive Long productId, @Min(1) @Max(20) int quantity) {}

  public record CreateOrder(
      @NotBlank @Size(max = 100) String tableToken,
      @NotNull UUID requestKey,
      @NotEmpty @Size(max = 40) List<@NotNull @Valid ItemRequest> items,
      @Size(max = 500) String notes) {}

  public record ChangeStatus(@NotNull OrderStatus status) {}

  public record ProductView(
      Long id, String name, String description, Category category, BigDecimal price) {
    public static ProductView of(Product p) {
      return new ProductView(p.id, p.name, p.description, p.category, p.price);
    }
  }

  public record ItemView(String name, int quantity, BigDecimal unitPrice) {}

  public record OrderView(
      UUID id,
      Integer tableNumber,
      OrderStatus status,
      BigDecimal total,
      String notes,
      Instant createdAt,
      Instant paidAt,
      String paymentId,
      List<ItemView> items) {
    public static OrderView of(CustomerOrder o) {
      return new OrderView(
          o.id,
          o.tableNumber,
          o.status,
          o.total,
          o.notes,
          o.createdAt,
          o.paidAt,
          o.paymentId,
          o.items.stream().map(i -> new ItemView(i.name, i.quantity, i.unitPrice)).toList());
    }
  }

  public record CreatedOrder(OrderView order, String accessToken) {}

  public record Checkout(String url, boolean demo) {}
}
