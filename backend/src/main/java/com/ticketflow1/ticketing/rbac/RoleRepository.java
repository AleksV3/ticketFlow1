package com.ticketflow1.ticketing.rbac;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByOrganizationId(Long organizationId);

    List<Role> findByTemplateTrue();

    List<Role> findByOrganizationIsNull();
}
