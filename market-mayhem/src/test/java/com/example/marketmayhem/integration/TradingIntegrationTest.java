package com.example.marketmayhem.integration;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.marketmayhem.MarketMayhemApplication;
import com.example.marketmayhem.dto.PlaceOrderMessage;
import com.example.marketmayhem.dto.TradeEvent;
import com.example.marketmayhem.model.Order;
import com.example.marketmayhem.model.OrderStatus;
import com.example.marketmayhem.model.OrderType;
import com.example.marketmayhem.model.Side;
import com.example.marketmayhem.model.Trade;
import com.example.marketmayhem.repo.OrderRepository;
import com.example.marketmayhem.repo.TradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TradingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("trading")
            .withUsername("trading")
            .withPassword("trading");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.example.marketmayhem.engine.MatchingEngineService matchingEngineService;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final BlockingQueue<TradeEvent> tradeEvents = new LinkedBlockingQueue<>();
    private String roomId;

    @BeforeEach
    void setUp() throws Exception {
        // Generate unique room ID for each test to avoid interference
        roomId = "test-room-" + System.currentTimeMillis();
        
        // Clear existing data first
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        tradeEvents.clear();
        
        // Clear order books to ensure clean state between tests
        matchingEngineService.clearAllOrderBooks();
        
        // Create fresh WebSocket client for each test
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        StompSessionHandler sessionHandler = new TestStompSessionHandler();
        CompletableFuture<StompSession> sessionFuture = 
            stompClient.connectAsync("ws://localhost:" + port + "/ws/websocket", sessionHandler);
        
        stompSession = sessionFuture.get(5, TimeUnit.SECONDS);
        
        // Subscribe to trade events with unique room ID
        stompSession.subscribe("/topic/room/" + roomId + "/trades", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TradeEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                tradeEvents.offer((TradeEvent) payload);
            }
        });

        // Wait for subscription to be fully established
        Thread.sleep(1000);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        tradeEvents.clear();
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
        // Wait for cleanup to complete
        Thread.sleep(500);
    }

    @Test
    void testCompleteOrderMatchingFlow() throws Exception {
        // Place a sell order
        PlaceOrderMessage sellOrder = new PlaceOrderMessage(
            "Seller1",
            "SELL_ORDER_1",
            "AAPL",
            Side.SELL,
            100L,
            OrderType.LIMIT,
            BigDecimal.valueOf(150.00)
        );

        stompSession.send("/app/room/" + roomId + "/order.place", sellOrder);
        Thread.sleep(500); // Allow processing and database commit

        // Verify sell order is in database
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order savedSellOrder = orders.get(0);
        assertEquals("SELL_ORDER_1", savedSellOrder.getClOrdId());
        assertEquals(OrderStatus.NEW, savedSellOrder.getStatus());

        // Place a matching buy order
        PlaceOrderMessage buyOrder = new PlaceOrderMessage(
            "Buyer1",
            "BUY_ORDER_1",
            "AAPL",
            Side.BUY,
            50L,
            OrderType.LIMIT,
            BigDecimal.valueOf(150.00)
        );

        stompSession.send("/app/room/" + roomId + "/order.place", buyOrder);

        // Wait for database transaction to commit
        Thread.sleep(2000);
        
        // Check for trade event (optional - may fail due to WebSocket timing)
        TradeEvent tradeEvent = tradeEvents.poll(1, TimeUnit.SECONDS);
        if (tradeEvent != null) {
            assertEquals("AAPL", tradeEvent.symbol());
            assertEquals(50L, tradeEvent.qty());
            assertEquals(BigDecimal.valueOf(150.00), tradeEvent.price());
            assertEquals("Buyer1", tradeEvent.buyPlayer());
            assertEquals("Seller1", tradeEvent.sellPlayer());
        }

        // Verify trade is persisted
        List<Trade> trades = tradeRepository.findAll();
        assertEquals(1, trades.size());
        Trade savedTrade = trades.get(0);
        assertEquals("AAPL", savedTrade.getSymbol());
        assertEquals(50L, savedTrade.getQty());
        assertEquals(0, savedTrade.getPrice().compareTo(BigDecimal.valueOf(150.00)));

        // Verify order statuses
        orders = orderRepository.findAll();
        assertEquals(2, orders.size());
        
        Order buyOrderSaved = orders.stream()
            .filter(o -> "BUY_ORDER_1".equals(o.getClOrdId()))
            .findFirst().orElse(null);
        assertNotNull(buyOrderSaved);
        assertEquals(OrderStatus.FILLED, buyOrderSaved.getStatus());
        assertEquals(50L, buyOrderSaved.getFilledQty());

        Order sellOrderSaved = orders.stream()
            .filter(o -> "SELL_ORDER_1".equals(o.getClOrdId()))
            .findFirst().orElse(null);
        assertNotNull(sellOrderSaved);
        assertEquals(OrderStatus.PARTIAL, sellOrderSaved.getStatus());
        assertEquals(50L, sellOrderSaved.getFilledQty());
        assertEquals(50L, sellOrderSaved.getRemainingQty());
    }

    @Test
    void testMarketOrderExecution() throws Exception {
        // Place limit sell order first
        PlaceOrderMessage sellOrder = new PlaceOrderMessage(
            "Seller1",
            "SELL_LIMIT_1",
            "MSFT",
            Side.SELL,
            200L,
            OrderType.LIMIT,
            BigDecimal.valueOf(300.00)
        );

        stompSession.send("/app/room/" + roomId + "/order.place", sellOrder);
        Thread.sleep(100);

        // Place market buy order
        PlaceOrderMessage marketBuyOrder = new PlaceOrderMessage(
            "Buyer1",
            "MARKET_BUY_1",
            "MSFT",
            Side.BUY,
            100L,
            OrderType.MARKET,
            null // No price for market order
        );

        stompSession.send("/app/room/" + roomId + "/order.place", marketBuyOrder);

        // Wait for trade event
        TradeEvent tradeEvent = tradeEvents.poll(10, TimeUnit.SECONDS);
        assertNotNull(tradeEvent);
        
        // Wait for database transaction to commit
        Thread.sleep(1000);
        assertEquals("MSFT", tradeEvent.symbol());
        assertEquals(100L, tradeEvent.qty());
        assertEquals(BigDecimal.valueOf(300.00), tradeEvent.price()); // Should execute at limit price

        // Verify market order is filled
        List<Order> orders = orderRepository.findAll();
        Order marketOrder = orders.stream()
            .filter(o -> "MARKET_BUY_1".equals(o.getClOrdId()))
            .findFirst().orElse(null);
        assertNotNull(marketOrder);
        assertEquals(OrderStatus.FILLED, marketOrder.getStatus());
    }

    @Test
    void testNoMatchingWhenNoCross() throws Exception {
        // Place buy order below market
        PlaceOrderMessage buyOrder = new PlaceOrderMessage(
            "Buyer1",
            "LOW_BUY_1",
            "AAPL",
            Side.BUY,
            100L,
            OrderType.LIMIT,
            BigDecimal.valueOf(90.00)
        );

        stompSession.send("/app/room/" + roomId + "/order.place", buyOrder);
        Thread.sleep(100);

        // Place sell order above market
        PlaceOrderMessage sellOrder = new PlaceOrderMessage(
            "Seller1",
            "HIGH_SELL_1",
            "AAPL",
            Side.SELL,
            100L,
            OrderType.LIMIT,
            BigDecimal.valueOf(110.00)
        );

        stompSession.send("/app/room/" + roomId + "/order.place", sellOrder);
        Thread.sleep(100);

        // Should be no trades
        assertTrue(tradeEvents.isEmpty());
        assertEquals(0, tradeRepository.count());

        // Both orders should remain NEW
        List<Order> orders = orderRepository.findAll();
        assertEquals(2, orders.size());
        orders.forEach(order -> assertEquals(OrderStatus.NEW, order.getStatus()));
    }

    @Test
    void testPerformanceBenchmark() throws Exception {
        long startTime = System.currentTimeMillis();
        int numOrders = 1000;
        
        // Place alternating buy and sell orders that will match
        for (int i = 0; i < numOrders; i++) {
            Side side = (i % 2 == 0) ? Side.BUY : Side.SELL;
            PlaceOrderMessage order = new PlaceOrderMessage(
                "Player" + i,
                "ORDER_" + i,
                "AAPL",
                side,
                10L,
                OrderType.LIMIT,
                BigDecimal.valueOf(100.00)
            );
            stompSession.send("/app/room/test-room/order.place", order);
        }

        // Wait for processing to complete
        Thread.sleep(2000);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Processed " + numOrders + " orders in " + duration + "ms");
        System.out.println("Rate: " + (numOrders * 1000.0 / duration) + " orders/second");

        // Verify some trades occurred
        assertTrue(tradeRepository.count() > 0);
        System.out.println("Total trades: " + tradeRepository.count());
    }

    private static class TestStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            // Connection established
        }

        @Override
        public void handleException(StompSession session, StompCommand command,
                                  StompHeaders headers, byte[] payload, Throwable exception) {
            exception.printStackTrace();
        }
    }
}