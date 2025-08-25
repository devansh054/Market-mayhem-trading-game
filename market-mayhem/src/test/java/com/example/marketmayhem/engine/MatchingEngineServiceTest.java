package com.example.marketmayhem.engine;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.example.marketmayhem.model.Order;
import com.example.marketmayhem.model.OrderStatus;
import com.example.marketmayhem.model.OrderType;
import com.example.marketmayhem.model.Side;
import com.example.marketmayhem.model.Trade;
import com.example.marketmayhem.repo.OrderRepository;
import com.example.marketmayhem.repo.TradeRepository;
import com.example.marketmayhem.service.LeaderboardService;

@ExtendWith(MockitoExtension.class)
// Make default stubs lenient so tests that don't hit them won't fail
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingEngineServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private LeaderboardService leaderboardService;

    private MatchingEngineService matchingEngine;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngineService(
                orderRepository, tradeRepository, messagingTemplate, leaderboardService);

        // --- sane default behavior for mocks (lenient) ---

        // Return the same Trade instance we pass in (prevents null when engine saves)
        lenient().when(tradeRepository.save(any(Trade.class)))
                 .thenAnswer(inv -> inv.getArgument(0));

        // Assign an id to Orders that don't have one, and return the SAME instance
        AtomicLong seq = new AtomicLong(1);
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(seq.getAndIncrement());
            return o;
        });
    }

    @Test
    void testLimitOrderCrossing() {
        // Setup: Create a sell order in the book first
        Order sellOrder = new Order("Seller", "S1", "AAPL", Side.SELL, 100L,
                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        sellOrder.setId(2L);

        when(orderRepository.findById(2L)).thenReturn(Optional.of(sellOrder));

        // Add sell order to book
        matchingEngine.processOrder(sellOrder, "room1");

        // Now create a crossing buy order
        Order buyOrder = new Order("Buyer", "B1", "AAPL", Side.BUY, 50L,
                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(buyOrder));

        MatchingEngineService.MatchResult result = matchingEngine.processOrder(buyOrder, "room1");

        // Verify trade was created
        assertNotNull(result);
        assertEquals(1, result.getTrades().size());

        Trade trade = result.getTrades().get(0);
        assertEquals(50L, trade.getQty());
        assertEquals(BigDecimal.valueOf(100.00), trade.getPrice());

        // Verify order statuses
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus());
        assertEquals(50L, buyOrder.getFilledQty());
        assertEquals(0L, buyOrder.getRemainingQty());

        verify(tradeRepository).save(any(Trade.class));
        verify(leaderboardService, times(2)).updatePlayerPnl(any(), any());
    }

    @Test
    void testPartialFill() {
        // Setup: Large sell order
        Order sellOrder = new Order("Seller", "S1", "AAPL", Side.SELL, 200L,
                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        sellOrder.setId(2L);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(sellOrder));

        matchingEngine.processOrder(sellOrder, "room1");

        // Smaller buy order
        Order buyOrder = new Order("Buyer", "B1", "AAPL", Side.BUY, 50L,
                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(buyOrder));

        MatchingEngineService.MatchResult result = matchingEngine.processOrder(buyOrder, "room1");

        // Verify partial fill
        assertEquals(1, result.getTrades().size());
        assertEquals(50L, result.getTrades().get(0).getQty());

        // Buy order should be fully filled
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus());

        // Sell order should be partially filled
        assertEquals(OrderStatus.PARTIAL, sellOrder.getStatus());
        assertEquals(50L, sellOrder.getFilledQty());
        assertEquals(150L, sellOrder.getRemainingQty());
    }

    @Test
    void testMarketOrderExecution() {
        // Setup: Sell order in book
        Order sellOrder = new Order("Seller", "S1", "AAPL", Side.SELL, 100L,
                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        sellOrder.setId(2L);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(sellOrder));

        matchingEngine.processOrder(sellOrder, "room1");

        // Market buy order
        Order marketOrder = new Order("Buyer", "M1", "AAPL", Side.BUY, 50L,
                null, OrderType.MARKET);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(marketOrder));

        MatchingEngineService.MatchResult result = matchingEngine.processOrder(marketOrder, "room1");

        // Should execute at the sell order price
        assertEquals(1, result.getTrades().size());
        assertEquals(BigDecimal.valueOf(100.00), result.getTrades().get(0).getPrice());
        assertEquals(OrderStatus.FILLED, marketOrder.getStatus());
    }

    @Test
    void testNoCrossing() {
        // Buy order with price below market
        Order buyOrder = new Order("Buyer", "B1", "AAPL", Side.BUY, 100L,
                BigDecimal.valueOf(99.00), OrderType.LIMIT);

        // IMPORTANT: no stubbing of orderRepository.save() here,
        // because processOrder() doesn't save for non-crossing quotes.

        MatchingEngineService.MatchResult result = matchingEngine.processOrder(buyOrder, "room1");

        // Should not create any trades
        assertTrue(result.getTrades().isEmpty());
        assertEquals(OrderStatus.NEW, buyOrder.getStatus());
        assertEquals(100L, buyOrder.getRemainingQty());
    }

    @Test
    void testOrderCancellation() {
        // Create and add order to book
        Order order = new Order("Player", "O1", "AAPL", Side.BUY, 100L,
                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        order.setId(1L);

        when(orderRepository.findByClOrdId("O1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        matchingEngine.processOrder(order, "room1");

        // Cancel the order
        boolean cancelled = matchingEngine.cancelOrder("O1", "room1");
        assertTrue(cancelled);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        verify(orderRepository).save(order);
    }

    @Test
    void testCancelNonExistentOrder() {
        when(orderRepository.findByClOrdId("NONEXISTENT")).thenReturn(Optional.empty());
        boolean cancelled = matchingEngine.cancelOrder("NONEXISTENT", "room1");
        assertFalse(cancelled);
    }
}
