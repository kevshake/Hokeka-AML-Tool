package com.posgateway.aml.controller.auth;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * CSRF Token Controller
 * Provides CSRF token for frontend
 */
@RestController
@RequestMapping("/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public Map<String, Object> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        Map<String, Object> response = new HashMap<>();
        
        if (csrfToken != null) {
            response.put("token", csrfToken.getToken());
            response.put("headerName", csrfToken.getHeaderName());
            response.put("parameterName", csrfToken.getParameterName());
        } else {
            response.put("token", null);
            response.put("message", "CSRF token not available");
        }
        
        return response;
    }
}

