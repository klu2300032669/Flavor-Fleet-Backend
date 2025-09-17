package com.flavorfleet.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    // No passwordEncoder bean here to avoid BeanDefinitionOverrideException
}
