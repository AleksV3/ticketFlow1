package com.ticketflow1.ticketing.ticket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.rbac.Role;
import com.ticketflow1.ticketing.rbac.RoleRepository;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.jwt.secret", () -> "01234567890123456789012345678901");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureClientUsersExist() {
        Organization orgA = ensureOrganizationExists("Client A");
        Organization orgB = ensureOrganizationExists("Client B");

        if (!appUserRepository.existsByEmailIgnoreCase("client-a@demo.test")) {
            Role role = roleRepository.findByOrganizationId(orgA.getId()).stream()
                    .filter(r -> "Client User".equals(r.getName()))
                    .findFirst()
                    .orElseThrow();
            AppUser user = new AppUser("client-a@demo.test", passwordEncoder.encode("client123"),
                    "Client A", Responsibility.CLIENT, role, role.getOrganization());
            appUserRepository.save(user);
        }
        if (!appUserRepository.existsByEmailIgnoreCase("client-b@demo.test")) {
            Role role = roleRepository.findByOrganizationId(orgB.getId()).stream()
                    .filter(r -> "Client User".equals(r.getName()))
                    .findFirst()
                    .orElseThrow();
            AppUser user = new AppUser("client-b@demo.test", passwordEncoder.encode("client123"),
                    "Client B", Responsibility.CLIENT, role, role.getOrganization());
            appUserRepository.save(user);
        }
    }

    private Organization ensureOrganizationExists(String name) {
        return organizationRepository.findAll().stream()
                .filter(org -> name.equals(org.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Organization saved = organizationRepository.saveAndFlush(new Organization(name));
                    jdbcTemplate.execute("select clone_org_templates(%d)".formatted(saved.getId()));
                    return saved;
                });
    }

    @Test
    void createAndReadSeededTypes_andCrossOrgReadReturns404() throws Exception {
        Cookie clientACookie = login("client-a@demo.test", "client123");
        Cookie clientBCookie = login("client-b@demo.test", "client123");

        String changeRequestKey = createTicket(clientACookie, """
                {
                  "type":"CHANGE_REQUEST",
                  "title":"CR title",
                  "description":"CR desc",
                  "priority":"MEDIUM"
                }
                """, "SUBMITTED");

        createTicket(clientACookie, """
                {
                  "type":"TASK",
                  "title":"Task title",
                  "description":"Task desc",
                  "priority":"HIGH"
                }
                """, "SUBMITTED");

        createTicket(clientACookie, """
                {
                  "type":"DEFECT",
                  "title":"Defect title",
                  "description":"Defect desc",
                  "priority":"CRITICAL",
                  "severity":"SEV_1"
                }
                """, "REPORTED");

        mockMvc.perform(get("/api/tickets/{ticketKey}", changeRequestKey)
                        .cookie(clientBCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void transitionToAnalysis_writesHistoryAndAudit_andClientCannotPerformIt() throws Exception {
        Cookie clientCookie = login("client-a@demo.test", "client123");
        Cookie internalCookie = login("admin@ticketflow1.demo", "admin123");

        String allowedTicketKey = createTicket(clientCookie, """
                {
                  "type":"CHANGE_REQUEST",
                  "title":"Transition ok",
                  "description":"CR desc",
                  "priority":"MEDIUM"
                }
                """, "SUBMITTED");

        String forbiddenTicketKey = createTicket(clientCookie, """
                {
                  "type":"CHANGE_REQUEST",
                  "title":"Transition blocked",
                  "description":"CR desc",
                  "priority":"MEDIUM"
                }
                """, "SUBMITTED");

        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", allowedTicketKey)
                        .cookie(internalCookie)
                        .contentType("application/json")
                        .content("""
                                {
                                  "toStatus":"ANALYSIS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANALYSIS"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", forbiddenTicketKey)
                        .cookie(clientCookie)
                        .contentType("application/json")
                        .content("""
                                {
                                  "toStatus":"ANALYSIS"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ILLEGAL_TRANSITION"));

        mockMvc.perform(get("/api/tickets/{ticketKey}/status-history", allowedTicketKey)
                        .cookie(internalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].fromStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].toStatus").value("ANALYSIS"));

        mockMvc.perform(get("/api/tickets/{ticketKey}/audit-log", allowedTicketKey)
                        .cookie(internalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("TICKET_CREATED"))
                .andExpect(jsonPath("$[1].action").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[1].oldValue").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].newValue").value("ANALYSIS"));
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("ticketflow1_auth");
    }

    private String createTicket(Cookie authCookie, String body, String expectedInitialState) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .cookie(authCookie)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedInitialState))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String ticketKey = created.get("ticketKey").asText();

        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey)
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketKey").value(ticketKey))
                .andExpect(jsonPath("$.status").value(expectedInitialState));

        return ticketKey;
    }
}
