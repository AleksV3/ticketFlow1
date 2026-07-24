package com.ticketflow1.ticketing.preference;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.preference.dto.PreferenceResponse;
import com.ticketflow1.ticketing.preference.dto.ReplacePreferenceRequest;
import com.ticketflow1.ticketing.team.DeveloperTeam;
import com.ticketflow1.ticketing.team.DeveloperTeamRepository;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {

    private static final List<String> DEFAULT_FILTERS =
            List.of("TYPE", "STATUS", "PRIORITY", "TEAM");

    private final UserOrganizationPreferenceRepository preferences;
    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final DeveloperTeamRepository teams;

    public PreferenceService(UserOrganizationPreferenceRepository preferences,
            AppUserRepository users, OrganizationRepository organizations,
            DeveloperTeamRepository teams) {
        this.preferences = preferences;
        this.users = users;
        this.organizations = organizations;
        this.teams = teams;
    }

    @Transactional(readOnly = true)
    public PreferenceResponse get(AuthPrincipal principal) {
        return find(principal).map(preference -> response(preference, principal))
                .orElseGet(this::defaults);
    }

    @Transactional
    public PreferenceResponse replace(ReplacePreferenceRequest request, AuthPrincipal principal) {
        List<String> widgets = validateCatalog(
                request.dashboardWidgets(), DashboardWidget.class, "dashboard widget");
        List<String> filters = validateCatalog(
                request.enabledTicketFilters(), TicketFilterPreference.class, "ticket filter");
        PreferenceTheme theme = parseTheme(request.theme());
        DeveloperTeam team = resolveTeam(request.lastViewedTeamId(), principal, true);

        UserOrganizationPreference preference = find(principal).orElseGet(() -> newPreference(principal));
        if (preference.getVersion() != request.version()) {
            throw ApiException.conflict(
                    "Preferences were changed in another session. Reload and try again.");
        }
        preference.replace(widgets, filters, team, theme);
        return response(preferences.saveAndFlush(preference), principal);
    }

    @Transactional
    public PreferenceResponse reset(AuthPrincipal principal) {
        find(principal).ifPresent(preferences::delete);
        preferences.flush();
        return defaults();
    }

    private java.util.Optional<UserOrganizationPreference> find(AuthPrincipal principal) {
        if (principal.organizationId() == null) {
            return preferences.findByUserIdAndOrganizationIsNull(principal.userId());
        }
        return preferences.findByUserIdAndOrganizationId(
                principal.userId(), principal.organizationId());
    }

    private UserOrganizationPreference newPreference(AuthPrincipal principal) {
        AppUser user = users.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        Organization organization = principal.organizationId() == null ? null
                : organizations.findById(principal.organizationId())
                        .orElseThrow(() -> ApiException.notFound(
                                "Current organization no longer exists."));
        return new UserOrganizationPreference(user, organization);
    }

    private PreferenceResponse response(
            UserOrganizationPreference preference, AuthPrincipal principal) {
        List<String> widgets = knownValues(
                preference.getDashboardWidgets(), DashboardWidget.class);
        List<String> filters = knownValues(
                preference.getEnabledTicketFilters(), TicketFilterPreference.class);
        DeveloperTeam team = preference.getLastViewedTeam();
        Long visibleTeamId = team != null
                && resolveTeam(team.getId(), principal, false) != null ? team.getId() : null;
        return new PreferenceResponse(
                widgets, filters, visibleTeamId, preference.getTheme().name(),
                preference.getVersion());
    }

    private PreferenceResponse defaults() {
        return new PreferenceResponse(
                DashboardWidget.defaults(), DEFAULT_FILTERS, null,
                PreferenceTheme.SYSTEM.name(), 0);
    }

    private DeveloperTeam resolveTeam(
            Long teamId, AuthPrincipal principal, boolean rejectInvalid) {
        if (teamId == null) {
            return null;
        }
        boolean visible = teams.findDistinctByLeaderIdOrMembersIdOrderByNameAsc(
                        principal.userId(), principal.userId()).stream()
                .anyMatch(team -> team.getId().equals(teamId));
        if (!visible) {
            if (rejectInvalid) {
                throw ApiException.conflict(
                        "The selected team is no longer assigned to this user.");
            }
            return null;
        }
        return teams.getReferenceById(teamId);
    }

    private PreferenceTheme parseTheme(String value) {
        try {
            return PreferenceTheme.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw ApiException.validation("Unknown theme: " + value);
        }
    }

    private <E extends Enum<E>> List<String> validateCatalog(
            List<String> values, Class<E> catalog, String label) {
        if (values == null) {
            throw ApiException.validation(label + " list is required.");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> distinct = new HashSet<>();
        for (String value : values) {
            String key = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            try {
                Enum.valueOf(catalog, key);
            } catch (RuntimeException exception) {
                throw ApiException.validation("Unknown " + label + ": " + value);
            }
            if (!distinct.add(key)) {
                throw ApiException.validation("Duplicate " + label + ": " + key);
            }
            normalized.add(key);
        }
        return List.copyOf(normalized);
    }

    private <E extends Enum<E>> List<String> knownValues(
            List<String> values, Class<E> catalog) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> {
            try {
                Enum.valueOf(catalog, value);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }).distinct().toList();
    }
}
