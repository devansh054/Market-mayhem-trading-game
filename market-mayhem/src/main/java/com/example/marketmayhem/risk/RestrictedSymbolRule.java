package com.example.marketmayhem.risk;

import com.example.marketmayhem.dto.PlaceOrderMessage;
import com.example.marketmayhem.model.RiskViolationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RestrictedSymbolRule implements RiskRule {
    
    @Value("${game.risk.restricted-symbols:#{{'GME'}}}")
    private List<String> restrictedSymbols;
    
    @Override
    public Optional<RiskViolation> validate(PlaceOrderMessage order) {
        if (restrictedSymbols.contains(order.symbol())) {
            return Optional.of(new RiskViolation(
                RiskViolationType.RESTRICTED_SYMBOL,
                String.format("Symbol %s is restricted for trading", order.symbol())
            ));
        }
        return Optional.empty();
    }
}