package com.example.marketmayhem.engine;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.marketmayhem.dto.BookUpdate;
import com.example.marketmayhem.model.Order;
import com.example.marketmayhem.model.OrderType;
import com.example.marketmayhem.model.Side;

class OrderBookTest {
    
    private OrderBook orderBook;
    
    @BeforeEach
    void setUp() {
        orderBook = new OrderBook("AAPL");
    }
    
    @Test
    void testAddAndRetrieveBestBid() {
        Order order1 = new Order("P1", "O1", "AAPL", Side.BUY, 100L, 
                                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        Order order2 = new Order("P2", "O2", "AAPL", Side.BUY, 200L, 
                                BigDecimal.valueOf(101.00), OrderType.LIMIT);
        
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        
        Order bestBid = orderBook.getBestBid();
        assertNotNull(bestBid);
        assertEquals(BigDecimal.valueOf(101.00), bestBid.getPrice());
        assertEquals("O2", bestBid.getClOrdId());
    }
    
    @Test
    void testAddAndRetrieveBestAsk() {
        Order order1 = new Order("P1", "O1", "AAPL", Side.SELL, 100L, 
                                BigDecimal.valueOf(102.00), OrderType.LIMIT);
        Order order2 = new Order("P2", "O2", "AAPL", Side.SELL, 200L, 
                                BigDecimal.valueOf(101.00), OrderType.LIMIT);
        
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        
        Order bestAsk = orderBook.getBestAsk();
        assertNotNull(bestAsk);
        assertEquals(BigDecimal.valueOf(101.00), bestAsk.getPrice());
        assertEquals("O2", bestAsk.getClOrdId());
    }
    
    @Test
    void testRemoveOrder() {
        Order order = new Order("P1", "O1", "AAPL", Side.BUY, 100L, 
                               BigDecimal.valueOf(100.00), OrderType.LIMIT);
        
        orderBook.addOrder(order);
        assertNotNull(orderBook.getBestBid());
        
        boolean removed = orderBook.removeOrder(order);
        assertTrue(removed);
        assertNull(orderBook.getBestBid());
    }
    
    @Test
    void testFIFOOrdering() {
        Order order1 = new Order("P1", "O1", "AAPL", Side.BUY, 100L, 
                                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        Order order2 = new Order("P2", "O2", "AAPL", Side.BUY, 200L, 
                                BigDecimal.valueOf(100.00), OrderType.LIMIT);
        
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        
        Order bestBid = orderBook.getBestBid();
        assertEquals("O1", bestBid.getClOrdId()); // First in, first out
    }
    
    @Test
    void testSnapshot() {
        // Add some orders
        orderBook.addOrder(new Order("P1", "O1", "AAPL", Side.BUY, 100L, 
                                   BigDecimal.valueOf(100.00), OrderType.LIMIT));
        orderBook.addOrder(new Order("P2", "O2", "AAPL", Side.BUY, 200L, 
                                   BigDecimal.valueOf(99.00), OrderType.LIMIT));
        orderBook.addOrder(new Order("P3", "O3", "AAPL", Side.SELL, 150L, 
                                   BigDecimal.valueOf(101.00), OrderType.LIMIT));
        orderBook.addOrder(new Order("P4", "O4", "AAPL", Side.SELL, 250L, 
                                   BigDecimal.valueOf(102.00), OrderType.LIMIT));
        
        BookUpdate snapshot = orderBook.getSnapshot(10);
        
        assertEquals("AAPL", snapshot.symbol());
        assertEquals(2, snapshot.bids().size());
        assertEquals(2, snapshot.asks().size());
        
        // Check bid ordering (highest first)
        assertEquals(BigDecimal.valueOf(100.00), snapshot.bids().get(0).price());
        assertEquals(BigDecimal.valueOf(99.00), snapshot.bids().get(1).price());
        
        // Check ask ordering (lowest first)
        assertEquals(BigDecimal.valueOf(101.00), snapshot.asks().get(0).price());
        assertEquals(BigDecimal.valueOf(102.00), snapshot.asks().get(1).price());
    }
    
    @Test
    void testEmptyBook() {
        assertTrue(orderBook.isEmpty());
        assertNull(orderBook.getBestBid());
        assertNull(orderBook.getBestAsk());
        
        BookUpdate snapshot = orderBook.getSnapshot(5);
        assertTrue(snapshot.bids().isEmpty());
        assertTrue(snapshot.asks().isEmpty());
    }
}