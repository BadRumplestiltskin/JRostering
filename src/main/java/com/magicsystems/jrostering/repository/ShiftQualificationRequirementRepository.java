package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Qualification;
import com.magicsystems.jrostering.domain.Shift;
import com.magicsystems.jrostering.domain.ShiftQualificationRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository for {@link ShiftQualificationRequirement} entities. */
public interface ShiftQualificationRequirementRepository extends JpaRepository<ShiftQualificationRequirement, Long> {

    List<ShiftQualificationRequirement> findByShift(Shift shift);

    /**
     * Returns all qualification requirements for any of the given shifts.
     * Used by {@code RosterSolutionMapper} to batch-load requirements for all shifts
     * in a period, avoiding N+1 queries.
     */
    List<ShiftQualificationRequirement> findByShiftIn(Collection<Shift> shifts);

    Optional<ShiftQualificationRequirement> findByShiftAndQualification(Shift shift, Qualification qualification);

    boolean existsByShiftAndQualification(Shift shift, Qualification qualification);
}
