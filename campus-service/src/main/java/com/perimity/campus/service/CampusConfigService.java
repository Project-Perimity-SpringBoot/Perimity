package com.perimity.campus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perimity.campus.dto.request.CampusConfigBulkUpsertDto;
import com.perimity.campus.dto.request.CampusConfigUpsertDto;
import com.perimity.campus.dto.response.CampusConfigResponse;
import com.perimity.campus.entity.CampusConfig;
import com.perimity.campus.entity.enums.ConfigValueType;
import com.perimity.campus.exception.ResourceNotFoundException;
import com.perimity.campus.repository.CampusConfigRepository;
import com.perimity.campus.repository.CampusRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-campus policy settings.
 *
 * The DTO already checked that the text parses as the declared type - BOOLEAN
 * is true or false, INTEGER is digits, JSON is at least bracketed. This class
 * adds the one check the DTO could not do cheaply: a real JSON parse.
 *
 * Why any of this matters: campus_config is key-value, so the column is text
 * and text is what it accepts. Nothing in the schema can stop
 * approval.required from holding the word "banana". If it did, five services
 * would read a broken setting and the failure would surface days later, at a
 * gate, as behaviour nobody can explain.
 */
@Service
public class CampusConfigService {

    private final CampusConfigRepository configRepository;
    private final CampusRepository campusRepository;
    private final ObjectMapper objectMapper;

    public CampusConfigService(CampusConfigRepository configRepository,
                               CampusRepository campusRepository,
                               ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.campusRepository = campusRepository;
        this.objectMapper = objectMapper;
    }

    /** Create or replace one setting. */
    @Transactional
    public CampusConfigResponse upsert(Long campusId, CampusConfigUpsertDto dto) {
        requireCampus(campusId);
        validateJsonIfNeeded(dto);

        /*
         * FR-CFG-4: "validate a setting value against its declared type and
         * permitted range before saving". Only JSON was being checked, so a
         * BOOLEAN key accepted "banana" and an INTEGER key accepted "abc",
         * silently, and every service reading that value inherited the
         * problem.
         *
         * It matters most for repeat_entry_result. A Campus Admin who types
         * GREENN gets a saved setting and no error; guard-service then meets a
         * value it cannot interpret at the gate, mid-scan, with a queue behind
         * the person. The cheapest place to catch that is here, once, at the
         * moment it is typed.
         */
        CampusConfigDefaults.validate(dto.getConfigKey(), dto.getConfigValue());

        CampusConfig config = configRepository
                .findByCampusIdAndConfigKey(campusId, dto.getConfigKey())
                .orElseGet(() -> CampusConfig.builder()
                        .campusId(campusId)
                        .configKey(dto.getConfigKey())
                        .build());

        config.setConfigValue(dto.getConfigValue());
        config.setValueType(dto.getValueType());
        config.setDescription(dto.getDescription());

        return CampusConfigResponse.from(configRepository.save(config));
    }

    /**
     * Save the whole settings screen at once.
     *
     * All or nothing. A partially saved settings page leaves the campus in a
     * state the admin never chose and cannot see.
     */
    @Transactional
    public List<CampusConfigResponse> upsertAll(Long campusId, CampusConfigBulkUpsertDto dto) {
        requireCampus(campusId);

        // Two rows with the same key in one payload would silently make the
        // last one win, and the admin would never learn which was dropped.
        long distinct = dto.getSettings().stream()
                .map(CampusConfigUpsertDto::getConfigKey).distinct().count();
        if (distinct != dto.getSettings().size()) {
            throw new IllegalArgumentException("The same setting key appears more than once.");
        }

        return dto.getSettings().stream().map(s -> upsert(campusId, s)).toList();
    }

    @Transactional(readOnly = true)
    public List<CampusConfigResponse> list(Long campusId) {
        requireCampus(campusId);
        return configRepository.findByCampusId(campusId)
                .stream().map(CampusConfigResponse::from).toList();
    }

    /** One setting. This is the call the other five services make. */
    @Transactional(readOnly = true)
    public CampusConfigResponse get(Long campusId, String key) {
        return configRepository.findByCampusIdAndConfigKey(campusId, key)
                .map(CampusConfigResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Campus " + campusId + " has no setting named \"" + key + "\""));
    }

    /**
     * Restore any default that is missing.
     *
     * Existing settings are left alone - this repairs a campus created before a
     * default existed, it does not undo an admin's choices.
     */
    @Transactional
    public List<CampusConfigResponse> restoreMissingDefaults(Long campusId) {
        requireCampus(campusId);

        List<CampusConfig> added = CampusConfigDefaults.forCampus(campusId).stream()
                .filter(d -> !configRepository.existsByCampusIdAndConfigKey(campusId, d.getConfigKey()))
                .toList();

        configRepository.saveAll(added);
        return added.stream().map(CampusConfigResponse::from).toList();
    }

    /**
     * The DTO checks JSON is bracketed, which catches a typo cheaply. This
     * catches malformed JSON that happens to start and end correctly.
     */
    private void validateJsonIfNeeded(CampusConfigUpsertDto dto) {
        if (dto.getValueType() != ConfigValueType.JSON
                || dto.getConfigValue() == null || dto.getConfigValue().isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(dto.getConfigValue());
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Setting \"" + dto.getConfigKey() + "\" is declared JSON but does not parse.");
        }
    }

    private void requireCampus(Long campusId) {
        if (!campusRepository.existsById(campusId)) {
            throw ResourceNotFoundException.of("Campus", campusId);
        }
    }
}
