package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.PreferenceType;
import com.magicsystems.jrostering.domain.ShiftType;
import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;

/**
 * Repository for {@link StaffPreference} entities.
 */
public interface StaffPreferenceRepository extends JpaRepository<StaffPreference, Long> {

    /** Returns all preferences declared by the given staff member. */
    List<StaffPreference> findByStaff(Staff staff);

    /** Checks whether a PREFERRED_DAY_OFF preference already exists for this staff + day. */
    boolean existsByStaffAndPreferenceTypeAndDayOfWeek(
            Staff staff, PreferenceType preferenceType, DayOfWeek dayOfWeek);

    /** Checks whether a shift-type preference already exists for this staff + type + shift type. */
    boolean existsByStaffAndPreferenceTypeAndShiftType(
            Staff staff, PreferenceType preferenceType, ShiftType shiftType);

    /**
     * Returns all preferences for any of the given staff members.
     * Used by {@code RosterSolutionMapper} to batch-load preferences, avoiding N+1 queries.
     */
    List<StaffPreference> findByStaffIn(Collection<Staff> staff);
}
