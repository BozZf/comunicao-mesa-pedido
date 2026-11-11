package br.com.mesapedido.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {
  @Id public UUID id;
  @Version public long version;

  @Column(nullable = false, unique = true)
  public UUID requestKey;

  @Column(nullable = false)
  public String accessToken;

  public Integer tableNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public OrderStatus status;

  @Column(nullable = false, precision = 12, scale = 2)
  public BigDecimal total;

  @Column(length = 500)
  public String notes;

  public Instant createdAt;
  public Instant paidAt;

  @Column(unique = true)
  public String paymentId;

  @Column(length = 2000)
  public String checkoutUrl;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "order_id")
  @OrderBy("id")
  public List<OrderItem> items = new ArrayList<>();
}
