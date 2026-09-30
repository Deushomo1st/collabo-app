package com.collabo.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The login cookie. Spring Session ignores server.servlet.session.cookie.*, so it is set here:
 * HttpOnly (page scripts can't read it), SameSite=Lax, and Secure once the site is on HTTPS.
 */
@Configuration
public class SessionConfig {

    @Bean
    public CookieSerializer cookieSerializer(@Value("${app.session.cookie-secure:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("COLLABO_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }
}
