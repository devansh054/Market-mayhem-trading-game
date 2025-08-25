package com.example.marketmayhem.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.marketmayhem.dto.MarketTick;

@Service
public class MarketDataService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, MarketData> currentPrices = new ConcurrentHashMap<>();
    
    @Value("${game.symbols:#{{'AAPL','MSFT'}}}")
    private List<String> symbols;
    
    public MarketDataService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        initializePrices();
    }
    
    private void initializePrices() {
        // Initialize with some base prices
        currentPrices.put("AAPL", new MarketData("AAPL", 
            BigDecimal.valueOf(189.50), BigDecimal.valueOf(189.52), BigDecimal.valueOf(189.51)));
        currentPrices.put("MSFT", new MarketData("MSFT", 
            BigDecimal.valueOf(378.20), BigDecimal.valueOf(378.25), BigDecimal.valueOf(378.22)));
    }
    
    @Scheduled(fixedDelayString = "${game.market-data.tick-interval-ms:250}")
    public void generateMarketTicks() {
        for (String symbol : symbols) {
            MarketData data = currentPrices.get(symbol);
            if (data != null) {
                updateMarketData(data);
                
                MarketTick tick = new MarketTick(
                    symbol,
                    data.bid,
                    data.ask,
                    data.last,
                    Instant.now()
                );
                
                // Broadcast to all rooms - in a real implementation, 
                // you'd track which rooms are active
                messagingTemplate.convertAndSend("/topic/room/1/ticks", tick);
            }
        }
    }
    
    private void updateMarketData(MarketData data) {
        // Simple random walk model for market data
        double change = (random.nextGaussian() * 0.005); // 0.5% volatility
        
        BigDecimal priceDelta = data.last.multiply(BigDecimal.valueOf(change));
        data.last = data.last.add(priceDelta).setScale(2, RoundingMode.HALF_UP);
        
        // Keep bid/ask spread around 0.02
        BigDecimal spread = BigDecimal.valueOf(0.01);
        data.bid = data.last.subtract(spread);
        data.ask = data.last.add(spread);
        
        // Ensure prices don't go negative
        if (data.bid.compareTo(BigDecimal.ZERO) < 0) {
            data.bid = BigDecimal.valueOf(0.01);
            data.ask = BigDecimal.valueOf(0.03);
            data.last = BigDecimal.valueOf(0.02);
        }
    }
    
    public MarketTick getCurrentTick(String symbol) {
        MarketData data = currentPrices.get(symbol);
        if (data != null) {
            return new MarketTick(symbol, data.bid, data.ask, data.last, Instant.now());
        }
        return null;
    }
    
    private static class MarketData {
        String symbol;
        BigDecimal bid;
        BigDecimal ask;
        BigDecimal last;
        
        MarketData(String symbol, BigDecimal bid, BigDecimal ask, BigDecimal last) {
            this.symbol = symbol;
            this.bid = bid;
            this.ask = ask;
            this.last = last;
        }
    }
}