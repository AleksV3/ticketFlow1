package com.ticketflow1.ticketing.configaudit;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.PagedResponse;
import com.ticketflow1.ticketing.ticket.Responsibility;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api/admin/configuration-audit")
@PreAuthorize("hasAnyAuthority('USER_MANAGE','ROLE_MANAGE','TYPE_MANAGE','WORKFLOW_MANAGE')")
public class ConfigurationAuditController {
    private final ConfigurationAuditRepository repository;
    public ConfigurationAuditController(ConfigurationAuditRepository repository){this.repository=repository;}
    @GetMapping @Transactional(readOnly = true) public PagedResponse<Response> list(@AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required=false)Long organizationId,@RequestParam(defaultValue="0")int page,
            @RequestParam(defaultValue="20")int pageSize){
        Long scope=principal.party()==Responsibility.CLIENT?principal.organizationId():organizationId;
        Specification<ConfigurationAuditLog> spec=Specification.where(null);
        if(scope!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("organization").get("id"),scope));
        int size=Math.min(Math.max(pageSize,1),100);
        return PagedResponse.from(repository.findAll(spec,PageRequest.of(Math.max(page,0),size,Sort.by("createdAt").descending())),Response::from);
    }
    public record Response(Long id,Long organizationId,Long actorId,String actorName,String targetType,
            Long targetId,String action,String oldValue,String newValue,Instant createdAt){
        static Response from(ConfigurationAuditLog l){return new Response(l.getId(),l.getOrganization()==null?null:l.getOrganization().getId(),l.getActor().getId(),l.getActor().getDisplayName(),l.getTargetType(),l.getTargetId(),l.getAction(),l.getOldValue(),l.getNewValue(),l.getCreatedAt());}
    }
}
