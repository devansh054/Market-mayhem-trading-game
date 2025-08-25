package com.example.marketmayhem.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trades_symbol", columnList = "symbol")
})
public class Trade {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long buyOrderId;
    
    @Column(nullable = false)
    private Long sellOrderId;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private Long qty;
    
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal price;
    
    @Column(nullable = false)
    private Instant executedAt = Instant.now();
    
    // Constructors
    public Trade() {}
    
    public Trade(Long buyOrderId, Long sellOrderId, String symbol, 
                 Long qty, BigDecimal price) {
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.qty = qty;
        this.price = price;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getBuyOrderId() { return buyOrderId; }
    public void setBuyOrderId(Long buyOrderId) { this.buyOrderId = buyOrderId; }
    
    public Long getSellOrderId() { return sellOrderId; }
    public void setSellOrderId(Long sellOrderId) { this.sellOrderId = sellOrderId; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public Long getQty() { return qty; }
    public void setQty(Long qty) { this.qty = qty; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}