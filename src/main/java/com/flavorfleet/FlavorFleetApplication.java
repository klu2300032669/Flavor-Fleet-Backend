package com.flavorfleet;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlavorFleetApplication {

    public static void main(String[] args) {
        // Load environment variables from .env file
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing() // Ignores if .env file is missing
                .load();

        // Set system properties for Spring to use
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        // Log loaded environment variables (for debugging, remove in production)
        System.out.println("Loaded EMAIL_FROM: " + System.getenv("EMAIL_FROM"));
        System.out.println("Loaded EMAIL_USERNAME: " + System.getenv("EMAIL_USERNAME"));
        System.out.println("Loaded JWT_SECRET: " + System.getenv("JWT_SECRET"));

        SpringApplication.run(FlavorFleetApplication.class, args);
    }
}