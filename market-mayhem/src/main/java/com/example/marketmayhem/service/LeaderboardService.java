package com.example.marketmayhem.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketmayhem.dto.LeaderboardEntry;
import com.example.marketmayhem.dto.LeaderboardResponse;
import com.example.marketmayhem.dto.ScoreUpdate;
import com.example.marketmayhem.model.PlayerScore;
import com.example.marketmayhem.repo.PlayerScoreRepository;

@Service
public class LeaderboardService {
    
    private final PlayerScoreRepository playerScoreRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    public LeaderboardService(PlayerScoreRepository playerScoreRepository,
                             SimpMessagingTemplate messagingTemplate) {
        this.playerScoreRepository = playerScoreRepository;
        this.messagingTemplate = messagingTemplate;
    }
    
    @Transactional
    public void updatePlayerPnl(String playerId, BigDecimal pnlDelta) {
        PlayerScore score = playerScoreRepository.findById(playerId)
            .orElse(new PlayerScore(playerId));
        
        score.addPnl(pnlDelta);
        playerScoreRepository.save(score);
        
        // Broadcast score update
        ScoreUpdate update = new ScoreUpdate(
            playerId, 
            score.getPnl(), 
            score.getViolations(), 
            score.getMatches()
        );
        messagingTemplate.convertAndSend("/topic/scores", update);
    }
    
    @Transactional
    public void incrementViolations(String playerId) {
        PlayerScore score = playerScoreRepository.findById(playerId)
            .orElse(new PlayerScore(playerId));
        
        score.incrementViolations();
        playerScoreRepository.save(score);
        
        // Broadcast score update
        ScoreUpdate update = new ScoreUpdate(
            playerId, 
            score.getPnl(), 
            score.getViolations(), 
            score.getMatches()
        );
        messagingTemplate.convertAndSend("/topic/scores", update);
    }
    
    @Transactional
    public void incrementMatches(String playerId) {
        PlayerScore score = playerScoreRepository.findById(playerId)
            .orElse(new PlayerScore(playerId));
        
        score.incrementMatches();
        playerScoreRepository.save(score);
    }
    
    public LeaderboardResponse getLeaderboard() {
        List<PlayerScore> topPlayers = playerScoreRepository.findTopPlayers();
        
        List<LeaderboardEntry> entries = topPlayers.stream()
            .map(score -> new LeaderboardEntry(
                score.getPlayerId(),
                score.getPnl(),
                score.getViolations(),
                score.getMatches()
            ))
            .collect(Collectors.toList());
            
        return new LeaderboardResponse(entries, Instant.now());
    }
}