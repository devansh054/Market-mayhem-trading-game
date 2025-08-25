package com.example.marketmayhem.dto;

public record ErrorMessage(
    String code,
    String message,
    String details
) {}