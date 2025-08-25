package com.example.marketmayhem.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketTick(
    String symbol,
    BigDecimal bid,
    BigDecimal ask,
    BigDecimal last,
    Instant timestamp
) {}