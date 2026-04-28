package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service facade for the single {@link Organisation} entity.
 *
 * <p>JRostering is a single-organisation deployment. This service provides a
 * typed read interface so that UI views and other services do not need to
 * depend directly on {@link OrganisationRepository}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganisationService {

    private final OrganisationRepository organisationRepository;

    /**
     * Returns the single organisation for this installation.
     *
     * @throws InvalidOperationException if the seed migration has not been applied
     */
    public Organisation getCurrent() {
        return organisationRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new InvalidOperationException(
                        "No organisation found. Ensure the V3 seed migration has been applied."));
    }

    /**
     * Returns the ID of the current organisation.
     *
     * @throws InvalidOperationException if no organisation exists
     */
    public Long currentOrganisationId() {
        return getCurrent().getId();
    }
}
