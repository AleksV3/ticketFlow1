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
        String clientAToken = login("client-a@demo.test", "client123");
        String clientBToken = login("client-b@demo.test", "client123");

        String changeRequestKey = createTicket(clientAToken, """
                {
                  "type":"CHANGE_REQUEST",
                  "title":"CR title",
                  "description":"CR desc",
                  "priority":"MEDIUM"
                }
                """, "SUBMITTED");

        createTicket(clientAToken, """
                {
                  "type":"TASK",
                  "title":"Task title",
                  "description":"Task desc",
                  "priority":"HIGH"
                }
                """, "SUBMITTED");

        createTicket(clientAToken, """
                {
                  "type":"DEFECT",
                  "title":"Defect title",
                  "description":"Defect desc",
                  "priority":"CRITICAL",
                  "severity":"SEV_1"
                }
                """, "REPORTED");

        mockMvc.perform(get("/api/tickets/{ticketKey}", changeRequestKey)
                        .header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private String login(String email, String password) throws Exception {
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
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private String createTicket(String token, String body, String expectedInitialState) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedInitialState))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String ticketKey = created.get("ticketKey").asText();

        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketKey").value(ticketKey))
                .andExpect(jsonPath("$.status").value(expectedInitialState));

        return ticketKey;
    }
}
