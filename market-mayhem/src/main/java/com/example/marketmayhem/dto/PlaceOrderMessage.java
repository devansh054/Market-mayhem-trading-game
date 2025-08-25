package com.example.marketmayhem.dto;

import com.example.marketmayhem.model.Side;
import com.example.marketmayhem.model.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PlaceOrderMessage(
    @NotBlank String player,
    @NotBlank String clOrdId,
    @NotBlank String symbol,
    @NotNull Side side,
    @Positive Long qty,
    @NotNull OrderType type,
    BigDecimal price
) {}