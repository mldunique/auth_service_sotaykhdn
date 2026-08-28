package com.agribank.auth_service.security;

import com.agribank.auth_service.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Main Spring Security configurations for Spring Boot 3.5.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieDomain;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${app.security.jwt.cookie-name:accessToken}") String cookieName,
                          @Value("${app.security.jwt.cookie-secure:false}") boolean cookieSecure,
                          @Value("${app.security.jwt.cookie-domain:localhost}") String cookieDomain) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieDomain = cookieDomain;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS and disable CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                
                // Clickjacking mitigation: Block rendering inside iframes
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))
                
                // Allow cookie-based stateless sessions but let Spring security support request caching
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Disable default form login and basic authentication
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                
                // Configure authorized requests
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/health",
                                "/login",
                                "/logout",
                                "/css/**",
                                "/js/**",
                                "/images/**",

                                "/**/*.png",
                                "/**/*.jpg",
                                "/**/*.jpeg",
                                "/**/*.gif",
                                "/**/*.svg",
                                "/**/*.ico",
                                "/api/v1/branches/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                
                // Configure Spring Security Standard Logout to clear HttpOnly cookie
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(cookieName, "")
                                    .httpOnly(true)
                                    .secure(cookieSecure)
                                    .path("/")
                                    .maxAge(0)
                                    .sameSite("Lax");
                            if (cookieDomain != null && !cookieDomain.isBlank() && !"localhost".equalsIgnoreCase(cookieDomain)) {
                                cookieBuilder.domain(cookieDomain);
                            }
                            ResponseCookie cookie = cookieBuilder.build();
                            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
                            
                            String hostHeader = request.getHeader("Host");
                            String scheme = request.getScheme();
                            String targetUrl;
                            if (hostHeader != null && !hostHeader.isBlank()) {
                                targetUrl = scheme + "://" + hostHeader + request.getContextPath() + "/login";
                            } else {
                                targetUrl = request.getContextPath() + "/login";
                            }

                            String redirectUri = request.getParameter("redirect_uri");
                            if (redirectUri != null && !redirectUri.isBlank()) {
                                targetUrl += (targetUrl.contains("?") ? "&" : "?") + "redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8);
                            }
                            response.sendRedirect(targetUrl);
                        })
                )
                
                // Add JWT filter before the UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                
                // Handle unauthorized requests based on Accept headers or URI
                .exceptionHandling(exception -> exception.authenticationEntryPoint(customAuthenticationEntryPoint()));

        return http.build();
    }

    /**
     * Custom Entry Point to handle authentication failure gracefully.
     * Redirects browser requests to /login, while returning 401 for REST/API requests.
     */
    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            String acceptHeader = request.getHeader("Accept");
            String requestURI = request.getRequestURI();

            if ((acceptHeader != null && acceptHeader.contains("application/json")) 
                    || requestURI.startsWith("/api/")) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + authException.getMessage() + "\"}");
            } else {
                String hostHeader = request.getHeader("Host");
                String scheme = request.getScheme();
                String targetUrl;
                if (hostHeader != null && !hostHeader.isBlank()) {
                    targetUrl = scheme + "://" + hostHeader + request.getContextPath() + "/login";
                } else {
                    targetUrl = request.getContextPath() + "/login";
                }
                response.sendRedirect(targetUrl);
            }
        };
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        // Cho phép origin linh hoạt bao gồm cả Localhost lẫn UAT (10.0.175.10)
        configuration.setAllowedOriginPatterns(java.util.List.of("*"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setExposedHeaders(java.util.List.of("Set-Cookie", "Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}