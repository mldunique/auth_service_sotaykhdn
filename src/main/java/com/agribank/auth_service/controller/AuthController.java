package com.agribank.auth_service.controller;

import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.LoginResponse;
import com.agribank.auth_service.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Controller handling HTML form-based authentication (Login and Logout).
 */
@Controller
public class AuthController {

    private final AuthService authService;
    private final String cookieName;
    private final long expirationMs;
    private final String redirectUrl;
    private final boolean cookieSecure;
    private final String cookieDomain;

    private final org.springframework.security.web.savedrequest.RequestCache requestCache = 
            new org.springframework.security.web.savedrequest.HttpSessionRequestCache();

    public AuthController(AuthService authService,
                          @Value("${app.security.jwt.cookie-name:accessToken}") String cookieName,
                          @Value("${app.security.jwt.expiration-ms:3600000}") long expirationMs,
                          @Value("${app.security.jwt.redirect-url:http://localhost:5173/}") String redirectUrl,
                          @Value("${app.security.jwt.cookie-secure:false}") boolean cookieSecure,
                          @Value("${app.security.jwt.cookie-domain:localhost}") String cookieDomain) {
        this.authService = authService;
        this.cookieName = cookieName;
        this.expirationMs = expirationMs;
        this.redirectUrl = redirectUrl;
        this.cookieSecure = cookieSecure;
        this.cookieDomain = cookieDomain;
    }

    /**
     * Authenticates username and password. On success, generates a JWT
     * and sets it in an HttpOnly cookie, then redirects to the target page or frontend.
     */
    @PostMapping("/login")
    public String login(@Valid LoginRequest request,
                        BindingResult bindingResult,
                        HttpServletRequest httpRequest,
                        HttpServletResponse response,
                        Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "login";
        }

        LoginResponse loginResponse = authService.login(request);

        if (loginResponse.isSuccess()) {
            ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(cookieName, loginResponse.getToken())
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .path("/")
                    .maxAge(expirationMs / 1000)
                    .sameSite("Lax");
            if (cookieDomain != null && !cookieDomain.isBlank() && !"localhost".equalsIgnoreCase(cookieDomain)) {
                cookieBuilder.domain(cookieDomain);
            }
            ResponseCookie cookie = cookieBuilder.build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            // Determine redirect target URL: Check redirect_uri parameter first, then SavedRequest, then fallback
            String targetUrl = redirectUrl;
            String sessionRedirectUri = (String) httpRequest.getSession().getAttribute("redirect_uri");
            
            if (isValidRedirectUri(sessionRedirectUri)) {
                targetUrl = sessionRedirectUri;
                httpRequest.getSession().removeAttribute("redirect_uri");
            } else {
                org.springframework.security.web.savedrequest.SavedRequest savedRequest = 
                        requestCache.getRequest(httpRequest, response);
                if (savedRequest != null) {
                    targetUrl = savedRequest.getRedirectUrl();
                }
            }

            return "redirect:" + targetUrl;
        }

        model.addAttribute("error", loginResponse.getMessage());
        return "login";
    }

    private boolean isValidRedirectUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        try {
            java.net.URI redirect = new java.net.URI(uri);
            String host = redirect.getHost();
            if (host == null) {
                return false;
            }
            // Allow localhost and any agribank.com.vn subdomain to prevent Open Redirect attacks
            return host.equals("localhost") || host.endsWith("agribank.com.vn");
        } catch (Exception e) {
            return false;
        }
    }
}