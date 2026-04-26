package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Qualification;
import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffQualification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository for {@link StaffQualification} associations. */
public interface StaffQualificationRepository extends JpaRepository<StaffQualification, Long> {

    List<StaffQualification> findByStaff(Staff staff);

    /**
     * Returns all qualification records for any of the given staff members.
     * Used by {@code RosterSolutionMapper} to batch-load qualifications for all
     * staff eligible for a solve, avoiding N+1 queries.
     */
    List<StaffQualification> findByStaffIn(Collection<Staff> staff);

    Optional<StaffQualification> findByStaffAndQualification(Staff staff, Qualification qualification);

    boolean existsByStaffAndQualification(Staff staff, Qualification qualification);
}
