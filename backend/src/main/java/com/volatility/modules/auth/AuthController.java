package com.volatility.modules.auth;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import com.volatility.modules.auth.dto.LoginRequest;
import com.volatility.modules.auth.dto.LoginResponse;
import com.volatility.modules.auth.dto.RegisterRequest;
import com.volatility.modules.auth.dto.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        LoginResponse response = authService.login(request.username(), request.password());
        return ApiResponse.success(response, requestId(servletRequest));
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        LoginResponse response = authService.register(request.username(), request.password(), request.nickname());
        return ApiResponse.success(response, requestId(servletRequest));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfile> me(HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.currentUser(), requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
