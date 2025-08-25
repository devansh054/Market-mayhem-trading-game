package com.example.marketmayhem.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_scores")
public class PlayerScore {
    
    @Id
    private String playerId;
    
    @Column(nullable = false)
    private Integer matches = 0;
    
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal pnl = BigDecimal.ZERO;
    
    @Column(nullable = false)
    private Integer violations = 0;
    
    // Constructors
    public PlayerScore() {}
    
    public PlayerScore(String playerId) {
        this.playerId = playerId;
    }
    
    // Getters and Setters
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    
    public Integer getMatches() { return matches; }
    public void setMatches(Integer matches) { this.matches = matches; }
    
    public BigDecimal getPnl() { return pnl; }
    public void setPnl(BigDecimal pnl) { this.pnl = pnl; }
    
    public Integer getViolations() { return violations; }
    public void setViolations(Integer violations) { this.violations = violations; }
    
    public void addPnl(BigDecimal amount) {
        this.pnl = this.pnl.add(amount);
    }
    
    public void incrementViolations() {
        this.violations++;
    }
    
    public void incrementMatches() {
        this.matches++;
    }
}