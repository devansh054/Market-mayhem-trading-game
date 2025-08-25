package com.example.marketmayhem.risk;

import com.example.marketmayhem.dto.PlaceOrderMessage;
import com.example.marketmayhem.model.RiskViolationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MaxOrderSizeRule implements RiskRule {
    
    @Value("${game.risk.max-order-size:50000}")
    private long maxOrderSize;
    
    @Override
    public Optional<RiskViolation> validate(PlaceOrderMessage order) {
        if (order.qty() > maxOrderSize) {
            return Optional.of(new RiskViolation(
                RiskViolationType.MAX_ORDER_SIZE,
                String.format("Order quantity %d exceeds maximum allowed %d", order.qty(), maxOrderSize)
            ));
        }
        return Optional.empty();
    }
}