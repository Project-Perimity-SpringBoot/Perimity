package com.perimity.guard.document;

import com.perimity.guard.document.enums.DenyReason;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.validation.ValidationPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One document per scan. This is the digital gate register.
 *
 * Append-only: a row is never updated or deleted. Denied attempts are recorded
 * exactly like successful ones - the refusals are half the value of the log.
 *
 * MongoDB rather than Postgres because this is write-heavy, needs no joins,
 * grows into millions of rows, and is queried by simple filters.
 *
 * Entry only. There is no exit scan and no in/out toggle anywhere in here.
 */
@Document(collection = "entry_logs")
@CompoundIndex(name = "idx_campus_scanned", def = "{'campusId': 1, 'scannedAt': -1}")
@CompoundIndex(name = "idx_event_scanned", def = "{'eventId': 1, 'scannedAt': -1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntryLog {

    @Id
    private String id;

    /** Null when the token could not be decoded at all (INVALID_TOKEN). */
    @Indexed(name = "idx_pass")
    @Field("passId")
    private Long passId;

    @Indexed(name = "idx_holder")
    @Field("holderUserId")
    private Long holderUserId;

    /**
     * Copied at scan time. The log must stay readable years later even if the
     * person's identity record changes - and guard-service must never read
     * another service's database to render its own history.
     */
    @Size(max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    @Field("holderName")
    private String holderName;

    @NotNull
    @Field("guardId")
    private Long guardId;

    /** The gate this guard was bound to for the session. */
    @NotNull
    @Field("gateId")
    private Long gateId;

    @NotNull
    @Indexed(name = "idx_campus")
    @Field("campusId")
    private Long campusId;

    /**
     * Set when the entry was attributed to a running event - either the person
     * scanned an EVENT pass, or Behavior 2 attributed their DAILY scan to an
     * event running that day. Null for a normal campus entry.
     */
    @Indexed(name = "idx_event")
    @Field("eventId")
    private Long eventId;

    @NotNull
    @Field("result")
    private ScanResult result;

    /** Required when result = RED, null otherwise. */
    @Field("denyReason")
    private DenyReason denyReason;

    @NotNull
    @Indexed(name = "idx_scanned_at")
    @Field("scannedAt")
    private LocalDateTime scannedAt;

    @Size(max = 120)
    @Pattern(regexp = ValidationPatterns.DEVICE_LABEL, message = ValidationPatterns.DEVICE_LABEL_MESSAGE)
    @Field("device")
    private String device;

    @CreatedDate
    @Field("createdAt")
    private LocalDateTime createdAt;

    /** True when this scan was attributed to an event rather than general entry. */
    public boolean isEventAttributed() {
        return eventId != null;
    }
}
