package com.ticketflow1.ticketing.organization;

import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.dto.CreateOrganizationRequest;
import com.ticketflow1.ticketing.organization.dto.OrganizationResponse;
import com.ticketflow1.ticketing.organization.dto.UpdateOrganizationRequest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final EntityManager entityManager;

    public OrganizationService(OrganizationRepository organizationRepository, EntityManager entityManager) {
        this.organizationRepository = organizationRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> list() {
        return organizationRepository.findAll(Sort.by("name")).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        if (organizationRepository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.validation("An organization named '" + request.name() + "' already exists.");
        }
        // saveAndFlush forces the INSERT now so @CreationTimestamp is populated
        // before we map the response (UUID keys otherwise defer INSERT to commit).
        Organization saved = organizationRepository.saveAndFlush(new Organization(request.name()));
        // FR-022: clone the CLIENT-party role templates and ticket
        // types/workflows so this organization gets its own editable copies —
        // the same function V6 calls for the demo seed orgs (V2 migration).
        entityManager.createNativeQuery("SELECT clone_org_templates(:orgId)")
                .setParameter("orgId", saved.getId())
                .getSingleResult();
        return OrganizationResponse.from(saved);
    }

    @Transactional
    public OrganizationResponse update(Long id, UpdateOrganizationRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Organization not found: " + id));
        if (request.name() != null && !request.name().isBlank()) {
            org.setName(request.name());
        }
        if (request.active() != null) {
            org.setActive(request.active());
        }
        return OrganizationResponse.from(org);
    }
}
