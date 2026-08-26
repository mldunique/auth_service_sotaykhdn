package com.agribank.auth_service.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for rendering the login view.
 */
// @Controller (Merged into AuthController)
public class LoginController {

    private final String redirectUrl;

    public LoginController(@Value("${app.security.jwt.redirect-url:http://localhost:5173/}") String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            jakarta.servlet.http.HttpServletRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // If a redirectUri is specified, save it in the session
        if (redirectUri != null && !redirectUri.isBlank()) {
            request.getSession().setAttribute("redirect_uri", redirectUri);
        }

        // If user is already logged in, redirect to the target URL or configured frontend page
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String savedRedirect = (String) request.getSession().getAttribute("redirect_uri");
            if (savedRedirect != null && !savedRedirect.isBlank()) {
                request.getSession().removeAttribute("redirect_uri");
                return "redirect:" + savedRedirect;
            }
            return "redirect:" + redirectUrl;
        }

        return "login";
    }
}