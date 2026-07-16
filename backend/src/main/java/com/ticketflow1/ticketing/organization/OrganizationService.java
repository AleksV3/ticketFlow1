package com.ticketflow1.ticketing.organization;

import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.dto.CreateOrganizationRequest;
import com.ticketflow1.ticketing.organization.dto.OrganizationResponse;
import com.ticketflow1.ticketing.organization.dto.UpdateOrganizationRequest;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
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
        // FR-022 template cloning (roles, ticket types, workflows) is wired in
        // here once the template model and clone_org_templates() exist (V2).
        return OrganizationResponse.from(saved);
    }

    @Transactional
    public OrganizationResponse update(Long id, UpdateOrganizationRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Organization not found: " + id));
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(org.getName()) && organizationRepository.existsByNameIgnoreCase(name)) {
                throw ApiException.validation("An organization named '" + name + "' already exists.");
            }
            org.setName(name);
        }
        if (request.active() != null) {
            org.setActive(request.active());
        }
        return OrganizationResponse.from(org);
    }
}
