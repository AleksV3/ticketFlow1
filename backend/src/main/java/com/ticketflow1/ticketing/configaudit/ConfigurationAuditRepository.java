package com.ticketflow1.ticketing.configaudit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ConfigurationAuditRepository extends JpaRepository<ConfigurationAuditLog, Long>,
        JpaSpecificationExecutor<ConfigurationAuditLog> {}
