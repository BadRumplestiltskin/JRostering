package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Site;
import com.magicsystems.jrostering.domain.ShiftType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link ShiftType} reference data.
 */
public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {

    /** Returns all shift types defined for the given site. */
    List<ShiftType> findBySite(Site site);
}
