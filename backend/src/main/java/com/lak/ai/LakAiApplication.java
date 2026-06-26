package com.lak.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LakAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LakAiApplication.class, args);
    }

}
