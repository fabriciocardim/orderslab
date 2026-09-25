package com.orderslab.payment_api.repository;

import com.orderslab.payment_api.model.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
