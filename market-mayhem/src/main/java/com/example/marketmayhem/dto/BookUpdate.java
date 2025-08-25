package com.example.marketmayhem.dto;

import java.time.Instant;
import java.util.List;

public record BookUpdate(
    String symbol,
    List<BookLevel> bids,
    List<BookLevel> asks,
    Instant timestamp
) {}