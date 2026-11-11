package br.com.mesapedido.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false, precision = 12, scale = 2)
  public BigDecimal unitPrice;

  public int quantity;
  public Long productId;
}
