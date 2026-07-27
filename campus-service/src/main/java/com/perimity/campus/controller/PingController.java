package com.perimity.campus.controller;

import com.perimity.campus.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campus")
@Tag(name = "Health", description = "Service health and readiness")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Check that campus-service is running")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of(
                "status", "ok",
                "service", "campus-service"
        ));
    }
}
