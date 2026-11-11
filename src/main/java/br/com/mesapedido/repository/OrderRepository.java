package br.com.mesapedido.repository;

import br.com.mesapedido.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {
  @Query(
      "select o.id from CustomerOrder o where"
          + " o.status=br.com.mesapedido.domain.OrderStatus.AGUARDANDO_PAGAMENTO and o.checkoutUrl"
          + " is not null")
  List<UUID> pendingPayments();

  Optional<CustomerOrder> findByRequestKey(UUID key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from CustomerOrder o where o.id=:id")
  Optional<CustomerOrder> lock(@Param("id") UUID id);

  List<CustomerOrder> findTop100ByStatusNotOrderByCreatedAtDesc(OrderStatus status);
}
