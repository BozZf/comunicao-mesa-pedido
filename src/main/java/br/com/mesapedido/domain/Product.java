package br.com.mesapedido.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Product {
  @Id public Long id;

  @Column(nullable = false)
  public String name;

  public String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Category category;

  @Column(nullable = false, precision = 12, scale = 2)
  public BigDecimal price;

  public boolean available;
}
