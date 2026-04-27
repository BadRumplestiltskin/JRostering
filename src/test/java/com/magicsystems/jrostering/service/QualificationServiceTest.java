package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.domain.Qualification;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.repository.QualificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QualificationServiceTest {

    @Mock QualificationRepository qualificationRepository;
    @Mock OrganisationRepository  organisationRepository;

    @InjectMocks QualificationService service;

    private Organisation org;

    @BeforeEach
    void setUp() {
        org = new Organisation();
        org.setId(1L);
        org.setName("Test Org");
    }

    @Test
    void getAll_returnsQualificationsForOrganisation() {
        Qualification q = qualification(1L, "First Aid");
        when(organisationRepository.findAll()).thenReturn(List.of(org));
        when(qualificationRepository.findByOrganisation(org)).thenReturn(List.of(q));

        List<Qualification> result = service.getAll();

        assertThat(result).containsExactly(q);
    }

    @Test
    void getAll_returnsEmpty_whenNoOrganisation() {
        when(organisationRepository.findAll()).thenReturn(List.of());

        List<Qualification> result = service.getAll();

        assertThat(result).isEmpty();
        verifyNoInteractions(qualificationRepository);
    }

    @Test
    void create_savesWithTrimmedNameAndDescription() {
        when(organisationRepository.findAll()).thenReturn(List.of(org));
        when(qualificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Qualification result = service.create("  RSA  ", "  Responsible service  ");

        assertThat(result.getName()).isEqualTo("RSA");
        assertThat(result.getDescription()).isEqualTo("Responsible service");
        assertThat(result.getOrganisation()).isEqualTo(org);
    }

    @Test
    void create_setsNullDescription_whenBlank() {
        when(organisationRepository.findAll()).thenReturn(List.of(org));
        when(qualificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Qualification result = service.create("First Aid", "   ");

        assertThat(result.getDescription()).isNull();
    }

    @Test
    void create_throwsInvalidOperation_whenNoOrganisation() {
        when(organisationRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.create("First Aid", null))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void update_changesNameAndDescription() {
        Qualification q = qualification(1L, "Old Name");
        when(qualificationRepository.findById(1L)).thenReturn(Optional.of(q));
        when(qualificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Qualification result = service.update(1L, "New Name", "New desc");

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New desc");
    }

    @Test
    void update_throwsEntityNotFound_whenMissing() {
        when(qualificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, "X", null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_callsRepository_whenExists() {
        when(qualificationRepository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(qualificationRepository).deleteById(1L);
    }

    @Test
    void delete_throwsEntityNotFound_whenMissing() {
        when(qualificationRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(EntityNotFoundException.class);
        verify(qualificationRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------

    private static Qualification qualification(Long id, String name) {
        Qualification q = new Qualification();
        q.setId(id);
        q.setName(name);
        return q;
    }
}
