package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.ticketconfig.TicketConfigurationDtos.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasAuthority('TYPE_MANAGE')")
public class TicketConfigurationController {
    private final TicketConfigurationService service;
    public TicketConfigurationController(TicketConfigurationService service){this.service=service;}
    @GetMapping("/ticket-types/{typeId}/subtypes") public List<SubtypeResponse> subtypes(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long typeId){return service.listSubtypes(p,typeId).stream().map(SubtypeResponse::from).toList();}
    @PostMapping("/ticket-types/{typeId}/subtypes") @ResponseStatus(HttpStatus.CREATED) public SubtypeResponse createSubtype(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long typeId,@RequestBody CreateSubtype r){return SubtypeResponse.from(service.createSubtype(p,typeId,r.key(),r.name(),r.description(),value(r.sortOrder())));}
    @PutMapping("/subtypes/{id}") public SubtypeResponse updateSubtype(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id,@RequestBody UpdateSubtype r){return SubtypeResponse.from(service.updateSubtype(p,id,r.version(),r.name(),r.description(),value(r.sortOrder())));}
    @PutMapping("/ticket-types/{typeId}/subtypes/order") @ResponseStatus(HttpStatus.NO_CONTENT) public void reorderSubtypes(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long typeId,@RequestBody Reorder r){service.reorderSubtypes(p,typeId,r.ids());}
    @PostMapping("/subtypes/{id}/activate") @ResponseStatus(HttpStatus.NO_CONTENT) public void activateSubtype(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.setSubtypeActive(p,id,true);}
    @PostMapping("/subtypes/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateSubtype(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.setSubtypeActive(p,id,false);}
    @DeleteMapping("/subtypes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSubtype(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.deleteSubtype(p,id);}

    @GetMapping("/subtypes/{subtypeId}/fields") public List<FieldResponse> fields(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long subtypeId){return service.listFields(p,subtypeId).stream().map(FieldResponse::from).toList();}
    @PostMapping("/subtypes/{subtypeId}/fields") @ResponseStatus(HttpStatus.CREATED) public FieldResponse createField(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long subtypeId,@RequestBody CreateField r){return FieldResponse.from(service.createField(p,subtypeId,r.key(),r.label(),r.helpText(),r.fieldKind(),r.required(),r.visibility(),value(r.sortOrder()),r.minLength(),r.maxLength(),r.minNumber(),r.maxNumber()));}
    @PutMapping("/fields/{id}") public FieldResponse updateField(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id,@RequestBody UpdateField r){return FieldResponse.from(service.updateField(p,id,r.version(),r.label(),r.helpText(),r.required(),r.visibility(),value(r.sortOrder()),r.minLength(),r.maxLength(),r.minNumber(),r.maxNumber()));}
    @PutMapping("/subtypes/{subtypeId}/fields/order") @ResponseStatus(HttpStatus.NO_CONTENT) public void reorderFields(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long subtypeId,@RequestBody Reorder r){service.reorderFields(p,subtypeId,r.ids());}
    @PostMapping("/fields/{id}/activate") @ResponseStatus(HttpStatus.NO_CONTENT) public void activateField(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.setFieldActive(p,id,true);}
    @PostMapping("/fields/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateField(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.setFieldActive(p,id,false);}
    @DeleteMapping("/fields/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteField(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.deleteField(p,id);}

    @GetMapping("/fields/{fieldId}/options") public List<OptionResponse> options(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long fieldId){return service.listOptions(p,fieldId).stream().map(OptionResponse::from).toList();}
    @PostMapping("/fields/{fieldId}/options") @ResponseStatus(HttpStatus.CREATED) public OptionResponse createOption(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long fieldId,@RequestBody CreateOption r){return OptionResponse.from(service.createOption(p,fieldId,r.key(),r.label(),value(r.sortOrder())));}
    @PutMapping("/field-options/{id}") public OptionResponse updateOption(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id,@RequestBody UpdateOption r){return OptionResponse.from(service.updateOption(p,id,r.version(),r.label(),value(r.sortOrder())));}
    @PutMapping("/fields/{fieldId}/options/order") @ResponseStatus(HttpStatus.NO_CONTENT) public void reorderOptions(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long fieldId,@RequestBody Reorder r){service.reorderOptions(p,fieldId,r.ids());}
    @PostMapping("/field-options/{id}/activate") @ResponseStatus(HttpStatus.NO_CONTENT) public void activateOption(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.setOptionActive(p,id,true);}
    @PostMapping("/field-options/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateOption(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.setOptionActive(p,id,false);}
    @DeleteMapping("/field-options/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteOption(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.deleteOption(p,id);}

    @GetMapping("/subtypes/{subtypeId}/routing") public RoutingResponse routing(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long subtypeId,@RequestParam(required=false) Long organizationId){return RoutingResponse.from(service.getRouting(p,subtypeId,organizationId));}
    @PutMapping("/subtypes/{subtypeId}/routing") public RoutingResponse putRouting(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long subtypeId,@RequestBody PutRouting r){return RoutingResponse.from(service.putRouting(p,subtypeId,r.organizationId(),r.teamId(),r.primaryDeveloperId(),r.fallbackDeveloperId(),r.approverId(),r.active(),r.version()));}
    @DeleteMapping("/subtypes/{subtypeId}/routing") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteRouting(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long subtypeId,@RequestParam(required=false) Long organizationId){service.deactivateRouting(p,subtypeId,organizationId);}
    private int value(Integer v){return v==null?0:v;}
}
