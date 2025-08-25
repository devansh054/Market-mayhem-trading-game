package com.example.marketmayhem.dto;

import java.math.BigDecimal;

public record LeaderboardEntry(
    String playerId,
    BigDecimal pnl,
    Integer violations,
    Integer matches
) {}