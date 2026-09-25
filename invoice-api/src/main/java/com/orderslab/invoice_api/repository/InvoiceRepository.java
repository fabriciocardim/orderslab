package com.orderslab.invoice_api.repository;

import com.orderslab.invoice_api.model.Invoice;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
}
