package com.ticketflow1.ticketing.preference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOrganizationPreferenceRepository
        extends JpaRepository<UserOrganizationPreference, Long> {

    Optional<UserOrganizationPreference> findByUserIdAndOrganizationId(
            Long userId, Long organizationId);

    Optional<UserOrganizationPreference> findByUserIdAndOrganizationIsNull(Long userId);
}
