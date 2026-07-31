package com.perimity.guard.document;

import com.perimity.guard.document.enums.SessionState;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One guard's shift at one gate, with running counters.
 *
 * Binds one guard to exactly one gate for the duration of a shift. Without
 * this, a scan cannot say which gate it happened at, and the guard would have
 * to pick a gate on every single scan. The guard chooses once at the start of
 * the shift; every scan inherits the gate from the open session.
 */
@Document(collection = "scan_sessions")
@CompoundIndexes({
    @CompoundIndex(name = "idx_guard_state", def = "{'guardUserId': 1, 'state': 1}"),
    @CompoundIndex(name = "idx_campus_started", def = "{'campusId': 1, 'startedAt': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSession {

    @Id
    private String id;

    @NotNull
    @Field("guardUserId")
    private Long guardUserId;

    @NotNull
    @Field("campusId")
    private Long campusId;

    /** The one gate this session is pinned to. */
    @NotNull
    @Field("gateId")
    private Long gateId;

    /** Denormalised label. */
    @NotNull
    @Field("gateName")
    private String gateName;

    @NotNull
    @Field("state")
    private SessionState state;

    @NotNull
    @Field("startedAt")
    private LocalDateTime startedAt;

    /** Null while the shift is open. */
    @Field("endedAt")
    private LocalDateTime endedAt;

    @Builder.Default
    @Field("totalScans")
    private Integer totalScans = 0;

    @Builder.Default
    @Field("allowedCount")
    private Integer allowedCount = 0;

    @Builder.Default
    @Field("deniedCount")
    private Integer deniedCount = 0;

    /** The scanner device used for this shift. */
    @Field("deviceInfo")
    private Map<String, Object> deviceInfo;
}