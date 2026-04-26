package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for {@link StaffPreference} entities.
 */
public interface StaffPreferenceRepository extends JpaRepository<StaffPreference, Long> {

    /** Returns all preferences declared by the given staff member. */
    List<StaffPreference> findByStaff(Staff staff);

    /**
     * Returns all preferences for any of the given staff members.
     * Used by {@code RosterSolutionMapper} to batch-load preferences, avoiding N+1 queries.
     */
    List<StaffPreference> findByStaffIn(Collection<Staff> staff);
}
