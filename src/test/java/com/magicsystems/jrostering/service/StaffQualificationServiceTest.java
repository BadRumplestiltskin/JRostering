package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffQualificationServiceTest {

    @Mock StaffRepository              staffRepository;
    @Mock QualificationRepository      qualificationRepository;
    @Mock StaffQualificationRepository staffQualificationRepository;

    @InjectMocks StaffQualificationService service;

    @Test
    void addQualification_linksQualificationToStaff() {
        Staff staff = staff(1L, "Jane", "Smith");
        Qualification qual = qualification(10L, "First Aid");
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(qualificationRepository.findById(10L)).thenReturn(Optional.of(qual));
        when(staffQualificationRepository.existsByStaffAndQualification(staff, qual))
                .thenReturn(false);
        when(staffQualificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StaffQualification result = service.addQualification(1L, 10L, null);

        assertThat(result.getStaff()).isEqualTo(staff);
        assertThat(result.getQualification()).isEqualTo(qual);
    }

    @Test
    void addQualification_throwsInvalidOperation_whenAlreadyAssigned() {
        Staff staff = staff(1L, "Jane", "Smith");
        Qualification qual = qualification(10L, "First Aid");
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(qualificationRepository.findById(10L)).thenReturn(Optional.of(qual));
        when(staffQualificationRepository.existsByStaffAndQualification(staff, qual))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addQualification(1L, 10L, null))
                .isInstanceOf(InvalidOperationException.class);
    }

    // -------------------------------------------------------------------------

    private static Staff staff(Long id, String first, String last) {
        Staff s = new Staff();
        s.setId(id);
        s.setFirstName(first);
        s.setLastName(last);
        s.setEmail(first.toLowerCase() + "@example.com");
        s.setActive(true);
        return s;
    }

    private static Qualification qualification(Long id, String name) {
        Qualification q = new Qualification();
        q.setId(id);
        q.setName(name);
        return q;
    }
}
