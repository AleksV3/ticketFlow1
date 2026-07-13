package com.ticketflow1.ticketing.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.proposal.ChangeProposalRepository;
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
    @Autowired
    private ChangeProposalRepository changeProposalRepository;

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
        if (!appUserRepository.existsByEmailIgnoreCase("approver-a@demo.test")) {
            Role role = roleRepository.findByOrganizationId(orgA.getId()).stream()
                    .filter(r -> "Client Approver".equals(r.getName())).findFirst().orElseThrow();
            appUserRepository.save(new AppUser("approver-a@demo.test", passwordEncoder.encode("client123"),
                    "Client A Approver", Responsibility.CLIENT, role, role.getOrganization()));
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

    @Test
    void commentsAndAttachments_enforceVisibilityTenantScopeAndValidationLimits() throws Exception {
        Cookie clientACookie = login("client-a@demo.test", "client123");
        Cookie clientBCookie = login("client-b@demo.test", "client123");
        Cookie internalCookie = login("admin@ticketflow1.demo", "admin123");
        String ticketKey = createTicket(clientACookie, """
                {
                  "type":"TASK",
                  "title":"Phase 4 integration",
                  "description":"Comment and attachment boundaries",
                  "priority":"MEDIUM"
                }
                """, "SUBMITTED");

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey)
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"Visible to both parties","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey)
                        .cookie(internalCookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"Internal investigation detail","visibility":"INTERNAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("INTERNAL"));

        mockMvc.perform(get("/api/tickets/{ticketKey}/comments", ticketKey).cookie(clientACookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("Visible to both parties"));

        mockMvc.perform(get("/api/tickets/{ticketKey}/comments", ticketKey).cookie(internalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].body").value("Internal investigation detail"));

        mockMvc.perform(get("/api/tickets/{ticketKey}/comments", ticketKey).cookie(clientBCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey)
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"Not allowed","visibility":"INTERNAL"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey)
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"   ","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/attachments", ticketKey)
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"screen.png","contentType":"image/png","sizeBytes":245678}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("screen.png"));

        mockMvc.perform(get("/api/tickets/{ticketKey}/attachments", ticketKey).cookie(clientBCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/attachments", ticketKey)
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"bad.bin","contentType":"not-a-mime","sizeBytes":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/attachments", ticketKey)
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"too-large.zip","contentType":"application/zip","sizeBytes":104857601}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void commentAuditFeedIsPrivacySafe_andAuditFailureRollsBackComment() throws Exception {
        Cookie clientCookie = login("client-a@demo.test", "client123");
        Cookie internalCookie = login("admin@ticketflow1.demo", "admin123");
        String ticketKey = createTicket(clientCookie, """
                {
                  "type":"TASK",
                  "title":"Audit privacy test",
                  "description":"Internal events must not leak",
                  "priority":"MEDIUM"
                }
                """, "SUBMITTED");

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey)
                        .cookie(clientCookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"Public audit comment","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey)
                        .cookie(internalCookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"Sensitive internal body","visibility":"INTERNAL"}
                                """))
                .andExpect(status().isCreated());

        MvcResult clientAudit = mockMvc.perform(get("/api/tickets/{ticketKey}/audit-log", ticketKey)
                        .cookie(clientCookie))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(countActions(clientAudit, "COMMENT_ADDED")).isEqualTo(1);
        assertThat(clientAudit.getResponse().getContentAsString())
                .doesNotContain("Sensitive internal body", "INTERNAL");

        MvcResult internalAudit = mockMvc.perform(get("/api/tickets/{ticketKey}/audit-log", ticketKey)
                        .cookie(internalCookie))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(countActions(internalAudit, "COMMENT_ADDED")).isEqualTo(2);
        assertThat(internalAudit.getResponse().getContentAsString()).doesNotContain("Sensitive internal body");

        Integer beforeCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM comment c
                JOIN ticket t ON t.id = c.ticket_id
                WHERE t.ticket_key = ?
                """, Integer.class, ticketKey);
        jdbcTemplate.execute("""
                ALTER TABLE audit_log ADD CONSTRAINT test_reject_comment_audit
                CHECK (action <> 'COMMENT_ADDED') NOT VALID
                """);
        try {
            mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey)
                            .cookie(clientCookie)
                            .contentType("application/json")
                            .content("""
                                    {"body":"Must roll back","visibility":"PUBLIC"}
                                    """))
                    .andExpect(status().isInternalServerError());
        } finally {
            jdbcTemplate.execute("ALTER TABLE audit_log DROP CONSTRAINT test_reject_comment_audit");
        }
        Integer afterCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM comment c
                JOIN ticket t ON t.id = c.ticket_id
                WHERE t.ticket_key = ?
                """, Integer.class, ticketKey);
        assertThat(afterCount).isEqualTo(beforeCount);
    }

    @Test
    void latestProposalQueryUsesIdAsDeterministicTieBreaker() throws Exception {
        Cookie clientCookie = login("client-a@demo.test", "client123");
        String ticketKey = createTicket(clientCookie, """
                {
                  "type":"CHANGE_REQUEST",
                  "title":"Proposal ordering",
                  "description":"Latest must be deterministic",
                  "priority":"MEDIUM"
                }
                """, "SUBMITTED");
        Long ticketId = jdbcTemplate.queryForObject(
                "SELECT id FROM ticket WHERE ticket_key = ?", Long.class, ticketKey);
        Long creatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = 'admin@ticketflow1.demo'", Long.class);
        String insert = """
                INSERT INTO change_proposal
                    (ticket_id, description, status, created_by_id, decided_by_id, decided_at,
                     created_at, updated_at, version)
                VALUES (?, ?, 'REJECTED', ?, ?, now(), '2026-07-13T10:00:00Z', now(), 0)
                RETURNING id
                """;
        Long firstId = jdbcTemplate.queryForObject(insert, Long.class,
                ticketId, "First proposal", creatorId, creatorId);
        Long secondId = jdbcTemplate.queryForObject(insert, Long.class,
                ticketId, "Second proposal", creatorId, creatorId);

        var latest = changeProposalRepository.findFirstByTicketIdOrderByCreatedAtDescIdDesc(ticketId)
                .orElseThrow();

        assertThat(secondId).isGreaterThan(firstId);
        assertThat(latest.getId()).isEqualTo(secondId);
        assertThat(latest.getDescription()).isEqualTo("Second proposal");
    }

    @Test
    void protectedProposalCreateAndApproveCommandsDriveWorkflow() throws Exception {
        Cookie client = login("client-a@demo.test", "client123");
        Cookie internal = login("admin@ticketflow1.demo", "admin123");
        Cookie approver = login("approver-a@demo.test", "client123");
        String ticketKey = createTicket(client, """
                {"type":"CHANGE_REQUEST","title":"Protected proposal","description":"Command test","priority":"MEDIUM"}
                """, "SUBMITTED");
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", ticketKey).cookie(internal)
                        .contentType("application/json").content("{\"toStatus\":\"ANALYSIS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", ticketKey).cookie(internal)
                        .contentType("application/json").content("{\"toStatus\":\"PROPOSAL\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ILLEGAL_TRANSITION"));

        MvcResult created = mockMvc.perform(post("/api/tickets/{ticketKey}/proposals", ticketKey).cookie(internal)
                        .contentType("application/json").content("""
                                {"description":"Deliver protected change","effortEstimate":"5 person-days"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        long proposalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROPOSAL"));

        mockMvc.perform(post("/api/proposals/{id}/approve", proposalId).cookie(approver)
                        .contentType("application/json").content("{\"comment\":\"Approved\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROPOSAL_APPROVED"));
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

    private long countActions(MvcResult result, String action) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        long count = 0;
        for (JsonNode entry : body) {
            if (action.equals(entry.path("action").asText())) {
                count++;
            }
        }
        return count;
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
