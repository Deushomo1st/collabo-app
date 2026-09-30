package com.collabo.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS rules for allowing frontend requests.
 * Pages are served same-origin by Spring, so this is mostly dormant today —
 * it exists so a future decoupled frontend (different origin) has exactly
 * one place to be allowed. SecurityConfig picks this bean up via
 * .cors(Customizer.withDefaults()).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://collaboapp.pro",
                "http://localhost:8080"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        // X-Admin-Key must be listed or browsers block the admin panel's preflight
        config.setAllowedHeaders(List.of("Content-Type", "X-Admin-Key", "X-XSRF-TOKEN"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
