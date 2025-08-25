package com.example.marketmayhem.repo;

import com.example.marketmayhem.model.RiskViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskViolationRepository extends JpaRepository<RiskViolation, Long> {
    List<RiskViolation> findByPlayerId(String playerId);
    long countByPlayerId(String playerId);
}