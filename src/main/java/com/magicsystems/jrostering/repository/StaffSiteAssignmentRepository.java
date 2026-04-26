package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Site;
import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffSiteAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Repository for {@link StaffSiteAssignment} associations. */
public interface StaffSiteAssignmentRepository extends JpaRepository<StaffSiteAssignment, Long> {

    List<StaffSiteAssignment> findByStaff(Staff staff);

    /**
     * Returns all staff-site associations for the given site.
     * Used by {@code RosterSolutionMapper} to build the eligible staff list for a solve.
     */
    List<StaffSiteAssignment> findBySite(Site site);

    Optional<StaffSiteAssignment> findByStaffAndSite(Staff staff, Site site);

    boolean existsByStaffAndSite(Staff staff, Site site);
}
