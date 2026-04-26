package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Staff;
import com.magicsystems.jrostering.domain.StaffPairing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository for {@link StaffPairing} associations. */
public interface StaffPairingRepository extends JpaRepository<StaffPairing, Long> {

    /**
     * Returns all pairing records involving the given staff member
     * (in either the staffA or staffB position).
     */
    @Query("SELECT p FROM StaffPairing p WHERE p.staffA = :staff OR p.staffB = :staff")
    List<StaffPairing> findByStaff(@Param("staff") Staff staff);

    /**
     * Returns all pairing records where either staffA or staffB belongs to the given set.
     * Used by {@code RosterSolutionMapper} to batch-load pairings for all eligible staff
     * in a single query.
     */
    @Query("SELECT p FROM StaffPairing p WHERE p.staffA IN :staffSet OR p.staffB IN :staffSet")
    List<StaffPairing> findByStaffAInOrStaffBIn(@Param("staffSet") Collection<Staff> staffSet);

    /**
     * Looks up a canonical-ordered pair. Caller must ensure {@code lower.id < higher.id}.
     */
    Optional<StaffPairing> findByStaffAAndStaffB(Staff lower, Staff higher);

    /**
     * Checks whether a canonical-ordered pair exists. Caller must ensure {@code lower.id < higher.id}.
     */
    boolean existsByStaffAAndStaffB(Staff lower, Staff higher);
}
