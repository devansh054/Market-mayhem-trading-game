package com.example.marketmayhem.engine;

import com.example.marketmayhem.dto.BookLevel;
import com.example.marketmayhem.dto.BookUpdate;
import com.example.marketmayhem.model.Order;
import com.example.marketmayhem.model.Side;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

/**
 * Thread-safe order book implementation for a single symbol.
 * 
 * Features:
 * - Price-time priority matching
 * - FIFO execution within price levels
 * - Concurrent read/write access with StampedLock
 * - Real-time book snapshots for client updates
 */
public class OrderBook {
    private final String symbol;
    
    // Bids: highest price first (descending order)
    private final NavigableMap<BigDecimal, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    
    // Asks: lowest price first (ascending order) 
    private final NavigableMap<BigDecimal, Deque<Order>> asks = new TreeMap<>();
    
    // Thread-safe access control
    private final StampedLock lock = new StampedLock();
    
    public OrderBook(String symbol) {
        this.symbol = symbol;
    }
    
    /**
     * Add an order to the appropriate side of the book.
     * Orders are queued FIFO within each price level.
     */
    public void addOrder(Order order) {
        long stamp = lock.writeLock();
        try {
            NavigableMap<BigDecimal, Deque<Order>> book = getBookForSide(order.getSide());
            book.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).addLast(order);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    /**
     * Remove an order from the book.
     * Cleans up empty price levels automatically.
     */
    public boolean removeOrder(Order order) {
        long stamp = lock.writeLock();
        try {
            NavigableMap<BigDecimal, Deque<Order>> book = getBookForSide(order.getSide());
            Deque<Order> level = book.get(order.getPrice());
            if (level != null) {
                boolean removed = level.remove(order);
                if (level.isEmpty()) {
                    book.remove(order.getPrice());
                }
                return removed;
            }
            return false;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    /**
     * Get the best bid order (highest price).
     */
    public Order getBestBid() {
        long stamp = lock.readLock();
        try {
            Map.Entry<BigDecimal, Deque<Order>> entry = bids.firstEntry();
            return (entry != null && !entry.getValue().isEmpty()) ? 
                entry.getValue().peekFirst() : null;
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    /**
     * Get the best ask order (lowest price).
     */
    public Order getBestAsk() {
        long stamp = lock.readLock();
        try {
            Map.Entry<BigDecimal, Deque<Order>> entry = asks.firstEntry();
            return (entry != null && !entry.getValue().isEmpty()) ? 
                entry.getValue().peekFirst() : null;
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    /**
     * Get the best price for a given side.
     */
    public BigDecimal getBestPrice(Side side) {
        Order bestOrder = (side == Side.BUY) ? getBestBid() : getBestAsk();
        return bestOrder != null ? bestOrder.getPrice() : null;
    }
    
    /**
     * Get all orders at a specific price level.
     */
    public List<Order> getOrdersAtPrice(Side side, BigDecimal price) {
        long stamp = lock.readLock();
        try {
            NavigableMap<BigDecimal, Deque<Order>> book = getBookForSide(side);
            Deque<Order> level = book.get(price);
            return level != null ? new ArrayList<>(level) : Collections.emptyList();
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    /**
     * Get total quantity available at a price level.
     */
    public long getQuantityAtPrice(Side side, BigDecimal price) {
        return getOrdersAtPrice(side, price).stream()
            .mapToLong(Order::getRemainingQty)
            .sum();
    }
    
    /**
     * Create a market data snapshot showing top N price levels.
     * This is used for broadcasting book updates to clients.
     */
    public BookUpdate getSnapshot(int levels) {
        long stamp = lock.readLock();
        try {
            List<BookLevel> bidLevels = bids.entrySet().stream()
                .limit(levels)
                .map(this::createBookLevel)
                .collect(Collectors.toList());
                
            List<BookLevel> askLevels = asks.entrySet().stream()
                .limit(levels)
                .map(this::createBookLevel)
                .collect(Collectors.toList());
                
            return new BookUpdate(symbol, bidLevels, askLevels, Instant.now());
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    /**
     * Check if there are any orders that can cross with the given order.
     * Used for matching logic.
     */
    public boolean canCross(Order order) {
        if (order.getSide() == Side.BUY) {
            Order bestAsk = getBestAsk();
            return bestAsk != null && order.getPrice().compareTo(bestAsk.getPrice()) >= 0;
        } else {
            Order bestBid = getBestBid();
            return bestBid != null && order.getPrice().compareTo(bestBid.getPrice()) <= 0;
        }
    }
    
    /**
     * Get the spread between best bid and ask.
     */
    public BigDecimal getSpread() {
        Order bestBid = getBestBid();
        Order bestAsk = getBestAsk();
        
        if (bestBid != null && bestAsk != null) {
            return bestAsk.getPrice().subtract(bestBid.getPrice());
        }
        return null;
    }
    
    /**
     * Get mid-market price (average of best bid and ask).
     */
    public BigDecimal getMidPrice() {
        Order bestBid = getBestBid();
        Order bestAsk = getBestAsk();
        
        if (bestBid != null && bestAsk != null) {
            return bestBid.getPrice().add(bestAsk.getPrice())
                .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        }
        return null;
    }
    
    /**
     * Check if the order book is empty.
     */
    public boolean isEmpty() {
        long stamp = lock.readLock();
        try {
            return bids.isEmpty() && asks.isEmpty();
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    /**
     * Get total number of orders in the book.
     */
    public int getOrderCount() {
        long stamp = lock.readLock();
        try {
            return bids.values().stream().mapToInt(Deque::size).sum() +
                   asks.values().stream().mapToInt(Deque::size).sum();
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    /**
     * Get symbol for this order book.
     */
    public String getSymbol() {
        return symbol;
    }
    
    /**
     * Clear all orders from the book.
     */
    public void clear() {
        long stamp = lock.writeLock();
        try {
            bids.clear();
            asks.clear();
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    // Helper methods
    
    private NavigableMap<BigDecimal, Deque<Order>> getBookForSide(Side side) {
        return (side == Side.BUY) ? bids : asks;
    }
    
    private BookLevel createBookLevel(Map.Entry<BigDecimal, Deque<Order>> entry) {
        long totalQty = entry.getValue().stream()
            .mapToLong(Order::getRemainingQty)
            .sum();
        return new BookLevel(entry.getKey(), totalQty);
    }
    
    @Override
    public String toString() {
        long stamp = lock.readLock();
        try {
            return String.format("OrderBook[%s: %d bids, %d asks]", 
                symbol, bids.size(), asks.size());
        } finally {
            lock.unlockRead(stamp);
        }
    }
}