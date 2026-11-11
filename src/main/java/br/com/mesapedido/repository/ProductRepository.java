package br.com.mesapedido.repository;

import br.com.mesapedido.domain.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
  List<Product> findByAvailableTrueOrderByIdAsc();
}
