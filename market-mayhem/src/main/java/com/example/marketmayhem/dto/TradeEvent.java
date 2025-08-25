package com.example.marketmayhem.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeEvent(
    String symbol,
    Long qty,
    BigDecimal price,
    Instant executedAt,
    String buyPlayer,
    String sellPlayer
) {}