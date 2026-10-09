package com.onboarding.clientes.config;

import org.springframework.context.annotation.Bean;
import java.time.Clock;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelojConfig {
    @Bean
    public Clock clock(){
        return Clock.systemUTC();
    }
}
