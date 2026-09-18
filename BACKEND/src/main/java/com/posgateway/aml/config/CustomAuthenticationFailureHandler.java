package com.posgateway.aml.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String errorMessage;

        if (exception instanceof BadCredentialsException) {
            errorMessage = "bad_credentials";
        } else if (exception instanceof DisabledException) {
            errorMessage = "disabled";
        } else if (exception instanceof LockedException) {
            errorMessage = "locked";
        } else if (exception instanceof CredentialsExpiredException) {
            errorMessage = "expired";
        } else if (exception.getCause() instanceof UsernameNotFoundException ||
                exception.getMessage().contains("User not found")) {
            errorMessage = "user_not_found";
        } else if (exception.getCause() instanceof java.sql.SQLException ||
                exception.getMessage().contains("JDBC") ||
                exception.getMessage().contains("Connection")) {
            errorMessage = "system_error";
        } else {
            // Generic error with sanitized message
            errorMessage = "error";
        }

        // Log the actual error for debugging
        System.err.println("Authentication failed: " + exception.getClass().getSimpleName() +
                " - " + exception.getMessage());

        setDefaultFailureUrl("/login.html?error=" + errorMessage);
        super.onAuthenticationFailure(request, response, exception);
    }
}
