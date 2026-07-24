package com.ticketflow1.ticketing.rbac;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByKey(String key);

    Set<Permission> findByKeyIn(Set<String> keys);
}
import java.util.Set;
