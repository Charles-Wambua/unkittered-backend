package com.unkittered.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // trip/meetup reminder sweep
@EnableAsync        // push sends run off the request/event thread
public class UnkitteredApplication {
    public static void main(String[] args) {
        SpringApplication.run(UnkitteredApplication.class, args);
    }
}
