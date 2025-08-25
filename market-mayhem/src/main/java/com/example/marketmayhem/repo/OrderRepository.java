package com.example.marketmayhem.repo;

import com.example.marketmayhem.model.Order;
import com.example.marketmayhem.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByClOrdId(String clOrdId);
    List<Order> findBySymbolAndStatusIn(String symbol, List<OrderStatus> statuses);
    List<Order> findByPlayerIdAndStatus(String playerId, OrderStatus status);
}