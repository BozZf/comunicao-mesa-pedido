package br.com.mesapedido.domain;

import jakarta.persistence.*;

@Entity
public class DiningTable {
  @Id public Integer id;

  @Column(nullable = false, unique = true)
  public String token;

  public boolean active;
}
