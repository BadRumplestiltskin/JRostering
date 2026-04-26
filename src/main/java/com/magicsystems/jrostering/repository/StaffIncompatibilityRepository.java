package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffIncompatibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository for {@link StaffIncompatibility} associations. */
public interface StaffIncompatibilityRepository extends JpaRepository<StaffIncompatibility, Long> {

    /**
     * Returns all incompatibility records involving the given staff member
     * (in either the staffA or staffB position).
     */
    @Query("SELECT i FROM StaffIncompatibility i WHERE i.staffA = :staff OR i.staffB = :staff")
    List<StaffIncompatibility> findByStaff(@Param("staff") Staff staff);

    /**
     * Returns all incompatibility records where either staffA or staffB belongs to the
     * given set. Used by {@code RosterSolutionMapper} to batch-load incompatibilities
     * for all eligible staff in a single query.
     */
    @Query("SELECT i FROM StaffIncompatibility i WHERE i.staffA IN :staffSet OR i.staffB IN :staffSet")
    List<StaffIncompatibility> findByStaffAInOrStaffBIn(@Param("staffSet") Collection<Staff> staffSet);

    /**
     * Looks up a canonical-ordered pair. Caller must ensure {@code lower.id < higher.id}.
     */
    Optional<StaffIncompatibility> findByStaffAAndStaffB(Staff lower, Staff higher);

    /**
     * Checks whether a canonical-ordered pair exists. Caller must ensure {@code lower.id < higher.id}.
     */
    boolean existsByStaffAAndStaffB(Staff lower, Staff higher);
}
