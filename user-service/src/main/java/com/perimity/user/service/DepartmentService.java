package com.perimity.user.service;

import com.perimity.user.dto.request.DepartmentCreateDto;
import com.perimity.user.dto.request.DepartmentUpdateDto;
import com.perimity.user.dto.response.DepartmentResponse;
import com.perimity.user.entity.Department;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.DepartmentRepository;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for departments - the per-campus list every profile, dropdown
 * and directory filter selects from.
 *
 * Division of labour with the DTO layer: DepartmentCreateDto and
 * DepartmentUpdateDto have already proved the input is well formed - code and
 * name shape, length, required fields. Everything in here needs the database
 * or the current state of a row, which is exactly what a DTO cannot see:
 * duplicate codes on the same campus, and whether a department still has
 * people attached before it is retired from new selections.
 */
@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final FacultyProfileRepository facultyProfileRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                              StudentProfileRepository studentProfileRepository,
                              FacultyProfileRepository facultyProfileRepository) {
        this.departmentRepository = departmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.facultyProfileRepository = facultyProfileRepository;
    }

    /**
     * Seed a department for a campus. Rejects a duplicate code on the same
     * campus - the same code may legitimately repeat across different campuses,
     * so this check is always scoped by campusId, never global.
     */
    @Transactional
    public DepartmentResponse create(DepartmentCreateDto dto) {
        if (departmentRepository.existsByCampusIdAndCodeIgnoreCase(dto.getCampusId(), dto.getCode())) {
            throw new IllegalArgumentException(
                    "A department with code \"" + dto.getCode() + "\" already exists on this campus");
        }

        Department department = Department.builder()
                .campusId(dto.getCampusId())
                .code(dto.getCode().trim())
                .name(dto.getName().trim())
                .active(true)
                .build();

        return DepartmentResponse.from(departmentRepository.save(department));
    }

    /** One department, scoped to its campus so nobody reads another campus's data. */
    @Transactional(readOnly = true)
    public DepartmentResponse getOne(Long campusId, Long id) {
        return DepartmentResponse.from(require(campusId, id));
    }

    /**
     * The department list for a campus. activeOnly=true is what every dropdown
     * and profile form uses (FR-PROF-9 - no free-text entry); activeOnly=false
     * is for the campus admin's own management screen, which still needs to
     * see retired departments to review or reactivate them.
     */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> list(Long campusId, boolean activeOnly) {
        List<Department> departments = activeOnly
                ? departmentRepository.findByCampusIdAndActiveTrueOrderByNameAsc(campusId)
                : departmentRepository.findByCampusIdOrderByNameAsc(campusId);

        return departments.stream().map(DepartmentResponse::from).toList();
    }

    /**
     * Rename a department, or retire/restore it from new selections.
     *
     * code and campusId are not editable here - profiles already point at this
     * department by id, and renaming its code would break every report that
     * groups by it. Deactivating is refused while students or faculty are
     * still attached, because a department a person is assigned to must not
     * simply vanish from the system - it just stops appearing in new forms.
     */
    @Transactional
    public DepartmentResponse update(Long campusId, Long id, DepartmentUpdateDto dto) {
        Department department = require(campusId, id);

        boolean deactivating = department.isActive() && !dto.getActive();
        if (deactivating) {
            boolean hasStudents = !studentProfileRepository.findByDepartmentId(id).isEmpty();
            boolean hasFaculty = !facultyProfileRepository.findByDepartmentId(id).isEmpty();
            if (hasStudents || hasFaculty) {
                throw new IllegalStateException(
                        "This department still has students or faculty attached and cannot be deactivated");
            }
        }

        department.setName(dto.getName().trim());
        department.setActive(dto.getActive());

        return DepartmentResponse.from(departmentRepository.save(department));
    }

    /** Load or 404. Campus-scoped, so a wrong campus reads as "not found". */
    private Department require(Long campusId, Long id) {
        return departmentRepository.findByIdAndCampusId(id, campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Department", id));
    }
}
