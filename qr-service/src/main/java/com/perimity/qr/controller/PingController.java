// ============================================================
// PERIMITY - Day 1 ping endpoint. Every service needs one.
//
// PUT IT HERE:
//   src/main/java/com/perimity/<service>/controller/PingController.java
//
// CHANGE THESE 4 THINGS to match your service:
//
//   |        | package               | @RequestMapping     | "service" value  | port |
//   |--------|-----------------------|---------------------|------------------|------|
//   | Omkar  | com.perimity.auth     | /api/auth           | auth-service     | 8081 |
//   | Mukul  | com.perimity.user     | /api/users          | user-service     | 8082 |
//   | Tushar | com.perimity.gatepass | /api/gatepass       | gatepass-service | 8083 |
//   | Arham  | com.perimity.campus   | /api/campus         | campus-service   | 8084 |
//   | Palash | com.perimity.guard    | /api/guard          | guard-service    | 8085 |
//   | Sanjay | com.perimity.qr       | /api/qr             | qr-service       | 8086 |
//
// (Both import lines and the ApiResponse return type also need the
//  package changed - just search the file for "<service>".)
//
// TEST IT:
//   Run the app, open http://localhost:<your-port>/swagger-ui.html
//   You should see a "Health" section with GET /api/<service>/ping
//   Click Try it out -> Execute. Expected response:
//     { "success": true, "message": "OK",
//       "data": { "status": "ok", "service": "<service>-service" },
//       "errors": null }
//
// NOTE: @Operation is required on EVERY endpoint you ever write.
// It is what makes the Swagger page readable instead of a list of
// bare paths. An endpoint without it is not "done".
// ============================================================

package com.perimity.qr.controller;

import com.perimity.qr.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/<service>")
@Tag(name = "Health", description = "Service health and readiness")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Check that the service is running")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of(
                "status", "ok",
                "service", "<service>-service"
        ));
    }
}
