package com.perimity.user;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        // Must be Asia/Kolkata, not Asia/Calcutta - Postgres 16 rejects the alias.
        // Set before Spring starts, or JPA timestamps are written in UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
