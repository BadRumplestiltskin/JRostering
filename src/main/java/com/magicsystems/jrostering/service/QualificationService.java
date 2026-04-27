package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.domain.Qualification;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.repository.QualificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for managing {@link Qualification} reference data.
 *
 * <p>Qualifications are scoped to the single {@link Organisation}. They are
 * assigned to staff via {@code StaffQualification} and required on shifts via
 * {@code ShiftQualificationRequirement}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QualificationService {

    private final QualificationRepository qualificationRepository;
    private final OrganisationRepository  organisationRepository;

    @Transactional(readOnly = true)
    public List<Qualification> getAll() {
        return organisationRepository.findAll().stream()
                .findFirst()
                .map(qualificationRepository::findByOrganisation)
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Qualification getById(Long id) {
        return qualificationRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Qualification", id));
    }

    public Qualification create(String name, String description) {
        Organisation org = organisationRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new InvalidOperationException("No organisation found"));
        Qualification q = new Qualification();
        q.setOrganisation(org);
        q.setName(name.strip());
        q.setDescription(description == null || description.isBlank() ? null : description.strip());
        return qualificationRepository.save(q);
    }

    public Qualification update(Long id, String name, String description) {
        Qualification q = getById(id);
        q.setName(name.strip());
        q.setDescription(description == null || description.isBlank() ? null : description.strip());
        return qualificationRepository.save(q);
    }

    public void delete(Long id) {
        if (!qualificationRepository.existsById(id)) {
            throw EntityNotFoundException.of("Qualification", id);
        }
        qualificationRepository.deleteById(id);
    }
}
