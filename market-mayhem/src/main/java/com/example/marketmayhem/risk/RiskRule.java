package com.example.marketmayhem.risk;

import com.example.marketmayhem.dto.PlaceOrderMessage;
import com.example.marketmayhem.model.RiskViolationType;

import java.util.Optional;

public interface RiskRule {
    Optional<RiskViolation> validate(PlaceOrderMessage order);
    
    record RiskViolation(RiskViolationType type, String detail) {}
}