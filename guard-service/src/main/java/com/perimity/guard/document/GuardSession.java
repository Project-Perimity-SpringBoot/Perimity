package com.perimity.guard.document;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Binds one guard to exactly one gate for the duration of a shift (v1.1).
 *
 * Without this, a scan cannot say which gate it happened at, and the guard
 * would have to pick a gate on every single scan. The guard chooses once at
 * the start of the shift; every scan inherits the gate from the open session.
 */
@Document(collection = "guard_sessions")
@CompoundIndex(name = "idx_guard_active", def = "{'guardId': 1, 'active': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardSession {

    @Id
    private String id;

    @NotNull
    @Indexed(name = "idx_gs_guard")
    @Field("guardId")
    private Long guardId;

    @NotNull
    @Field("gateId")
    private Long gateId;

    @NotNull
    @Indexed(name = "idx_gs_campus")
    @Field("campusId")
    private Long campusId;

    @NotNull
    @Field("startedAt")
    private LocalDateTime startedAt;

    /** Null while the shift is open. */
    @Field("endedAt")
    private LocalDateTime endedAt;

    @Field("active")
    @Builder.Default
    private boolean active = true;

    @Field("device")
    private String device;
}
