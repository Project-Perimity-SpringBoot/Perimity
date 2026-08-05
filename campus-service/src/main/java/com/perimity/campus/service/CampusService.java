package com.perimity.campus.service;

import com.perimity.campus.dto.request.CampusCreateDto;
import com.perimity.campus.dto.request.CampusStatusUpdateDto;
import com.perimity.campus.dto.request.CampusUpdateDto;
import com.perimity.campus.dto.response.CampusResponse;
import com.perimity.campus.dto.response.CampusStatsResponse;
import com.perimity.campus.entity.Campus;
import com.perimity.campus.exception.ResourceNotFoundException;
import com.perimity.campus.repository.CampusConfigRepository;
import com.perimity.campus.repository.CampusGateRepository;
import com.perimity.campus.repository.CampusRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Institutions. This service is what makes Perimity multi-tenant rather than
 * one organisation's internal tool.
 *
 * The DTOs already proved the input is well formed - code shape, email shape,
 * no path traversal in the logo key. Everything here needs the database.
 */
@Service
public class CampusService {

    private static final Logger log = LoggerFactory.getLogger(CampusService.class);

    private final CampusRepository campusRepository;
    private final CampusGateRepository gateRepository;
    private final CampusConfigRepository configRepository;

    public CampusService(CampusRepository campusRepository,
                         CampusGateRepository gateRepository,
                         CampusConfigRepository configRepository) {
        this.campusRepository = campusRepository;
        this.gateRepository = gateRepository;
        this.configRepository = configRepository;
    }

    /**
     * Onboard an institution.
     *
     * Seeds the default policy set in the same transaction. Either the campus
     * and its settings both exist, or neither does - a half-created campus
     * whose config is missing would fail in five other services with errors
     * that point nowhere near here.
     */
    @Transactional
    public CampusResponse create(CampusCreateDto dto) {
        String code = dto.getCode().trim().toUpperCase();

        if (campusRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException(
                    "A campus with code \"" + code + "\" already exists.");
        }

        // One admin account runs one campus. Reusing an account across two
        // would give that person authority in both, silently.
        if (dto.getAdminUserId() != null) {
            campusRepository.findByAdminUserId(dto.getAdminUserId()).ifPresent(other -> {
                throw new IllegalArgumentException(
                        "That admin account already runs campus \"" + other.getCode() + "\".");
            });
        }

        Campus campus = campusRepository.save(Campus.builder()
                .code(code)
                .name(dto.getName().trim())
                .address(dto.getAddress())
                .contactEmail(dto.getContactEmail())
                .contactPhone(dto.getContactPhone())
                .logoS3Key(dto.getLogoS3Key())
                .adminUserId(dto.getAdminUserId())
                .active(true)
                .build());

        configRepository.saveAll(CampusConfigDefaults.forCampus(campus.getId()));
        log.info("Campus {} created with {} default settings",
                campus.getCode(), CampusConfigDefaults.ALL.size());

        return CampusResponse.from(campus, 0L);
    }

    /**
     * Edit. The code is not editable and is absent from the DTO - it is baked
     * into storage prefixes and log lines the moment the campus is created, so
     * renaming it would orphan every object already written under it.
     */
    @Transactional
    public CampusResponse update(Long id, CampusUpdateDto dto) {
        Campus campus = require(id);

        if (dto.getAdminUserId() != null
                && !dto.getAdminUserId().equals(campus.getAdminUserId())) {
            campusRepository.findByAdminUserId(dto.getAdminUserId()).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new IllegalArgumentException(
                            "That admin account already runs campus \"" + other.getCode() + "\".");
                }
            });
        }

        campus.setName(dto.getName().trim());
        campus.setAddress(dto.getAddress());
        campus.setContactEmail(dto.getContactEmail());
        campus.setContactPhone(dto.getContactPhone());
        campus.setLogoS3Key(dto.getLogoS3Key());
        campus.setAdminUserId(dto.getAdminUserId());

        return withGateCount(campusRepository.save(campus));
    }

    /**
     * Activate or deactivate an entire institution.
     *
     * Its own endpoint, with a mandatory reason, because this takes every gate,
     * every guard session and every pass offline at once. That is not a stray
     * boolean on an edit form.
     */
    @Transactional
    public CampusResponse changeStatus(Long id, CampusStatusUpdateDto dto) {
        Campus campus = require(id);

        if (campus.isActive() == dto.getActive()) {
            throw new IllegalStateException("This campus is already "
                    + (campus.isActive() ? "active" : "inactive") + ".");
        }

        campus.setActive(dto.getActive());
        campusRepository.save(campus);

        // The reason is not stored on the row on purpose - a campus has one
        // current state, not a history column. This log line is the record
        // until the shared audit trail exists in auth-service.
        log.warn("Campus {} set {} by user {} - {}",
                campus.getCode(), dto.getActive() ? "ACTIVE" : "INACTIVE",
                dto.getChangedBy(), dto.getReason());

        return withGateCount(campus);
    }

    @Transactional(readOnly = true)
    public CampusResponse getOne(Long id) {
        return withGateCount(require(id));
    }

    /**
     * Lookup by code. Other services hold the code in URLs and storage
     * prefixes, so this is the path they use rather than guessing an id.
     */
    @Transactional(readOnly = true)
    public CampusResponse getByCode(String code) {
        Campus campus = campusRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No campus with code \"" + code + "\""));
        return withGateCount(campus);
    }

    @Transactional(readOnly = true)
    public List<CampusResponse> listActive() {
        return campusRepository.findByActiveTrueOrderByNameAsc()
                .stream().map(this::withGateCount).toList();
    }

    @Transactional(readOnly = true)
    public List<CampusResponse> listAll() {
        return campusRepository.findAll().stream().map(this::withGateCount).toList();
    }

    /**
     * Super Admin dashboard counts.
     *
     * Only what campus-service owns. Pass and scan totals live in other
     * services and must be fetched from them - reading another service's
     * database directly is the one thing the architecture forbids.
     */
    @Transactional(readOnly = true)
    public CampusStatsResponse stats() {
        return CampusStatsResponse.of(campusRepository.count(), campusRepository.countByActiveTrue());
    }

    private CampusResponse withGateCount(Campus campus) {
        return CampusResponse.from(campus, gateRepository.countByCampusIdAndActiveTrue(campus.getId()));
    }

    private Campus require(Long id) {
        return campusRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Campus", id));
    }
}
