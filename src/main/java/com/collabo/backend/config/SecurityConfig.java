package com.collabo.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public AdminKeyFilter adminKeyFilter(@Value("${app.admin.key:}") String adminKey) {
        return new AdminKeyFilter(adminKey);
    }

    /**
     * Prevents the servlet container from ALSO registering AdminKeyFilter in its
     * own filter chain — it must run only inside the Spring Security chain.
     */
    @Bean
    public FilterRegistrationBean<AdminKeyFilter> adminKeyFilterRegistration(AdminKeyFilter adminKeyFilter) {
        FilterRegistrationBean<AdminKeyFilter> registration = new FilterRegistrationBean<>(adminKeyFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AdminKeyFilter adminKeyFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // /api/admin/** is gated solely by AdminKeyFilter (X-Admin-Key header);
                // "authenticated" would 403 since no auth mechanism exists yet.
                .addFilterBefore(adminKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Tightened: only the registration POST is public, not every method on /api/users
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers("/api/admin/**").permitAll()
                        // Allow public access to the registration/admin pages and static assets
                        .requestMatchers("/", "/index.html", "/register.html", "/admin.html", "/**/*.css", "/**/*.js").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // This is the industry standard for hashing
    }
}
