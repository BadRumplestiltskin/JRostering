package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.RuleConfiguration;
import com.magicsystems.jrostering.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link RuleConfiguration} entities.
 */
public interface RuleConfigurationRepository extends JpaRepository<RuleConfiguration, Long> {

    /** Returns all rule configurations for the given site. */
    List<RuleConfiguration> findBySite(Site site);

    /** Returns all enabled rule configurations for the given site. */
    List<RuleConfiguration> findBySiteAndEnabledTrue(Site site);
}
