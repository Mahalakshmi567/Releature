package com.example.flags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlagApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlagApplication.class, args);
    }
}
