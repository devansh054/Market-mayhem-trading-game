package com.example.marketmayhem.dto;

import java.math.BigDecimal;

public record ScoreUpdate(
    String playerId,
    BigDecimal pnl,
    Integer violations,
    Integer matches
) {}