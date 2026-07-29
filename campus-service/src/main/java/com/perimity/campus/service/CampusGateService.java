package com.perimity.campus.service;

import com.perimity.campus.dto.request.CampusGateCreateDto;
import com.perimity.campus.dto.request.CampusGateUpdateDto;
import com.perimity.campus.dto.response.CampusGateResponse;
import com.perimity.campus.entity.CampusGate;
import com.perimity.campus.exception.ResourceNotFoundException;
import com.perimity.campus.repository.CampusGateRepository;
import com.perimity.campus.repository.CampusRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Physical gates.
 *
 * A guard binds to exactly one gate per shift and every scan is recorded
 * against it, so a gate must exist before anyone can work a shift. Guard-service
 * reads this list to fill the gate picker.
 */
@Service
public class CampusGateService {

    private final CampusGateRepository gateRepository;
    private final CampusRepository campusRepository;

    public CampusGateService(CampusGateRepository gateRepository,
                             CampusRepository campusRepository) {
        this.gateRepository = gateRepository;
        this.campusRepository = campusRepository;
    }

    @Transactional
    public CampusGateResponse create(CampusGateCreateDto dto) {
        requireCampus(dto.getCampusId());

        // Names are unique within a campus, not globally. Two institutions may
        // both have a "Main Gate" and that is correct.
        if (gateRepository.existsByCampusIdAndNameIgnoreCase(dto.getCampusId(), dto.getName())) {
            throw new IllegalArgumentException(
                    "This campus already has a gate named \"" + dto.getName() + "\".");
        }

        return CampusGateResponse.from(gateRepository.save(CampusGate.builder()
                .campusId(dto.getCampusId())
                .name(dto.getName().trim())
                .location(dto.getLocation())
                .active(true)
                .build()));
    }

    /**
     * Edit, or take out of service.
     *
     * Deactivating a gate is allowed here, unlike deactivating a campus, because
     * it is low-risk and reversible - one entrance closes for maintenance. The
     * difference is blast radius, not formatting preference.
     */
    @Transactional
    public CampusGateResponse update(Long campusId, Long id, CampusGateUpdateDto dto) {
        CampusGate gate = require(campusId, id);

        if (!gate.getName().equalsIgnoreCase(dto.getName())
                && gateRepository.existsByCampusIdAndNameIgnoreCase(campusId, dto.getName())) {
            throw new IllegalArgumentException(
                    "This campus already has a gate named \"" + dto.getName() + "\".");
        }

        // Refusing to close the last gate. A campus with no active gate cannot
        // admit anyone, and the failure would surface as a guard unable to open
        // a session with no explanation of why.
        boolean closingThisOne = gate.isActive() && !dto.getActive();
        if (closingThisOne && gateRepository.countByCampusIdAndActiveTrue(campusId) <= 1) {
            throw new IllegalStateException(
                    "This is the campus's only active gate. Add another before closing it.");
        }

        gate.setName(dto.getName().trim());
        gate.setLocation(dto.getLocation());
        gate.setActive(dto.getActive());

        return CampusGateResponse.from(gateRepository.save(gate));
    }

    @Transactional(readOnly = true)
    public CampusGateResponse getOne(Long campusId, Long id) {
        return CampusGateResponse.from(require(campusId, id));
    }

    /** What the guard's gate picker shows - active gates only. */
    @Transactional(readOnly = true)
    public List<CampusGateResponse> listActive(Long campusId) {
        return gateRepository.findByCampusIdAndActiveTrueOrderByNameAsc(campusId)
                .stream().map(CampusGateResponse::from).toList();
    }

    /** Everything including closed gates - the admin view. */
    @Transactional(readOnly = true)
    public List<CampusGateResponse> listAll(Long campusId) {
        return gateRepository.findByCampusIdOrderByNameAsc(campusId)
                .stream().map(CampusGateResponse::from).toList();
    }

    private void requireCampus(Long campusId) {
        if (!campusRepository.existsById(campusId)) {
            throw ResourceNotFoundException.of("Campus", campusId);
        }
    }

    /** Campus-scoped, so a gate belonging to another campus reads as not found. */
    private CampusGate require(Long campusId, Long id) {
        CampusGate gate = gateRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Gate", id));
        if (!gate.getCampusId().equals(campusId)) {
            throw ResourceNotFoundException.of("Gate", id);
        }
        return gate;
    }
}
