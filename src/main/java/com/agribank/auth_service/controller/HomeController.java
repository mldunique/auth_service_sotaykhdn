package com.agribank.auth_service.controller;

import com.agribank.auth_service.dto.response.UserInfo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for rendering the authenticated home page view.
 */
@Controller
public class HomeController {

    /**
     * Renders the home screen.
     * Uses @AuthenticationPrincipal to automatically inject the authenticated UserInfo principal.
     */
    @GetMapping("/home")
    public String home(@AuthenticationPrincipal UserInfo userInfo, Model model) {
        model.addAttribute("user", userInfo);
        return "home";
    }
}