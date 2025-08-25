package com.example.marketmayhem.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderMessage(
    @NotBlank String player,
    @NotBlank String clOrdId
) {}