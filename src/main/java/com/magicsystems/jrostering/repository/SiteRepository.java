package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link Site} entities.
 */
public interface SiteRepository extends JpaRepository<Site, Long> {

    /** Returns all active sites belonging to the given organisation. */
    List<Site> findByOrganisationAndActiveTrue(Organisation organisation);
}
