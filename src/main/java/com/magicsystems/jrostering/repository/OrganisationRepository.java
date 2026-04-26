package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Organisation} entities.
 */
public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
}
