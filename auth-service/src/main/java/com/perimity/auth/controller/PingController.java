package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Health", description = "Service health and readiness")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Check that auth-service is running")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of(
                "status", "ok",
                "service", "auth-service"
        ));
    }
}
