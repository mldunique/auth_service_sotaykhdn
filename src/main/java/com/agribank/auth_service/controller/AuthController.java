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

    @GetMapping("/login")
    public String showLoginForm(@org.springframework.web.bind.annotation.RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                HttpServletRequest request,
                                Model model) {
        if (isValidRedirectUri(redirectUri)) {
            String sanitized = sanitizeRedirectUri(redirectUri);
            request.getSession().setAttribute("redirect_uri", sanitized);
            model.addAttribute("redirect_uri", sanitized);
        } else {
            model.addAttribute("redirect_uri", redirectUrl);
        }

        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            String targetUrl = redirectUrl;
            String sessionRedirect = (String) request.getSession().getAttribute("redirect_uri");
            if (isValidRedirectUri(sessionRedirect)) {
                targetUrl = sanitizeRedirectUri(sessionRedirect);
            }
            return "redirect:" + targetUrl;
        }

        return "login";
    }

    /**
     * Authenticates username and password. On success, generates a JWT
     * and sets it in an HttpOnly cookie, then redirects to the target page or frontend.
     */
    @PostMapping("/login")
    public String login(@Valid LoginRequest request,
                        BindingResult bindingResult,
                        @org.springframework.web.bind.annotation.RequestParam(name = "redirect_uri", required = false) String paramRedirectUri,
                        HttpServletRequest httpRequest,
                        HttpServletResponse response,
                        Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            model.addAttribute("redirect_uri", paramRedirectUri);
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

            // Determine redirect target URL: Check parameter first, then session, then fallback
            String targetUrl = redirectUrl;
            if (isValidRedirectUri(paramRedirectUri)) {
                targetUrl = sanitizeRedirectUri(paramRedirectUri);
            } else {
                String sessionRedirectUri = (String) httpRequest.getSession().getAttribute("redirect_uri");
                if (isValidRedirectUri(sessionRedirectUri)) {
                    targetUrl = sanitizeRedirectUri(sessionRedirectUri);
                    httpRequest.getSession().removeAttribute("redirect_uri");
                } else {
                    org.springframework.security.web.savedrequest.SavedRequest savedRequest = 
                            requestCache.getRequest(httpRequest, response);
                    if (savedRequest != null) {
                        targetUrl = savedRequest.getRedirectUrl();
                    }
                }
            }

            // Append token to targetUrl query parameter for cross-origin/cross-port support
            if (targetUrl.contains("?")) {
                targetUrl += "&token=" + loginResponse.getToken();
            } else {
                targetUrl += "?token=" + loginResponse.getToken();
            }

            return "redirect:" + targetUrl;
        }

        model.addAttribute("error", loginResponse.getMessage());
        model.addAttribute("redirect_uri", paramRedirectUri);
        return "login";
    }

    private String sanitizeRedirectUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return redirectUrl;
        }
        return uri;
    }

    private String extractHostAndPort(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return null;
        }
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
            return host.equals("localhost") || host.endsWith("agribank.com.vn") || host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
        } catch (Exception e) {
            return false;
        }
    }
}