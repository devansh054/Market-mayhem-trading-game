package com.example.marketmayhem.repo;

import com.example.marketmayhem.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findBySymbolOrderByExecutedAtDesc(String symbol);
    
    @Query("SELECT t FROM Trade t WHERE t.buyOrderId = :orderId OR t.sellOrderId = :orderId")
    List<Trade> findByOrderId(@Param("orderId") Long orderId);
}