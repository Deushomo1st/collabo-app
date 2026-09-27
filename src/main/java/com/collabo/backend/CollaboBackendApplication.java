package com.collabo.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CollaboBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollaboBackendApplication.class, args);
    }

}
