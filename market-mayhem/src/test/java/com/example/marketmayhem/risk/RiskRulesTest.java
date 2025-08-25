package com.example.marketmayhem.risk;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.marketmayhem.dto.PlaceOrderMessage;
import com.example.marketmayhem.model.OrderType;
import com.example.marketmayhem.model.RiskViolationType;
import com.example.marketmayhem.model.Side;

class RiskRulesTest {
    
    private MaxOrderSizeRule maxOrderSizeRule;
    private RestrictedSymbolRule restrictedSymbolRule;
    
    @BeforeEach
    void setUp() {
        maxOrderSizeRule = new MaxOrderSizeRule();
        ReflectionTestUtils.setField(maxOrderSizeRule, "maxOrderSize", 50000L);
        
        restrictedSymbolRule = new RestrictedSymbolRule();
        ReflectionTestUtils.setField(restrictedSymbolRule, "restrictedSymbols", List.of("GME"));
    }
    
    @Test
    void testMaxOrderSizeRule_ValidOrder() {
        PlaceOrderMessage order = new PlaceOrderMessage(
            "Player1", "O1", "AAPL", Side.BUY, 10000L, OrderType.LIMIT, BigDecimal.valueOf(100.00)
        );
        
        Optional<RiskRule.RiskViolation> violation = maxOrderSizeRule.validate(order);
        assertTrue(violation.isEmpty());
    }
    
    @Test
    void testMaxOrderSizeRule_ViolatingOrder() {
        PlaceOrderMessage order = new PlaceOrderMessage(
            "Player1", "O1", "AAPL", Side.BUY, 60000L, OrderType.LIMIT, BigDecimal.valueOf(100.00)
        );
        
        Optional<RiskRule.RiskViolation> violation = maxOrderSizeRule.validate(order);
        assertTrue(violation.isPresent());
        assertEquals(RiskViolationType.MAX_ORDER_SIZE, violation.get().type());
        assertTrue(violation.get().detail().contains("60000"));
        assertTrue(violation.get().detail().contains("50000"));
    }
    
    @Test
    void testRestrictedSymbolRule_ValidSymbol() {
        PlaceOrderMessage order = new PlaceOrderMessage(
            "Player1", "O1", "AAPL", Side.BUY, 1000L, OrderType.LIMIT, BigDecimal.valueOf(100.00)
        );
        
        Optional<RiskRule.RiskViolation> violation = restrictedSymbolRule.validate(order);
        assertTrue(violation.isEmpty());
    }
    
    @Test
    void testRestrictedSymbolRule_RestrictedSymbol() {
        PlaceOrderMessage order = new PlaceOrderMessage(
            "Player1", "O1", "GME", Side.BUY, 1000L, OrderType.LIMIT, BigDecimal.valueOf(100.00)
        );
        
        Optional<RiskRule.RiskViolation> violation = restrictedSymbolRule.validate(order);
        assertTrue(violation.isPresent());
        assertEquals(RiskViolationType.RESTRICTED_SYMBOL, violation.get().type());
        assertTrue(violation.get().detail().contains("GME"));
    }
    
    @Test
    void testRestrictedSymbolRule_CaseSensitive() {
        PlaceOrderMessage order = new PlaceOrderMessage(
            "Player1", "O1", "gme", Side.BUY, 1000L, OrderType.LIMIT, BigDecimal.valueOf(100.00)
        );
        
        Optional<RiskRule.RiskViolation> violation = restrictedSymbolRule.validate(order);
        assertTrue(violation.isEmpty()); // Case sensitive, so "gme" is not restricted
    }
    
    @Test
    void testEdgeCases() {
        // Test exact limit
        PlaceOrderMessage exactLimitOrder = new PlaceOrderMessage(
            "Player1", "O1", "AAPL", Side.BUY, 50000L, OrderType.LIMIT, BigDecimal.valueOf(100.00)
        );
        
        Optional<RiskRule.RiskViolation> violation = maxOrderSizeRule.validate(exactLimitOrder);
        assertTrue(violation.isEmpty()); // Exactly at limit should be allowed
        
        // Test one over limit
        PlaceOrderMessage overLimitOrder = new PlaceOrderMessage(
            "Player1", "O1", "AAPL", Side.BUY, 50001L, OrderType.LIMIT, BigDecimal.valueOf(100.00)
        );
        
        violation = maxOrderSizeRule.validate(overLimitOrder);
        assertTrue(violation.isPresent());
    }
}