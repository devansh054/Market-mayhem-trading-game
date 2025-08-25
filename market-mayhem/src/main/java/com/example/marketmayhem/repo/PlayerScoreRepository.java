package com.example.marketmayhem.repo;

import com.example.marketmayhem.model.PlayerScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerScoreRepository extends JpaRepository<PlayerScore, String> {
    List<PlayerScore> findAllByOrderByPnlDescViolationsAsc();
    
    @Query("SELECT p FROM PlayerScore p ORDER BY p.pnl DESC, p.violations ASC")
    List<PlayerScore> findTopPlayers();
}