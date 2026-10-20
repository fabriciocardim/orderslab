package com.orderslab.invoice_api.repository;

import com.orderslab.invoice_api.model.Invoice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    boolean existsBySourceEventId(UUID sourceEventId);

    boolean existsByOrderIdAndSourceEventIdIsNotNull(String orderId);

    /** Registros de um pedido, do mais recente para o mais antigo (índice ix_invoices_order_id). */
    List<Invoice> findByOrderIdOrderByCreatedAtDesc(String orderId);
}
