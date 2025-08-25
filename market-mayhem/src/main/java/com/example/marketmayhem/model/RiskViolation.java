package com.example.marketmayhem.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "risk_violations")
public class RiskViolation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String playerId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskViolationType type;
    
    @Column(nullable = false)
    private String detail;
    
    @Column(nullable = false)
    private Instant occurredAt = Instant.now();
    
    @Column
    private String orderId;
    
    // Constructors
    public RiskViolation() {}
    
    public RiskViolation(String playerId, RiskViolationType type, 
                         String detail, String orderId) {
        this.playerId = playerId;
        this.type = type;
        this.detail = detail;
        this.orderId = orderId;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    
    public RiskViolationType getType() { return type; }
    public void setType(RiskViolationType type) { this.type = type; }
    
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}