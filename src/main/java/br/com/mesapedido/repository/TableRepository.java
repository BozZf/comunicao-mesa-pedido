package br.com.mesapedido.repository;

import br.com.mesapedido.domain.DiningTable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableRepository extends JpaRepository<DiningTable, Integer> {
  Optional<DiningTable> findByTokenAndActiveTrue(String token);
}
