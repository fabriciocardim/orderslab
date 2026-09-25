package com.orderslab.order_api.repository;

import com.orderslab.order_api.model.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
