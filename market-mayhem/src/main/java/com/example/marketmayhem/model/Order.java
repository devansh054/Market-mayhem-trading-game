package com.example.marketmayhem.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_symbol", columnList = "symbol"),
    @Index(name = "idx_orders_player_id", columnList = "playerId")
})
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String playerId;
    
    @Column(unique = true, nullable = false)
    private String clOrdId;
    
    @Column(nullable = false)
    private String symbol;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;
    
    @Column(nullable = false)
    private Long qty;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal price;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;
    
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column
    private Long filledQty = 0L;
    
    @Column
    private Long remainingQty;
    
    // Constructors
    public Order() {}
    
    public Order(String playerId, String clOrdId, String symbol, Side side, 
                 Long qty, BigDecimal price, OrderType type) {
        this.playerId = playerId;
        this.clOrdId = clOrdId;
        this.symbol = symbol;
        this.side = side;
        this.qty = qty;
        this.price = price;
        this.type = type;
        this.remainingQty = qty;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    
    public String getClOrdId() { return clOrdId; }
    public void setClOrdId(String clOrdId) { this.clOrdId = clOrdId; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public Side getSide() { return side; }
    public void setSide(Side side) { this.side = side; }
    
    public Long getQty() { return qty; }
    public void setQty(Long qty) { this.qty = qty; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }
    
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Long getFilledQty() { return filledQty; }
    public void setFilledQty(Long filledQty) { this.filledQty = filledQty; }
    
    public Long getRemainingQty() { return remainingQty; }
    public void setRemainingQty(Long remainingQty) { this.remainingQty = remainingQty; }
    
    public void addFill(Long fillQty) {
        this.filledQty += fillQty;
        this.remainingQty -= fillQty;
        if (this.remainingQty <= 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIAL;
        }
    }
}