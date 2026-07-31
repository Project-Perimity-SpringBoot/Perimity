package com.perimity.guard;

import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.ScanSession;
import com.perimity.guard.document.enums.PassType;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.document.enums.SessionState;
import com.perimity.guard.repository.EntryLogRepository;
import com.perimity.guard.repository.ScanSessionRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TimeZone;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
// Scans for @FeignClient interfaces. Without this they are never
// instantiated and injection fails with "no qualifying bean".
@EnableFeignClients
public class GuardServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(GuardServiceApplication.class, args);
    }

    /**
     * Local smoke-test data. Runs ONLY under the "dev" profile.
     *
     * The profile gate is not cosmetic. @WebMvcTest loads this class as its
     * configuration but does not load Mongo repositories, so an ungated @Bean
     * method asking for EntryLogRepository fails the whole application context -
     * which took all six controller tests down with it.
     *
     * To keep using it locally, add SPRING_PROFILES_ACTIVE=dev to the launch
     * configuration alongside JWT_SECRET, INTERNAL_API_KEY and GUARD_CLIENTS.
     *
     * Worth knowing what it does before you rely on it: it opens a session for
     * guardUserId 1 on every startup, so requireOpenSession will find a shift no
     * guard ever started, and a scan can succeed for the wrong reason. Real seed
     * data is Day 20's job, through the API.
     */
    @Bean
    @org.springframework.context.annotation.Profile("dev")
    CommandLineRunner testInsert(EntryLogRepository entryLogRepo, ScanSessionRepository sessionRepo) {
        return args -> {
            ScanSession session = ScanSession.builder()
                    .guardUserId(1L)
                    .campusId(1L)
                    .gateId(3L)
                    .gateName("Main Gate")
                    .state(SessionState.OPEN)
                    .startedAt(LocalDateTime.now())
                    .deviceInfo(Map.of("userAgent", "test-agent"))
                    .build();
            session = sessionRepo.save(session);

            EntryLog log = EntryLog.builder() 
                    .campusId(1L)
                    .gateId(3L)
                    .gateName("Main Gate")
                    .guardUserId(1L)
                    .sessionId(session.getId())
                    .passId(90231L)
                    .holderUserId(5578L)
                    .holderName("A. Sharma")
                    .passType(PassType.DAILY)
                    .attributedEventId(17L)
                    .scanResult(ScanResult.ALLOWED)
                    .tokenFingerprint("9f2ac41b7de0")
                    .scannedAt(LocalDateTime.now())
                    .scanDate("2026-07-27")
                    .deviceInfo(Map.of("userAgent", "test-agent", "appVersion", "1.0.3"))
                    .build();
            entryLogRepo.save(log);

            System.out.println("TEST INSERT DONE");
        };
    }
}