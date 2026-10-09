package com.orderslab.payment_api.outbox;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Lote mais antigo primeiro, com lock bloqueante (sem SKIP LOCKED): relays de réplicas
     * diferentes se serializam, preservando a ordem por pedido.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e order by e.id")
    List<OutboxEvent> findBatchForUpdate(Pageable pageable);
}
