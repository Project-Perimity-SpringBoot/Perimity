package com.perimity.guard.document;

import com.perimity.guard.document.enums.DenialReason;
import com.perimity.guard.document.enums.PassType;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.validation.ValidationPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One document per QR scan. This is the digital gate register.
 *
 * Append-only: a document is never updated or deleted. A wrong scan is
 * corrected by a later document, not by an edit. Denied scans are logged too
 * - a denial is exactly the event a security report needs.
 *
 * MongoDB rather than Postgres because this is write-heavy, needs no joins,
 * grows into millions of rows, and is shardable by campusId as volume grows.
 *
 * Entry only. There is no exit document, no in/out toggle, no occupancy
 * counter anywhere in here.
 */
@Document(collection = "entry_logs")
@CompoundIndexes({
    @CompoundIndex(name = "idx_campus_scanned", def = "{'campusId': 1, 'scannedAt': -1}"),
    @CompoundIndex(name = "idx_attributed_event_scandate", def = "{'attributedEventId': 1, 'scanDate': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntryLog {

    @Id
    private String id;

    @NotNull
    @Field("campusId")
    private Long campusId;

    @NotNull
    @Field("gateId")
    private Long gateId;

    /** Denormalised so the report renders without a lookup. */
    @NotNull
    @Field("gateName")
    private String gateName;

    @NotNull
    @Field("guardUserId")
    private Long guardUserId;

    /** Links back to the scan_sessions document for this shift. */
    @NotNull
    @Indexed(name = "idx_session")
    @Field("sessionId")
    private String sessionId;

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

    /** DAILY or EVENT - which QR was physically scanned. */
    @Field("passType")
    private PassType passType;

    /** The event the pass belongs to, if it was an EVENT pass. */
    @Field("eventId")
    private Long eventId;

    /**
     * Set when a DAILY scan was auto-credited to a running event (Behavior 2).
     * Attendance is counted on this field, not on eventId.
     */
    @Field("attributedEventId")
    private Long attributedEventId;

    @NotNull
    @Field("scanResult")
    private ScanResult scanResult;

    /** Populated only when scanResult is DENIED. */
    @Field("denialReason")
    private DenialReason denialReason;

    /** First 12 characters of the token hash - enough to correlate with QRDB, useless if leaked. */
    @Field("tokenFingerprint")
    private String tokenFingerprint;

    /** Server time of the scan, stored in UTC. */
    @NotNull
    @Field("scannedAt")
    private LocalDateTime scannedAt;

    /** yyyy-MM-dd in campus local time. Makes per-day event attendance a simple equality match. */
    @NotNull
    @Field("scanDate")
    private String scanDate;

    /** { userAgent, appVersion, ip } from the scanner device. */
    @Field("deviceInfo")
    private Map<String, Object> deviceInfo;

    /** True when this scan was attributed to an event rather than general entry. */
    public boolean isEventAttributed() {
        return attributedEventId != null;
    }
}