package com.example.marketmayhem.service;

import com.example.marketmayhem.dto.ErrorMessage;
import com.example.marketmayhem.dto.PlaceOrderMessage;
import com.example.marketmayhem.engine.MatchingEngineService;
import com.example.marketmayhem.model.Order;
import com.example.marketmayhem.model.OrderStatus;
import com.example.marketmayhem.model.RiskViolation;
import com.example.marketmayhem.repo.OrderRepository;
import com.example.marketmayhem.repo.RiskViolationRepository;
import com.example.marketmayhem.risk.RiskRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {
    
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final RiskViolationRepository riskViolationRepository;
    private final MatchingEngineService matchingEngine;
    private final LeaderboardService leaderboardService;
    private final SimpMessagingTemplate messagingTemplate;
    private final List<RiskRule> riskRules;
    
    public OrderService(OrderRepository orderRepository,
                       RiskViolationRepository riskViolationRepository,
                       MatchingEngineService matchingEngine,
                       LeaderboardService leaderboardService,
                       SimpMessagingTemplate messagingTemplate,
                       List<RiskRule> riskRules) {
        this.orderRepository = orderRepository;
        this.riskViolationRepository = riskViolationRepository;
        this.matchingEngine = matchingEngine;
        this.leaderboardService = leaderboardService;
        this.messagingTemplate = messagingTemplate;
        this.riskRules = riskRules;
    }
    
    @Transactional
    public void placeOrder(PlaceOrderMessage orderMsg, String roomId) {
        log.info("Placing order: {} for player: {}", orderMsg.clOrdId(), orderMsg.player());
        
        // Check for duplicate order ID
        if (orderRepository.findByClOrdId(orderMsg.clOrdId()).isPresent()) {
            sendError(roomId, orderMsg.player(), "DUPLICATE_ORDER", 
                     "Order ID already exists: " + orderMsg.clOrdId());
            return;
        }
        
        // Validate order against risk rules
        for (RiskRule rule : riskRules) {
            Optional<RiskRule.RiskViolation> violation = rule.validate(orderMsg);
            if (violation.isPresent()) {
                handleRiskViolation(orderMsg, violation.get(), roomId);
                return;
            }
        }
        
        // Create and save order
        Order order = new Order(
            orderMsg.player(),
            orderMsg.clOrdId(),
            orderMsg.symbol(),
            orderMsg.side(),
            orderMsg.qty(),
            orderMsg.price(),
            orderMsg.type()
        );
        
        order = orderRepository.save(order);
        
        // Process through matching engine
        try {
            matchingEngine.processOrder(order, roomId);
            log.info("Order processed successfully: {}", orderMsg.clOrdId());
        } catch (Exception e) {
            log.error("Error processing order: {}", orderMsg.clOrdId(), e);
            order.setStatus(OrderStatus.REJECTED);
            orderRepository.save(order);
            sendError(roomId, orderMsg.player(), "PROCESSING_ERROR", 
                     "Failed to process order: " + e.getMessage());
        }
    }
    
    @Transactional
    public boolean cancelOrder(String clOrdId, String playerId, String roomId) {
        log.info("Canceling order: {} for player: {}", clOrdId, playerId);
        
        Optional<Order> orderOpt = orderRepository.findByClOrdId(clOrdId);
        if (orderOpt.isEmpty()) {
            sendError(roomId, playerId, "ORDER_NOT_FOUND", "Order not found: " + clOrdId);
            return false;
        }
        
        Order order = orderOpt.get();
        if (!order.getPlayerId().equals(playerId)) {
            sendError(roomId, playerId, "UNAUTHORIZED", "Not authorized to cancel this order");
            return false;
        }
        
        boolean cancelled = matchingEngine.cancelOrder(clOrdId, roomId);
        if (!cancelled) {
            sendError(roomId, playerId, "CANCEL_FAILED", "Unable to cancel order: " + clOrdId);
        }
        
        return cancelled;
    }
    
    private void handleRiskViolation(PlaceOrderMessage orderMsg, RiskRule.RiskViolation violation, String roomId) {
        log.warn("Risk violation for order {}: {}", orderMsg.clOrdId(), violation.detail());
        
        // Save risk violation
        RiskViolation riskViolation = new RiskViolation(
            orderMsg.player(),
            violation.type(),
            violation.detail(),
            orderMsg.clOrdId()
        );
        riskViolationRepository.save(riskViolation);
        
        // Update player score
        leaderboardService.incrementViolations(orderMsg.player());
        
        // Send error to client
        sendError(roomId, orderMsg.player(), "RISK_VIOLATION", violation.detail());
    }
    
    private void sendError(String roomId, String playerId, String code, String message) {
        ErrorMessage error = new ErrorMessage(code, message, null);
        messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", error);
    }
}