package com.perimity.user.service;

import com.perimity.user.entity.Department;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.DepartmentRepository;
import org.springframework.stereotype.Component;

/**
 * The two checks StudentProfileService and FacultyProfileService both need, in
 * one place so they cannot drift apart.
 *
 * Neither belongs in a DTO: "does department 3 exist on campus 1, and is it
 * still active" is a database question, and a regex cannot see the database.
 */
@Component
public class ProfileGuard {

    private final DepartmentRepository departmentRepository;

    public ProfileGuard(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * A department is optional on a profile, but if one is named it must exist
     * on THIS campus and must still be active.
     *
     * The campus scoping is the point: without it, someone could attach a
     * student to another campus's department by guessing an id, and every
     * report that groups by department would quietly cross tenants.
     *
     * Retired departments are refused on new selections (FR-PROF-9) but are
     * deliberately left alone on profiles that already carry them - a person's
     * department must not vanish because the campus stopped offering it.
     */
    public void requireSelectableDepartment(Long campusId, Long departmentId) {
        if (departmentId == null) {
            return;
        }
        Department department = departmentRepository.findByIdAndCampusId(departmentId, campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Department", departmentId));

        if (!department.isActive()) {
            throw new IllegalStateException(
                    "Department \"" + department.getName() + "\" has been retired "
                            + "and cannot be selected on a profile.");
        }
    }

    /** The department's name for a response, or null when none is set. */
    public String departmentName(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .map(Department::getName)
                .orElse(null);
    }
}
