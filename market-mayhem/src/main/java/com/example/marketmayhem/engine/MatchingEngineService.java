package com.example.marketmayhem.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketmayhem.dto.BookUpdate;
import com.example.marketmayhem.dto.TradeEvent;
import com.example.marketmayhem.model.Order;
import com.example.marketmayhem.model.OrderStatus;
import com.example.marketmayhem.model.OrderType;
import com.example.marketmayhem.model.Side;
import com.example.marketmayhem.model.Trade;
import com.example.marketmayhem.repo.OrderRepository;
import com.example.marketmayhem.repo.TradeRepository;
import com.example.marketmayhem.service.LeaderboardService;

@Service
public class MatchingEngineService {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngineService.class);

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LeaderboardService leaderboardService;

    public MatchingEngineService(OrderRepository orderRepository,
                                 TradeRepository tradeRepository,
                                 SimpMessagingTemplate messagingTemplate,
                                 LeaderboardService leaderboardService) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.messagingTemplate = messagingTemplate;
        this.leaderboardService = leaderboardService;
    }

    // Method to clear all order books - useful for testing
    public void clearAllOrderBooks() {
        books.clear();
        log.debug("Cleared all order books");
    }

    /**
     * Persist the order only if it has no id, but keep the original reference.
     * Works with real JPA and Mockito (which may return a different instance or null).
     */
    private Order ensurePersisted(Order o) {
        if (o == null) {
            return o;
        }
        
        if (o.getId() != null) {
            log.debug("Order already persisted: {} with id: {}", o.getClOrdId(), o.getId());
            return o; // already persisted
        }
        
        Order saved = null;
        try {
            log.debug("Persisting order: {}", o.getClOrdId());
            saved = orderRepository.save(o);
            log.debug("Order persisted: {} with id: {}", o.getClOrdId(), saved != null ? saved.getId() : "null");
        } catch (RuntimeException ex) {
            log.warn("orderRepository.save threw; proceeding without swap. clOrdId={}", o.getClOrdId(), ex);
        }
        
        Long id = (saved != null && saved.getId() != null) ? saved.getId() : o.getId();
        if (id != null && o.getId() == null) {
            try {
                o.setId(id);
                log.debug("Set ID {} on order: {}", id, o.getClOrdId());
            } catch (Exception ignored) {
                // Keep original reference even if setter is restricted
            }
        }
        return o;
    }

    @Transactional
    public MatchResult processOrder(Order order, String roomId) {
        log.debug("Processing order: {} for symbol: {} in room: {}", order.getClOrdId(), order.getSymbol(), roomId);
        OrderBook book = books.computeIfAbsent(order.getSymbol(), OrderBook::new);

        List<Trade> trades = new ArrayList<>();
        try {
            if (order.getType() == OrderType.MARKET) {
                trades.addAll(executeMarketOrder(order, book));
            } else {
                trades.addAll(executeLimitOrder(order, book));
            }

            for (Trade trade : trades) {
                processTrade(trade, roomId);
            }

            // Always save the order to ensure it's persisted
            ensurePersisted(order);
            
            // Save any updated orders from trades
            orderRepository.save(order);

            broadcastBookUpdate(book, roomId);

            log.info("Order processing complete: {} - Status: {}, Trades: {}", order.getClOrdId(), order.getStatus(), trades.size());
        } catch (Exception e) {
            log.error("Error processing order: {}", order.getClOrdId(), e);
            order.setStatus(OrderStatus.REJECTED);
            orderRepository.save(order);
            throw e;
        }
        return new MatchResult(trades, order);
    }

    private List<Trade> executeMarketOrder(Order order, OrderBook book) {
        List<Trade> trades = new ArrayList<>();
        log.debug("Executing market order: {} side: {} qty: {}", order.getClOrdId(), order.getSide(), order.getRemainingQty());

        while (order.getRemainingQty() > 0) {
            Order bestCounter = getBestCounterOrder(order, book);
            if (bestCounter == null) {
                log.warn("No liquidity available for market order: {}", order.getClOrdId());
                order.setStatus(OrderStatus.REJECTED);
                break;
            }

            Trade t = executeTrade(order, bestCounter, bestCounter.getPrice());
            if (t != null) {
                trades.add(t);
                if (bestCounter.getRemainingQty() == 0) {
                    book.removeOrder(bestCounter);
                    bestCounter.setStatus(OrderStatus.FILLED);
                    orderRepository.save(bestCounter);
                }
            } else {
                log.error("Failed to execute trade for market order: {}", order.getClOrdId());
                order.setStatus(OrderStatus.REJECTED);
                break;
            }
        }

        if (order.getRemainingQty() == 0) {
            order.setStatus(OrderStatus.FILLED);
        }
        return trades;
    }

    private List<Trade> executeLimitOrder(Order order, OrderBook book) {
        List<Trade> trades = new ArrayList<>();
        log.debug("Executing limit order: {} side: {} qty: {} price: {}", order.getClOrdId(), order.getSide(), order.getRemainingQty(), order.getPrice());

        while (order.getRemainingQty() > 0 && canCross(order, book)) {
            Order bestCounter = getBestCounterOrder(order, book);
            if (bestCounter == null) break;

            Trade t = executeTrade(order, bestCounter, bestCounter.getPrice());
            if (t != null) {
                trades.add(t);
                if (bestCounter.getRemainingQty() == 0) {
                    book.removeOrder(bestCounter);
                    bestCounter.setStatus(OrderStatus.FILLED);
                } else {
                    bestCounter.setStatus(OrderStatus.PARTIAL);
                }
                // Always save the counter order to persist status changes
                orderRepository.save(bestCounter);
            } else {
                break;
            }
        }

        // Handle order persistence based on final state
        if (order.getRemainingQty() > 0) {
            // Order has remaining quantity - add to book and persist
            book.addOrder(order);
            log.debug("Added order to book: {} remaining qty: {}", order.getClOrdId(), order.getRemainingQty());
        } else if (order.getStatus() == OrderStatus.FILLED) {
            // Order was fully filled - ensure it's marked as filled
            log.debug("Order fully filled: {}", order.getClOrdId());
        }
        return trades;
    }

    private boolean canCross(Order order, OrderBook book) {
        if (order.getSide() == Side.BUY) {
            Order bestAsk = book.getBestAsk();
            return bestAsk != null && order.getPrice().compareTo(bestAsk.getPrice()) >= 0;
        } else {
            Order bestBid = book.getBestBid();
            return bestBid != null && order.getPrice().compareTo(bestBid.getPrice()) <= 0;
        }
    }

    private Order getBestCounterOrder(Order order, OrderBook book) {
        return (order.getSide() == Side.BUY) ? book.getBestAsk() : book.getBestBid();
    }

    /**
     * Execute a trade. Persist both sides only to assign IDs, but KEEP the original
     * object references used by the book (see ensurePersisted).
     */
    private Trade executeTrade(Order aggressive, Order passive, BigDecimal price) {
        // Assign IDs without swapping references
        ensurePersisted(aggressive);
        ensurePersisted(passive);

        long tradeQty = Math.min(aggressive.getRemainingQty(), passive.getRemainingQty());
        if (tradeQty <= 0) {
            log.warn("Invalid trade quantity: {} between orders {} and {}", tradeQty, aggressive.getClOrdId(), passive.getClOrdId());
            return null;
        }

        log.debug("Executing trade: {} shares at {} between {} and {}", tradeQty, price, aggressive.getClOrdId(), passive.getClOrdId());

        aggressive.addFill(tradeQty);
        passive.addFill(tradeQty);

        Long buyOrderId = (aggressive.getSide() == Side.BUY) ? aggressive.getId() : passive.getId();
        Long sellOrderId = (aggressive.getSide() == Side.SELL) ? aggressive.getId() : passive.getId();

        Trade trade = new Trade(buyOrderId, sellOrderId, aggressive.getSymbol(), tradeQty, price);
        log.info("Trade executed: {} {} @ {} (Buy: {}, Sell: {})", trade.getSymbol(), trade.getQty(), trade.getPrice(), buyOrderId, sellOrderId);
        return trade;
    }

    private void processTrade(Trade trade, String roomId) {
        // sanity: IDs must exist (we ensured this in executeTrade via ensurePersisted)
        if (trade.getBuyOrderId() == null || trade.getSellOrderId() == null) {
            throw new IllegalArgumentException("Trade must have non-null buy/sell order IDs");
        }

        // Null-safe save: Mockito can return null unless you stub it.
        log.debug("Saving trade: {} {} @ {}", trade.getSymbol(), trade.getQty(), trade.getPrice());
        Trade savedTrade = java.util.Optional.ofNullable(tradeRepository.save(trade)).orElse(trade);
        log.debug("Trade saved with ID: {}", savedTrade.getId());

        // Load orders for P&L and event
        java.util.Optional<Order> buyOrderOpt = orderRepository.findById(savedTrade.getBuyOrderId());
        java.util.Optional<Order> sellOrderOpt = orderRepository.findById(savedTrade.getSellOrderId());

        if (buyOrderOpt.isPresent() && sellOrderOpt.isPresent()) {
            Order buyOrder = buyOrderOpt.get();
            Order sellOrder = sellOrderOpt.get();

            updatePlayerPnL(buyOrder, sellOrder, savedTrade);
            broadcastTradeEvent(buyOrder, sellOrder, savedTrade, roomId);
        } else {
            log.error("Could not find orders for trade: {}", savedTrade.getId());
        }
    }

    private void updatePlayerPnL(Order buyOrder, Order sellOrder, Trade trade) {
        BigDecimal tradeCost = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQty()));
        leaderboardService.updatePlayerPnl(buyOrder.getPlayerId(), tradeCost.negate());
        leaderboardService.updatePlayerPnl(sellOrder.getPlayerId(), tradeCost);
        log.debug("Updated P&L - Buyer: {} (-{}), Seller: {} (+{})", buyOrder.getPlayerId(), tradeCost, sellOrder.getPlayerId(), tradeCost);
    }

    private void broadcastTradeEvent(Order buyOrder, Order sellOrder, Trade trade, String roomId) {
        TradeEvent tradeEvent = new TradeEvent(
                trade.getSymbol(),
                trade.getQty(),
                trade.getPrice(),
                trade.getExecutedAt(),
                buyOrder.getPlayerId(),
                sellOrder.getPlayerId()
        );
        String topic = "/topic/room/" + roomId + "/trades";
        messagingTemplate.convertAndSend(topic, tradeEvent);
        log.debug("Broadcasted trade event to {}: {}", topic, tradeEvent);
    }

    private void broadcastBookUpdate(OrderBook book, String roomId) {
        BookUpdate bookUpdate = book.getSnapshot(10);
        String topic = "/topic/room/" + roomId + "/book/" + book.getSymbol();
        messagingTemplate.convertAndSend(topic, bookUpdate);
        log.debug("Broadcasted book update to {}", topic);
    }

    @Transactional
    public boolean cancelOrder(String clOrdId, String roomId) {
        log.info("Attempting to cancel order: {}", clOrdId);

        Optional<Order> orderOpt = orderRepository.findByClOrdId(clOrdId);
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for cancellation: {}", clOrdId);
            return false;
        }

        Order order = orderOpt.get();

        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL) {
            log.warn("Cannot cancel order in status {}: {}", order.getStatus(), clOrdId);
            return false;
        }

        OrderBook book = books.get(order.getSymbol());
        if (book != null && book.removeOrder(order)) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            broadcastBookUpdate(book, roomId);
            log.info("Order cancelled successfully: {}", clOrdId);
            return true;
        }

        log.warn("Failed to remove order from book: {}", clOrdId);
        return false;
    }

    public BookUpdate getBookSnapshot(String symbol, int levels) {
        OrderBook book = books.get(symbol);
        return book != null
                ? book.getSnapshot(levels)
                : new BookUpdate(symbol, Collections.emptyList(), Collections.emptyList(), Instant.now());
    }

    public Map<String, Object> getBookStatistics(String symbol) {
        OrderBook book = books.get(symbol);
        if (book == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("symbol", symbol);
        stats.put("orderCount", book.getOrderCount());
        stats.put("isEmpty", book.isEmpty());
        stats.put("bestBidPrice", book.getBestPrice(Side.BUY));
        stats.put("bestAskPrice", book.getBestPrice(Side.SELL));
        stats.put("spread", book.getSpread());
        stats.put("midPrice", book.getMidPrice());
        return stats;
    }

    public void clearAllBooks() {
        log.warn("Clearing all order books");
        books.values().forEach(OrderBook::clear);
        books.clear();
    }

    public static class MatchResult {
        private final List<Trade> trades;
        private final Order order;

        public MatchResult(List<Trade> trades, Order order) {
            this.trades = Collections.unmodifiableList(trades);
            this.order = order;
        }

        public List<Trade> getTrades() { return trades; }
        public Order getOrder() { return order; }
        public boolean hasTrades() { return !trades.isEmpty(); }
        public int getTradeCount() { return trades.size(); }

        @Override
        public String toString() {
            return String.format("MatchResult[order=%s, trades=%d, status=%s]",
                    order.getClOrdId(), trades.size(), order.getStatus());
        }
    }
}
