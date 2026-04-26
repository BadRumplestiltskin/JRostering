package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.domain.Qualification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link Qualification} reference data.
 */
public interface QualificationRepository extends JpaRepository<Qualification, Long> {

    /** Returns all qualifications defined for the given organisation. */
    List<Qualification> findByOrganisation(Organisation organisation);
}
