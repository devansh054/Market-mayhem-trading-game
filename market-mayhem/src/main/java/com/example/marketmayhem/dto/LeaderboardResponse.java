package com.example.marketmayhem.dto;

import java.time.Instant;
import java.util.List;

public record LeaderboardResponse(
    List<LeaderboardEntry> players,
    Instant timestamp
) {}