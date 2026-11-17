package com.stockinsight;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StockInsightApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockInsightApplication.class, args);
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
