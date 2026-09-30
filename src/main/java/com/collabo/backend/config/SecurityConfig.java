package com.collabo.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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

    /** Same reason as above: RateLimitFilter must run only inside the Spring Security chain, after the session is loaded. */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AdminKeyFilter adminKeyFilter, RateLimitFilter rateLimitFilter) throws Exception {
        http
                // Cookie login means CSRF protection is needed: the token comes back in an XSRF-TOKEN cookie
                // and js/services/api.js sends it as X-XSRF-TOKEN. /api/admin/** uses a header key, not a cookie.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/admin/**"))
                // Picks up the CorsConfigurationSource bean from CorsConfig
                .cors(Customizer.withDefaults())
                // /api/admin/** is gated solely by AdminKeyFilter (X-Admin-Key header);
                // "authenticated" would 403 since no auth mechanism exists yet.
                .addFilterBefore(adminKeyFilter, UsernamePasswordAuthenticationFilter.class)
                // Token-bucket throttling of /api, ahead of the admin key check so wrong-key guesses are throttled too
                .addFilterBefore(rateLimitFilter, AdminKeyFilter.class)
                // Signed-out API calls answer 401 (not 403) so the frontend knows to show the login page
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Tightened: only the registration POST is public, not every method on /api/users
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/verify", "/api/users/resend-otp").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()   // login/logout/me decide for themselves
                        .requestMatchers("/api/admin/**").permitAll()
                        .requestMatchers("/api/moderator/login", "/api/moderator/logout").permitAll()   // the rest of /api/moderator needs a session
                        // Allow public access to the registration/admin pages and static assets
                        .requestMatchers("/", "/index.html", "/admin.html", "/HTML-pages/**", "/**/*.css", "/**/*.js", "/**/*.html").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // This is the industry standard for hashing
    }
}
