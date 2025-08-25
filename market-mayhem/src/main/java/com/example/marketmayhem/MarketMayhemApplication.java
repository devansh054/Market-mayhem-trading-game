package com.example.marketmayhem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketMayhemApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketMayhemApplication.class, args);
    }
}