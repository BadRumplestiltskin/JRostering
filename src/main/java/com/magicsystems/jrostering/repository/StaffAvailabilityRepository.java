package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Repository for {@link StaffAvailability} entities. */
public interface StaffAvailabilityRepository extends JpaRepository<StaffAvailability, Long> {

    List<StaffAvailability> findByStaff(Staff staff);

    /**
     * Returns all availability records for any of the given staff members.
     * Used by {@code RosterSolutionMapper} to batch-load availabilities, avoiding N+1 queries.
     */
    List<StaffAvailability> findByStaffIn(Collection<Staff> staff);
}
