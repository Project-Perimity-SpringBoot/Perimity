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
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GuardServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(GuardServiceApplication.class, args);
    }

    @Bean
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