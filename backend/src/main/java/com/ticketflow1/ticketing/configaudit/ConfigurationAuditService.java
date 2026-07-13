package com.ticketflow1.ticketing.configaudit;

import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.user.AppUserRepository;
import org.springframework.stereotype.Service;

@Service
public class ConfigurationAuditService {
    private final ConfigurationAuditRepository repository;
    private final AppUserRepository users;
    public ConfigurationAuditService(ConfigurationAuditRepository repository, AppUserRepository users) {
        this.repository = repository; this.users = users;
    }
    public void record(Organization organization, Long actorId, String type, Long targetId, String action,
            String oldValue, String newValue) {
        repository.save(new ConfigurationAuditLog(organization, users.getReferenceById(actorId), type, targetId,
                action, oldValue, newValue));
    }
}
