package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Organisation;
import com.magicsystems.jrostering.domain.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link Staff} entities.
 */
public interface StaffRepository extends JpaRepository<Staff, Long> {

    /** Returns all active staff in the given organisation. */
    List<Staff> findByOrganisationAndActiveTrue(Organisation organisation);
}
