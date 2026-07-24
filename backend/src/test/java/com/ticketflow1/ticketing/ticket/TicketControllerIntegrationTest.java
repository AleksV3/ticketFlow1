package com.ticketflow1.ticketing.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void ensureClientUsersExist() {
        ensureInternalAdminExists();
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
        if (!appUserRepository.existsByEmailIgnoreCase("approver-b@demo.test")) {
            Role role = roleRepository.findByOrganizationId(orgB.getId()).stream()
                    .filter(r -> "Client Approver".equals(r.getName())).findFirst().orElseThrow();
            appUserRepository.save(new AppUser("approver-b@demo.test", passwordEncoder.encode("client123"),
                    "Client B Approver", Responsibility.CLIENT, role, role.getOrganization()));
        }
    }

    private void ensureInternalAdminExists() {
        if (!appUserRepository.existsByEmailIgnoreCase("admin@ticketflow1.demo")) {
            Role role = roleRepository.findAll().stream()
                    .filter(r -> "Admin".equals(r.getName()) && r.getOrganization() == null)
                    .findFirst()
                    .orElseThrow();
            appUserRepository.save(new AppUser("admin@ticketflow1.demo", passwordEncoder.encode("admin123"),
                    "Test Admin", Responsibility.TICKETFLOW1, role, null));
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

        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", allowedTicketKey).with(csrf())
                        .cookie(internalCookie)
                        .contentType("application/json")
                        .content("""
                                {
                                  "toStatus":"ANALYSIS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANALYSIS"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", forbiddenTicketKey).with(csrf())
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

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey).with(csrf())
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"Visible to both parties","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey).with(csrf())
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

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey).with(csrf())
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"Not allowed","visibility":"INTERNAL"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey).with(csrf())
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"   ","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/attachments", ticketKey).with(csrf())
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

        mockMvc.perform(post("/api/tickets/{ticketKey}/attachments", ticketKey).with(csrf())
                        .cookie(clientACookie)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"bad.bin","contentType":"not-a-mime","sizeBytes":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/tickets/{ticketKey}/attachments", ticketKey).with(csrf())
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

        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey).with(csrf())
                        .cookie(clientCookie)
                        .contentType("application/json")
                        .content("""
                                {"body":"Public audit comment","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey).with(csrf())
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
            mockMvc.perform(post("/api/tickets/{ticketKey}/comments", ticketKey).with(csrf())
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
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", ticketKey).with(csrf()).cookie(internal)
                        .contentType("application/json").content("{\"toStatus\":\"ANALYSIS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", ticketKey).with(csrf()).cookie(internal)
                        .contentType("application/json").content("{\"toStatus\":\"PROPOSAL\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ILLEGAL_TRANSITION"));

        MvcResult created = mockMvc.perform(post("/api/tickets/{ticketKey}/proposals", ticketKey).with(csrf()).cookie(internal)
                        .contentType("application/json").content("""
                                {"description":"Deliver protected change","effortEstimate":"5 person-days"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        long proposalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROPOSAL"));

        mockMvc.perform(post("/api/proposals/{id}/approve", proposalId).with(csrf()).cookie(approver)
                        .contentType("application/json").content("{\"comment\":\"Approved\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROPOSAL_APPROVED"));
    }

    @Test
    void proposalPermissionsIsolationDuplicateAndRollbackBoundaries() throws Exception {
        Cookie client = login("client-a@demo.test", "client123");
        Cookie internal = login("admin@ticketflow1.demo", "admin123");
        Cookie approverA = login("approver-a@demo.test", "client123");
        Cookie approverB = login("approver-b@demo.test", "client123");
        String ticketKey = createTicket(client, """
                {"type":"CHANGE_REQUEST","title":"Proposal boundaries","description":"Boundary test","priority":"MEDIUM"}
                """, "SUBMITTED");
        transition(ticketKey, "ANALYSIS", internal, status().isOk());

        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(internal))
                .andExpect(jsonPath("$.proposalCommands[0]").value("CREATE"))
                .andExpect(jsonPath("$.allowedTransitions").isArray());
        MvcResult created = createProposal(ticketKey, internal, "Boundary proposal", status().isCreated());
        long proposalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        createProposal(ticketKey, internal, "Duplicate", status().isConflict());
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(approverA))
                .andExpect(jsonPath("$.proposalCommands[0]").value("APPROVE"))
                .andExpect(jsonPath("$.proposalCommands[1]").value("REJECT"))
                .andExpect(jsonPath("$.latestProposal.id").value(proposalId));
        mockMvc.perform(post("/api/proposals/{id}/approve", proposalId).with(csrf()).cookie(approverB)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/proposals/{id}/reject", proposalId).with(csrf()).cookie(approverA)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", ticketKey).with(csrf()).cookie(approverA)
                        .contentType("application/json").content("{\"toStatus\":\"PROPOSAL_APPROVED\"}"))
                .andExpect(status().isConflict());

        Integer commentsBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM comment c JOIN ticket t ON t.id=c.ticket_id WHERE t.ticket_key=?", Integer.class, ticketKey);
        jdbcTemplate.execute("ALTER TABLE audit_log ADD CONSTRAINT test_reject_decision_audit CHECK (action <> 'PROPOSAL_REJECTED') NOT VALID");
        try {
            mockMvc.perform(post("/api/proposals/{id}/reject", proposalId).with(csrf()).cookie(approverA)
                            .contentType("application/json").content("{\"comment\":\"Must also roll back\"}"))
                    .andExpect(status().isInternalServerError());
        } finally { jdbcTemplate.execute("ALTER TABLE audit_log DROP CONSTRAINT test_reject_decision_audit"); }
        assertThat(changeProposalRepository.findById(proposalId).orElseThrow().getStatus().name()).isEqualTo("PENDING");
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(client))
                .andExpect(jsonPath("$.status").value("PROPOSAL"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM comment c JOIN ticket t ON t.id=c.ticket_id WHERE t.ticket_key=?", Integer.class, ticketKey)).isEqualTo(commentsBefore);

        String taskKey = createTicket(client, """
                {"type":"TASK","title":"No proposal","description":"Task rejection","priority":"MEDIUM"}
                """, "SUBMITTED");
        transition(taskKey, "ANALYSIS", internal, status().isOk());
        createProposal(taskKey, internal, "Invalid task proposal", status().isConflict());

        String defectKey = createTicket(client, """
                {"type":"DEFECT","title":"No defect proposal","description":"Defect rejection","priority":"HIGH","severity":"SEV_3"}
                """, "REPORTED");
        transition(defectKey, "ANALYSIS", internal, status().isOk());
        createProposal(defectKey, internal, "Invalid defect proposal", status().isConflict());

        String rollbackKey = createTicket(client, """
                {"type":"CHANGE_REQUEST","title":"Rollback proposal","description":"Atomic rollback","priority":"MEDIUM"}
                """, "SUBMITTED");
        transition(rollbackKey, "ANALYSIS", internal, status().isOk());
        Integer historyBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM status_history h JOIN ticket t ON t.id=h.ticket_id WHERE t.ticket_key=?", Integer.class, rollbackKey);
        jdbcTemplate.execute("ALTER TABLE audit_log ADD CONSTRAINT test_reject_proposal_audit CHECK (action <> 'PROPOSAL_CREATED') NOT VALID");
        try { createProposal(rollbackKey, internal, "Must roll back", status().isInternalServerError()); }
        finally { jdbcTemplate.execute("ALTER TABLE audit_log DROP CONSTRAINT test_reject_proposal_audit"); }
        mockMvc.perform(get("/api/tickets/{ticketKey}", rollbackKey).cookie(internal))
                .andExpect(jsonPath("$.status").value("ANALYSIS"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM change_proposal p JOIN ticket t ON t.id=p.ticket_id WHERE t.ticket_key=?", Integer.class, rollbackKey)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM status_history h JOIN ticket t ON t.id=h.ticket_id WHERE t.ticket_key=?", Integer.class, rollbackKey)).isEqualTo(historyBefore);
    }

    @Test
    void rejectedProposalCanBeResubmittedAndApprovedWithCompleteEvidence() throws Exception {
        Cookie client = login("client-a@demo.test", "client123");
        Cookie internal = login("admin@ticketflow1.demo", "admin123");
        Cookie approver = login("approver-a@demo.test", "client123");
        String ticketKey = createTicket(client, """
                {"type":"CHANGE_REQUEST","title":"Proposal lifecycle","description":"Phase 5 verification","priority":"MEDIUM"}
                """, "SUBMITTED");
        transition(ticketKey, "ANALYSIS", internal, status().isOk());

        long rejectedId = objectMapper.readTree(createProposal(
                ticketKey, internal, "First proposal", status().isCreated())
                .getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/proposals/{id}/reject", rejectedId).with(csrf()).cookie(approver)
                        .contentType("application/json")
                        .content("{\"comment\":\"Please reduce the delivery risk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        transition(ticketKey, "ANALYSIS", internal, status().isOk());
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(internal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANALYSIS"))
                .andExpect(jsonPath("$.latestProposal.id").value(rejectedId))
                .andExpect(jsonPath("$.latestProposal.status").value("REJECTED"))
                .andExpect(jsonPath("$.proposalCommands[0]").value("CREATE"));

        long approvedId = objectMapper.readTree(createProposal(
                ticketKey, internal, "Safer revised proposal", status().isCreated())
                .getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/proposals/{id}/approve", approvedId).with(csrf()).cookie(approver)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(approvedId).isGreaterThan(rejectedId);
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSAL_APPROVED"))
                .andExpect(jsonPath("$.latestProposal.id").value(approvedId))
                .andExpect(jsonPath("$.latestProposal.status").value("APPROVED"))
                .andExpect(jsonPath("$.proposalCommands").isEmpty());
        mockMvc.perform(get("/api/tickets/{ticketKey}/comments", ticketKey).cookie(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Please reduce the delivery risk"))
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"));

        MvcResult audit = mockMvc.perform(get("/api/tickets/{ticketKey}/audit-log", ticketKey)
                        .cookie(internal))
                .andExpect(status().isOk()).andReturn();
        assertThat(countActions(audit, "PROPOSAL_CREATED")).isEqualTo(2);
        assertThat(countActions(audit, "PROPOSAL_REJECTED")).isEqualTo(1);
        assertThat(countActions(audit, "PROPOSAL_APPROVED")).isEqualTo(1);

        mockMvc.perform(get("/api/tickets/{ticketKey}/status-history", ticketKey).cookie(internal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].toStatus").value("ANALYSIS"))
                .andExpect(jsonPath("$[2].toStatus").value("PROPOSAL"))
                .andExpect(jsonPath("$[3].toStatus").value("PROPOSAL_REJECTED"))
                .andExpect(jsonPath("$[4].toStatus").value("ANALYSIS"))
                .andExpect(jsonPath("$[5].toStatus").value("PROPOSAL"))
                .andExpect(jsonPath("$[6].toStatus").value("PROPOSAL_APPROVED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM change_proposal p JOIN ticket t ON t.id=p.ticket_id WHERE t.ticket_key=?",
                Integer.class, ticketKey)).isEqualTo(2);
    }

    @Test
    void concurrentProposalDecisionsUseOptimisticLocking() throws Exception {
        Cookie client = login("client-a@demo.test", "client123");
        Cookie internal = login("admin@ticketflow1.demo", "admin123");
        String key = createTicket(client, """
                {"type":"CHANGE_REQUEST","title":"Concurrent proposal","description":"Race","priority":"MEDIUM"}
                """, "SUBMITTED");
        transition(key, "ANALYSIS", internal, status().isOk());
        long id = objectMapper.readTree(createProposal(key, internal, "Race proposal", status().isCreated())
                .getResponse().getContentAsString()).get("id").asLong();
        Long actorId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE email='approver-a@demo.test'", Long.class);
        CountDownLatch loaded = new CountDownLatch(2); CountDownLatch release = new CountDownLatch(1);
        java.util.function.Supplier<Boolean> decision = () -> {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    var em = entityManagerFactory.createEntityManager();
                    try {
                        em.joinTransaction();
                        var proposal = em.find(com.ticketflow1.ticketing.proposal.ChangeProposal.class, id);
                        var actor = em.getReference(AppUser.class, actorId);
                        loaded.countDown();
                        try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
                        proposal.decide(com.ticketflow1.ticketing.proposal.ProposalStatus.APPROVED, actor, java.time.Instant.now());
                        em.flush();
                    } finally { em.close(); }
                });
                return true;
            } catch (RuntimeException ex) { return false; }
        };
        var first = CompletableFuture.supplyAsync(decision); var second = CompletableFuture.supplyAsync(decision);
        assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue(); release.countDown();
        assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void slaFiltersArePaginatedTenantScopedAndConsistentWithDetail() throws Exception {
        Cookie clientA = login("client-a@demo.test", "client123");
        Cookie clientB = login("client-b@demo.test", "client123");
        String breachedA = createTicket(clientA, """
                {"type":"DEFECT","title":"Breached A","description":"SLA filter","priority":"HIGH","severity":"SEV_1"}
                """, "REPORTED");
        String breachedA2 = createTicket(clientA, """
                {"type":"DEFECT","title":"Breached A2","description":"SLA pagination","priority":"HIGH","severity":"SEV_1"}
                """, "REPORTED");
        String dueSoonA = createTicket(clientA, """
                {"type":"DEFECT","title":"Due soon A","description":"SLA consistency","priority":"HIGH","severity":"SEV_1"}
                """, "REPORTED");
        String breachedB = createTicket(clientB, """
                {"type":"DEFECT","title":"Breached B","description":"Other tenant","priority":"HIGH","severity":"SEV_1"}
                """, "REPORTED");

        jdbcTemplate.update("UPDATE ticket SET response_due_at=now()-interval '1 minute', first_info_due_at=now()+interval '1 hour', next_update_due_at=NULL WHERE ticket_key IN (?, ?, ?)",
                breachedA, breachedA2, breachedB);
        jdbcTemplate.update("UPDATE ticket SET response_due_at=now()+interval '2 minutes', first_info_due_at=now()+interval '1 hour', next_update_due_at=NULL WHERE ticket_key=?",
                dueSoonA);

        mockMvc.perform(get("/api/tickets").param("slaStatus", "BREACHED")
                        .param("pageSize", "1").cookie(clientA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].slaStatus").value("BREACHED"));
        mockMvc.perform(get("/api/tickets").param("slaStatus", "DUE_SOON").cookie(clientA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].ticketKey").value(dueSoonA))
                .andExpect(jsonPath("$.items[0].slaStatus").value("DUE_SOON"));
        mockMvc.perform(get("/api/tickets/{ticketKey}", dueSoonA).cookie(clientA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sla.status").value("DUE_SOON"));
    }

    @Test
    void dashboardCountsAndSlaCardsStayTenantScopedAndAgreeWithTicketApis() throws Exception {
        Cookie clientA = login("client-a@demo.test", "client123");
        Cookie clientB = login("client-b@demo.test", "client123");
        Cookie internal = login("admin@ticketflow1.demo", "admin123");
        JsonNode baseline = objectMapper.readTree(mockMvc.perform(get("/api/dashboard").cookie(clientA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String activeTask = createTicket(clientA, """
                {"type":"TASK","title":"Dashboard active","description":"Active count","priority":"MEDIUM"}
                """, "SUBMITTED");
        String closedTask = createTicket(clientA, """
                {"type":"TASK","title":"Dashboard closed","description":"Terminal metadata","priority":"MEDIUM"}
                """, "SUBMITTED");
        String otherTenant = createTicket(clientB, """
                {"type":"TASK","title":"Dashboard hidden","description":"Tenant isolation","priority":"MEDIUM"}
                """, "SUBMITTED");
        String breached = createTicket(clientA, """
                {"type":"DEFECT","title":"Dashboard breached","description":"SLA agreement","priority":"HIGH","severity":"SEV_1"}
                """, "REPORTED");
        String dueSoon = createTicket(clientA, """
                {"type":"DEFECT","title":"Dashboard due soon","description":"SLA agreement","priority":"HIGH","severity":"SEV_1"}
                """, "REPORTED");
        String ok = createTicket(clientA, """
                {"type":"DEFECT","title":"Dashboard ok","description":"SLA agreement","priority":"HIGH","severity":"SEV_1"}
                """, "REPORTED");

        jdbcTemplate.update("""
                UPDATE ticket t SET current_state_id = (
                    SELECT ws.id FROM workflow_state ws
                    WHERE ws.workflow_id=ticket_type.workflow_id AND ws.is_terminal=true LIMIT 1
                ), closed_at=now()
                FROM ticket_type WHERE t.ticket_type_id=ticket_type.id AND t.ticket_key=?
                """, closedTask);
        jdbcTemplate.update("UPDATE ticket SET ticket_lead_id=(SELECT id FROM app_user WHERE email='admin@ticketflow1.demo') WHERE ticket_key=?",
                activeTask);
        jdbcTemplate.update("UPDATE ticket SET response_due_at=now()-interval '1 minute', first_info_due_at=now()+interval '1 hour', next_update_due_at=NULL WHERE ticket_key=?",
                breached);
        jdbcTemplate.update("UPDATE ticket SET response_due_at=now()+interval '2 minutes', first_info_due_at=now()+interval '1 hour', next_update_due_at=NULL WHERE ticket_key=?",
                dueSoon);
        jdbcTemplate.update("UPDATE ticket SET response_due_at=now()+interval '10 minutes', first_info_due_at=now()+interval '30 minutes', next_update_due_at=now()+interval '1 hour' WHERE ticket_key=?",
                ok);

        JsonNode dashboard = objectMapper.readTree(mockMvc.perform(get("/api/dashboard").cookie(clientA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(dashboard.path("activeCount").asLong()).isEqualTo(baseline.path("activeCount").asLong() + 4);
        assertThat(dashboard.path("closedCount").asLong()).isEqualTo(baseline.path("closedCount").asLong() + 1);
        assertThat(dashboard.path("byType").path("TASK").asLong())
                .isEqualTo(baseline.path("byType").path("TASK").asLong() + 2);
        assertThat(dashboard.path("byType").path("DEFECT").asLong())
                .isEqualTo(baseline.path("byType").path("DEFECT").asLong() + 3);
        assertThat(containsTicket(dashboard.path("slaBreached"), breached)).isTrue();
        assertThat(containsTicket(dashboard.path("slaDueSoon"), dueSoon)).isTrue();
        assertThat(containsTicket(dashboard.path("slaBreached"), otherTenant)).isFalse();
        assertThat(dashboard.path("myAssignedTickets").isEmpty()).isTrue();

        JsonNode internalDashboard = objectMapper.readTree(mockMvc.perform(get("/api/dashboard").cookie(internal))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(containsTicket(internalDashboard.path("myAssignedTickets"), activeTask)).isTrue();

        assertSlaAgreement(clientA, breached, "BREACHED");
        assertSlaAgreement(clientA, dueSoon, "DUE_SOON");
        assertSlaAgreement(clientA, ok, "OK");
    }

    @Test
    void adminWorkflowMutationsValidateGraphScopeOptimisticVersionAndAudit() throws Exception {
        Cookie internal = login("admin@ticketflow1.demo", "admin123");
        Long orgA = jdbcTemplate.queryForObject("SELECT id FROM organization WHERE name='Client A'", Long.class);
        Long orgB = jdbcTemplate.queryForObject("SELECT id FROM organization WHERE name='Client B'", Long.class);

        mockMvc.perform(post("/api/admin/workflows").with(csrf()).cookie(internal).contentType("application/json").content("""
                {"name":"Invalid workflow","organizationId":%d,"states":[{"key":"OPEN","isInitial":false,"isTerminal":false,"sortOrder":1}],"transitions":[]}
                """.formatted(orgA))).andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/api/admin/workflows").with(csrf()).cookie(internal)
                        .contentType("application/json").content("""
                {"name":"Access workflow","organizationId":%d,
                 "states":[{"key":"OPEN","isInitial":true,"isTerminal":false,"sortOrder":1},{"key":"CLOSED","isInitial":false,"isTerminal":true,"sortOrder":2}],
                 "transitions":[{"fromState":"OPEN","toState":"CLOSED","requiredPermission":"TICKET_TRANSITION","requiredParty":"TICKETFLOW1","operationKind":"STANDARD"}]}
                """.formatted(orgA))).andExpect(status().isCreated()).andReturn();
        JsonNode workflow = objectMapper.readTree(created.getResponse().getContentAsString());
        long workflowId = workflow.path("id").asLong();
        long version = workflow.path("version").asLong();

        mockMvc.perform(post("/api/admin/ticket-types").with(csrf()).cookie(internal).contentType("application/json").content("""
                {"key":"ACCESS_REQUEST","name":"Access Request","workflowId":%d,"organizationId":%d,"requiresProposal":false}
                """.formatted(workflowId, orgA))).andExpect(status().isCreated());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/workflows/{id}", workflowId).with(csrf()).cookie(internal).contentType("application/json")
                        .content("{\"version\":999,\"transitions\":[]}"))
                .andExpect(status().isConflict());
        MvcResult updated = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/workflows/{id}", workflowId).with(csrf()).cookie(internal).contentType("application/json")
                        .content("""
                {"version":%d,"states":[{"key":"REVIEW","isInitial":false,"isTerminal":false,"sortOrder":2}],
                 "transitions":[{"fromState":"OPEN","toState":"REVIEW","requiredPermission":"TICKET_TRANSITION","operationKind":"STANDARD"},{"fromState":"REVIEW","toState":"CLOSED","requiredPermission":"TICKET_TRANSITION","operationKind":"STANDARD"}]}
                """.formatted(version))).andExpect(status().isOk())
                .andExpect(jsonPath("$.states.length()").value(3)).andReturn();
        long reorderedVersion = objectMapper.readTree(updated.getResponse().getContentAsString()).path("version").asLong();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/workflows/{id}", workflowId).with(csrf()).cookie(internal).contentType("application/json")
                        .content("""
                {"version":%d,"states":[
                  {"key":"CLOSED","isInitial":false,"isTerminal":true,"sortOrder":0},
                  {"key":"REVIEW","isInitial":false,"isTerminal":false,"sortOrder":1},
                  {"key":"OPEN","isInitial":true,"isTerminal":false,"sortOrder":2}]}
                """.formatted(reorderedVersion))).andExpect(status().isOk())
                .andExpect(jsonPath("$.states[0].key").value("CLOSED"))
                .andExpect(jsonPath("$.states[1].key").value("REVIEW"))
                .andExpect(jsonPath("$.states[2].key").value("OPEN"));
        mockMvc.perform(get("/api/admin/configuration-audit").cookie(internal).param("organizationId", orgA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        jdbcTemplate.update("""
                INSERT INTO role_permission(role_id,permission_id)
                SELECT r.id,p.id FROM role r JOIN permission p ON p.key IN ('TYPE_MANAGE','WORKFLOW_MANAGE')
                WHERE r.organization_id=? AND r.name='Client User' ON CONFLICT DO NOTHING
                """, orgA);
        Cookie clientAdmin = login("client-a@demo.test", "client123");
        mockMvc.perform(post("/api/admin/workflows").with(csrf()).cookie(clientAdmin).contentType("application/json").content("""
                {"name":"Cross org","organizationId":%d,"states":[{"key":"OPEN","isInitial":true,"isTerminal":false,"sortOrder":1},{"key":"CLOSED","isInitial":false,"isTerminal":true,"sortOrder":2}],"transitions":[]}
                """.formatted(orgB))).andExpect(status().isNotFound());
    }

    private void transition(String key, String to, Cookie cookie, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", key).with(csrf()).cookie(cookie)
                .contentType("application/json").content("{\"toStatus\":\"" + to + "\"}")).andExpect(expected);
    }

    private MvcResult createProposal(String key, Cookie cookie, String description,
            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        return mockMvc.perform(post("/api/tickets/{ticketKey}/proposals", key).with(csrf()).cookie(cookie)
                .contentType("application/json").content("{\"description\":\"" + description + "\"}"))
                .andExpect(expected).andReturn();
    }

    @Test
    void targetUserAutocompleteIsBoundedAndTenantSafe() throws Exception {
        Cookie clientA=login("client-a@demo.test","client123");
        Cookie internal=login("admin@ticketflow1.demo","admin123");
        Long orgA=organizationRepository.findAll().stream().filter(o->"Client A".equals(o.getName())).findFirst().orElseThrow().getId();
        Long orgB=organizationRepository.findAll().stream().filter(o->"Client B".equals(o.getName())).findFirst().orElseThrow().getId();

        mockMvc.perform(get("/api/reference/users").cookie(clientA).param("q","c").param("purpose","USR_TARGET"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reference/users").cookie(clientA).param("q","Client").param("purpose","USR_TARGET").param("organizationId",orgB.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reference/users").cookie(clientA).param("q","Client").param("purpose","USR_TARGET").param("organizationId",orgA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].displayName").exists())
                .andExpect(jsonPath("$[?(@.displayName == 'Client B')]").isEmpty());
        mockMvc.perform(get("/api/reference/users").cookie(internal).param("q","Client").param("purpose","USR_TARGET"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void configurationCanBeBuiltThroughApisAndRejectsCrossScopeIds() throws Exception {
        Cookie admin=login("admin@ticketflow1.demo","admin123");
        Organization orgA=organizationRepository.findAll().stream().filter(o->"Client A".equals(o.getName())).findFirst().orElseThrow();
        Organization orgB=organizationRepository.findAll().stream().filter(o->"Client B".equals(o.getName())).findFirst().orElseThrow();
        Long typeId=jdbcTemplate.queryForObject("select id from ticket_type where organization_id=? order by id limit 1",Long.class,orgA.getId());
        Long adminId=appUserRepository.findByEmail("admin@ticketflow1.demo").orElseThrow().getId();

        JsonNode team=json(mockMvc.perform(post("/api/teams").with(csrf()).cookie(admin).contentType("application/json").content("""
                {"name":"Configuration Test Team","description":"routing verification","leaderId":%d,"memberIds":[%d],"ticketKeys":[]}
                """.formatted(adminId,adminId))).andExpect(status().isOk()).andReturn());
        Long teamId=team.path("id").asLong();

        JsonNode subtype=json(mockMvc.perform(post("/api/admin/ticket-types/{id}/subtypes",typeId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"key":"API_VERIFY","name":"API verification","description":"created without schema changes","sortOrder":900}
                """)).andExpect(status().isCreated()).andReturn());
        Long subtypeId=subtype.path("id").asLong();

        JsonNode updatedSubtype=json(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/subtypes/{id}",subtypeId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"version":%d,"name":"API verification renamed","description":"updated","sortOrder":910}
                """.formatted(subtype.path("version").asLong()))).andExpect(status().isOk()).andReturn());

        JsonNode field=json(mockMvc.perform(post("/api/admin/subtypes/{id}/fields",subtypeId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"key":"environment","label":"Environment","helpText":"Choose environment","fieldKind":"SINGLE_SELECT","required":true,"visibility":"PUBLIC","sortOrder":0}
                """)).andExpect(status().isCreated()).andReturn());
        Long fieldId=field.path("id").asLong();
        JsonNode updatedField=json(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/fields/{id}",fieldId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"version":%d,"label":"Target environment","helpText":"Choose environment","required":true,"visibility":"PUBLIC","sortOrder":0}
                """.formatted(field.path("version").asLong()))).andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/admin/fields/{id}/deactivate",fieldId).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/fields/{id}/activate",fieldId).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/subtypes/{id}/fields/order",subtypeId).with(csrf()).cookie(admin).contentType("application/json").content("{\"ids\":[%d]}".formatted(fieldId))).andExpect(status().isNoContent());
        JsonNode option=json(mockMvc.perform(post("/api/admin/fields/{id}/options",fieldId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"key":"PRODUCTION","label":"Production","sortOrder":0}
                """)).andExpect(status().isCreated()).andReturn());
        Long optionId=option.path("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/field-options/{id}",optionId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"version":%d,"label":"Production system","sortOrder":0}
                """.formatted(option.path("version").asLong()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/field-options/{id}/deactivate",optionId).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/field-options/{id}/activate",optionId).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/fields/{id}/options/order",fieldId).with(csrf()).cookie(admin).contentType("application/json").content("{\"ids\":[%d]}".formatted(optionId))).andExpect(status().isNoContent());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/subtypes/{id}/routing",subtypeId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"teamId":%d,"primaryDeveloperId":%d,"approverId":%d,"active":true}
                """.formatted(teamId,adminId,adminId))).andExpect(status().isOk()).andExpect(jsonPath("$.teamId").value(teamId));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/subtypes/{id}/routing",subtypeId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"organizationId":%d,"teamId":%d,"active":true}
                """.formatted(orgB.getId(),teamId))).andExpect(status().isNotFound());

        JsonNode creationForm=json(mockMvc.perform(get("/api/reference/ticket-types/{id}/creation-form",typeId).cookie(admin))
                .andExpect(status().isOk()).andReturn());
        JsonNode configuredSubtype=java.util.stream.StreamSupport.stream(creationForm.path("subtypes").spliterator(),false)
                .filter(item->item.path("id").asLong()==subtypeId).findFirst().orElseThrow();
        assertThat(configuredSubtype.path("name").asText()).isEqualTo("API verification renamed");
        assertThat(configuredSubtype.path("fields").get(0).path("options").get(0).path("key").asText()).isEqualTo("PRODUCTION");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/subtypes/{id}",subtypeId).with(csrf()).cookie(admin))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/fields/999999/options").with(csrf()).cookie(admin).contentType("application/json").content("{\"key\":\"BAD\",\"label\":\"Bad\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select count(*) from configuration_audit_log where target_type in ('TICKET_SUBTYPE','SUBTYPE_FIELD','FIELD_OPTION','SUBTYPE_ROUTING') and actor_id=?",Long.class,adminId)).isGreaterThanOrEqualTo(6L);
        assertThat(updatedSubtype.path("version").asLong()).isGreaterThan(subtype.path("version").asLong());
        assertThat(updatedField.path("version").asLong()).isGreaterThan(field.path("version").asLong());

        Long workflowId=jdbcTemplate.queryForObject("select workflow_id from ticket_type where id=?",Long.class,typeId);
        JsonNode customType=json(mockMvc.perform(post("/api/admin/ticket-types").with(csrf()).cookie(admin).contentType("application/json").content("""
                {"key":"API_TYPE","name":"API Type","workflowId":%d,"organizationId":%d,"active":true,"sortOrder":990,"capability":"STANDARD"}
                """.formatted(workflowId,orgA.getId()))).andExpect(status().isCreated()).andReturn());
        Long customTypeId=customType.path("id").asLong();
        JsonNode renamedType=json(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/ticket-types/{id}",customTypeId).with(csrf()).cookie(admin).contentType("application/json").content("""
                {"version":%d,"name":"API Type Renamed","workflowId":%d,"active":true,"sortOrder":991,"capability":"STANDARD"}
                """.formatted(customType.path("version").asLong(),workflowId))).andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/admin/ticket-types/{id}/deactivate",customTypeId).with(csrf()).cookie(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/ticket-types/{id}/activate",customTypeId).with(csrf()).cookie(admin)).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/ticket-types/{id}",customTypeId).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        assertThat(renamedType.path("name").asText()).isEqualTo("API Type Renamed");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    private void assertSlaAgreement(Cookie cookie, String ticketKey, String expected) throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketKey}", ticketKey).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sla.status").value(expected));
        MvcResult list = mockMvc.perform(get("/api/tickets").param("slaStatus", expected).cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        assertThat(containsTicket(objectMapper.readTree(list.getResponse().getContentAsString()).path("items"), ticketKey))
                .isTrue();
    }

    private boolean containsTicket(JsonNode tickets, String ticketKey) {
        for (JsonNode ticket : tickets) {
            if (ticketKey.equals(ticket.path("ticketKey").asText())) {
                return true;
            }
        }
        return false;
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

    @Test
    void dynamicCreationValidatesAndAuditDoesNotExposeSubmittedValue() throws Exception {
        Cookie admin = login("admin@ticketflow1.demo", "admin123");
        Long orgId = organizationRepository.findAll().stream().filter(o -> "Client A".equals(o.getName())).findFirst().orElseThrow().getId();
        Long typeId = jdbcTemplate.queryForObject("select id from ticket_type where organization_id=? order by id limit 1", Long.class, orgId);
        JsonNode subtype = json(mockMvc.perform(post("/api/admin/ticket-types/{id}/subtypes", typeId).with(csrf()).cookie(admin)
                .contentType("application/json").content("{\"key\":\"DYNAMIC_AUDIT\",\"name\":\"Dynamic audit\"}"))
                .andExpect(status().isCreated()).andReturn());
        JsonNode field = json(mockMvc.perform(post("/api/admin/subtypes/{id}/fields", subtype.path("id").asLong()).with(csrf()).cookie(admin)
                .contentType("application/json").content("{\"key\":\"secret\",\"label\":\"Secret\",\"fieldKind\":\"SHORT_TEXT\",\"required\":true,\"visibility\":\"INTERNAL\"}"))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/tickets").with(csrf()).cookie(admin).contentType("application/json").content("""
                {"type":"CHANGE_REQUEST","organizationId":%d,"title":"Dynamic test","description":"Dynamic test","priority":"MEDIUM","subtypeId":%d,"dynamicValues":{"secret":"do-not-leak"}}
                """.formatted(orgId, subtype.path("id").asLong())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.dynamicValues.secret").value("do-not-leak"));
        mockMvc.perform(post("/api/tickets").with(csrf()).cookie(admin).contentType("application/json").content("""
                {"type":"CHANGE_REQUEST","organizationId":%d,"title":"Bad dynamic","description":"Bad dynamic","priority":"MEDIUM","subtypeId":%d,"dynamicValues":{"unknown":"x"}}
                """.formatted(orgId, subtype.path("id").asLong())))
                .andExpect(status().isBadRequest());
        String key = jdbcTemplate.queryForObject("select ticket_key from ticket where title='Dynamic test' order by id desc limit 1", String.class);
        mockMvc.perform(get("/api/tickets/{key}/audit-log", key).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'DYNAMIC_FIELDS_CAPTURED' && @.newValue == 'do-not-leak')]").isEmpty());
    }

    @Test
    void serviceRequestTypesSubtypesAndChildTicket_areCreatedWithTenantAuditContext() throws Exception {
        Cookie admin = login("admin@ticketflow1.demo", "admin123");
        Long orgId = jdbcTemplate.queryForObject("select id from organization where name='Client A'", Long.class);
        Long tasiSubtype = jdbcTemplate.queryForObject("""
                select s.id from ticket_subtype s join ticket_type t on t.id=s.ticket_type_id
                where t.organization_id=? and t.key='TASI' and s.key='FIREWALL'
                """, Long.class, orgId);
        Long usrNewSubtype = jdbcTemplate.queryForObject("""
                select s.id from ticket_subtype s join ticket_type t on t.id=s.ticket_type_id
                where t.organization_id=? and t.key='USR' and s.key='NEW'
                """, Long.class, orgId);
        Long usrModifySubtype = jdbcTemplate.queryForObject("""
                select s.id from ticket_subtype s join ticket_type t on t.id=s.ticket_type_id
                where t.organization_id=? and t.key='USR' and s.key='MODIFY'
                """, Long.class, orgId);
        Long targetUser = jdbcTemplate.queryForObject("select id from app_user where email='client-a@demo.test'", Long.class);

        String tasi = createTicketWithCsrf(admin, """
                {"type":"TASI","organizationId":%d,"subtypeId":%d,"title":"Firewall action","description":"Inspect firewall rule","priority":"HIGH"}
                """.formatted(orgId, tasiSubtype), "NEW");
        String usrNew = createTicketWithCsrf(admin, """
                {"type":"USR","organizationId":%d,"subtypeId":%d,"title":"New user","description":"Provision user","priority":"MEDIUM"}
                """.formatted(orgId, usrNewSubtype), "NEW");
        String usrModify = createTicketWithCsrf(admin, """
                {"type":"USR","organizationId":%d,"subtypeId":%d,"targetUserId":%d,"title":"Modify user","description":"Change access","priority":"MEDIUM"}
                """.formatted(orgId, usrModifySubtype, targetUser), "NEW");

        String child = createTicketWithCsrf(admin, """
                {"type":"TASI","organizationId":%d,"subtypeId":%d,"parentTicketKey":"%s","title":"Firewall follow-up","description":"Follow-up action","priority":"MEDIUM"}
                """.formatted(orgId, tasiSubtype, tasi), "NEW");

        mockMvc.perform(get("/api/tickets/{ticketKey}", child).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentTicketKey").value(tasi))
                .andExpect(jsonPath("$.subtype").value("FIREWALL"));
        mockMvc.perform(get("/api/tickets/{ticketKey}/audit-log", usrModify).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("TICKET_CREATED"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from ticket where ticket_key in (?, ?, ?, ?)",
                Long.class, tasi, usrNew, usrModify, child)).isEqualTo(4L);
    }

    @Test
    void allServiceRequestWorkflows_executeHappyPathsAndDecisionCommands() throws Exception {
        Cookie admin = login("admin@ticketflow1.demo", "admin123");
        Cookie client = login("client-a@demo.test", "client123");
        Long orgId = jdbcTemplate.queryForObject("select id from organization where name='Client A'", Long.class);
        Long adminId = jdbcTemplate.queryForObject("select id from app_user where email='admin@ticketflow1.demo'", Long.class);
        Long firewall = jdbcTemplate.queryForObject("""
                select s.id from ticket_subtype s join ticket_type t on t.id=s.ticket_type_id
                where t.organization_id=? and t.key='TASI' and s.key='FIREWALL'""", Long.class, orgId);
        Long modify = jdbcTemplate.queryForObject("""
                select s.id from ticket_subtype s join ticket_type t on t.id=s.ticket_type_id
                where t.organization_id=? and t.key='USR' and s.key='MODIFY'""", Long.class, orgId);
        Long target = jdbcTemplate.queryForObject("select id from app_user where email='client-a@demo.test'", Long.class);

        String tasi = createTicketWithCsrf(admin, """
                {"type":"TASI","organizationId":%d,"subtypeId":%d,"title":"TASI path","description":"TASI","priority":"MEDIUM"}
                """.formatted(orgId, firewall), "NEW");
        String usr = createTicketWithCsrf(admin, """
                {"type":"USR","organizationId":%d,"subtypeId":%d,"targetUserId":%d,"title":"USR path","description":"USR","priority":"MEDIUM"}
                """.formatted(orgId, modify, target), "NEW");
        jdbcTemplate.update("update ticket set resolved_approver_id=? where ticket_key in (?,?)", adminId, tasi, usr);

        transitionWithCsrf(tasi, "ANALYSIS", admin);
        transitionWithCsrf(tasi, "PENDING_APPROVAL", admin);
        decide(tasi, "workflow-approve", admin, null);
        transitionWithCsrf(tasi, "CLOSED", admin);
        assertStatus(tasi, "CLOSED", admin);

        transitionWithCsrf(usr, "ANALYSIS", admin);
        transitionWithCsrf(usr, "PENDING_APPROVAL", admin);
        decide(usr, "workflow-reject", admin, "Needs more information");
        assertStatus(usr, "ANALYSIS", admin);

        String defect = createTicketWithCsrf(client, """
                {"type":"DFCT","title":"DFCT path","description":"Defect","priority":"HIGH","severity":"SEV_3"}
                """, "REPORTED");
        transitionWithCsrf(defect, "ANALYSIS", admin);
        transitionWithCsrf(defect, "FIX_IN_PROGRESS", admin);
        transitionWithCsrf(defect, "CLIENT_CONFIRMATION", admin);
        transitionWithCsrf(defect, "FIX_IN_PROGRESS", client);
        transitionWithCsrf(defect, "CLIENT_CONFIRMATION", admin);
        transitionWithCsrf(defect, "CLOSED", client);

        String request = createTicketWithCsrf(client, """
                {"type":"REQ","title":"REQ path","description":"Request","priority":"MEDIUM"}
                """, "SUBMITTED");
        transitionWithCsrf(request, "ANALYSIS", admin);
        transitionWithCsrf(request, "CLIENT_ACCEPTANCE", admin);
        decide(request, "client-accept", client, null);
        transitionWithCsrf(request, "CLOSED", admin);
        assertStatus(request, "CLOSED", admin);
    }

    @Test
    void tasiApproval_enforcesActorCommandsEvidenceStaleStateAndRollback() throws Exception {
        jdbcTemplate.update("update app_user set password_hash=?, active=true where email=?",
                passwordEncoder.encode("manager123"), "test.manager@ticketflow1.app");
        Cookie admin = login("admin@ticketflow1.demo", "admin123");
        Cookie manager = login("test.manager@ticketflow1.app", "manager123");
        Cookie client = login("client-a@demo.test", "client123");
        Long internalOrgId = jdbcTemplate.queryForObject(
                "select id from organization where name='TicketFlow1 Internal'", Long.class);
        Long firewallSubtypeId = jdbcTemplate.queryForObject("""
                select subtype.id
                from ticket_subtype subtype
                join ticket_type type on type.id=subtype.ticket_type_id
                where type.organization_id=? and type.key='TASI' and subtype.key='FIREWALL'
                """, Long.class, internalOrgId);

        String approved = createInternalFirewallTicket(admin, internalOrgId, firewallSubtypeId, "Approval matrix");
        transitionWithCsrf(approved, "ANALYSIS", admin);
        transitionWithCsrf(approved, "PENDING_APPROVAL", admin);

        mockMvc.perform(get("/api/tickets/{ticketKey}", approved).cookie(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowCommands").isArray())
                .andExpect(jsonPath("$.workflowCommands").value(
                        org.hamcrest.Matchers.containsInAnyOrder("WORKFLOW_APPROVE", "WORKFLOW_REJECT")));
        mockMvc.perform(get("/api/tickets/{ticketKey}", approved).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowCommands").isEmpty());
        mockMvc.perform(post("/api/tickets/{ticketKey}/workflow-approve", approved)
                        .with(csrf()).cookie(admin).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(get("/api/tickets/{ticketKey}", approved).cookie(client))
                .andExpect(status().isNotFound());

        decide(approved, "workflow-approve", manager, "Ready for implementation");
        assertStatus(approved, "IMPLEMENTATION", manager);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from ticket_approval approval
                join ticket ticket on ticket.id=approval.ticket_id
                where ticket.ticket_key=? and approval.status='APPROVED'
                """, Long.class, approved)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from ticket_decision decision
                join ticket ticket on ticket.id=decision.ticket_id
                where ticket.ticket_key=? and decision.decision='APPROVED'
                """, Long.class, approved)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_log audit
                join ticket ticket on ticket.id=audit.ticket_id
                where ticket.ticket_key=? and audit.action='WORKFLOW_APPROVED'
                """, Long.class, approved)).isEqualTo(1L);
        mockMvc.perform(post("/api/tickets/{ticketKey}/workflow-approve", approved)
                        .with(csrf()).cookie(manager).contentType("application/json").content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        String inactive = createInternalFirewallTicket(admin, internalOrgId, firewallSubtypeId, "Inactive approver");
        transitionWithCsrf(inactive, "ANALYSIS", admin);
        transitionWithCsrf(inactive, "PENDING_APPROVAL", admin);
        jdbcTemplate.update("update app_user set active=false where email='test.manager@ticketflow1.app'");
        mockMvc.perform(get("/api/tickets/{ticketKey}", inactive).cookie(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowCommands").isEmpty());
        mockMvc.perform(post("/api/tickets/{ticketKey}/workflow-approve", inactive)
                        .with(csrf()).cookie(manager).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        jdbcTemplate.update("update app_user set active=true where email='test.manager@ticketflow1.app'");

        String rejected = createInternalFirewallTicket(admin, internalOrgId, firewallSubtypeId, "Rejected approval");
        transitionWithCsrf(rejected, "ANALYSIS", admin);
        transitionWithCsrf(rejected, "PENDING_APPROVAL", admin);
        decide(rejected, "workflow-reject", manager, "Needs a safer rollback plan");
        assertStatus(rejected, "ANALYSIS", manager);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from ticket_decision decision
                join ticket ticket on ticket.id=decision.ticket_id
                where ticket.ticket_key=? and decision.decision='REJECTED'
                  and decision.reason='Needs a safer rollback plan'
                """, Long.class, rejected)).isEqualTo(1L);

        String rollback = createInternalFirewallTicket(admin, internalOrgId, firewallSubtypeId, "Rollback approval");
        transitionWithCsrf(rollback, "ANALYSIS", admin);
        transitionWithCsrf(rollback, "PENDING_APPROVAL", admin);
        jdbcTemplate.execute("""
                alter table audit_log add constraint test_workflow_approval_rollback
                check (action <> 'WORKFLOW_APPROVED') not valid
                """);
        try {
            mockMvc.perform(post("/api/tickets/{ticketKey}/workflow-approve", rollback)
                            .with(csrf()).cookie(manager).contentType("application/json").content("{}"))
                    .andExpect(status().isInternalServerError());
        } finally {
            jdbcTemplate.execute(
                    "alter table audit_log drop constraint test_workflow_approval_rollback");
        }
        assertStatus(rollback, "PENDING_APPROVAL", manager);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from ticket_decision decision
                join ticket ticket on ticket.id=decision.ticket_id
                where ticket.ticket_key=?
                """, Long.class, rollback)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from ticket_approval approval
                join ticket ticket on ticket.id=approval.ticket_id
                where ticket.ticket_key=? and approval.status='PENDING'
                """, Long.class, rollback)).isEqualTo(1L);
    }

    private String createInternalFirewallTicket(Cookie admin, Long organizationId,
            Long subtypeId, String title) throws Exception {
        return createTicketWithCsrf(admin, """
                {
                  "type":"TASI",
                  "organizationId":%d,
                  "subtypeId":%d,
                  "title":"%s",
                  "description":"Verify protected TASI approval",
                  "priority":"HIGH",
                  "dynamicValues":{
                    "source_cidr":"10.20.0.0/24",
                    "destination":"payroll.internal",
                    "service_ports":"TCP/443",
                    "environment":"PRODUCTION",
                    "business_justification":"Required for the approval regression test"
                  }
                }
                """.formatted(organizationId, subtypeId, title), "NEW");
    }

    private void transitionWithCsrf(String key, String state, Cookie cookie) throws Exception {
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", key).with(csrf()).cookie(cookie)
                        .contentType("application/json").content("{\"toStatus\":\"" + state + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state));
    }

    private void decide(String key, String command, Cookie cookie, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        mockMvc.perform(post("/api/tickets/{ticketKey}/" + command, key).with(csrf()).cookie(cookie)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    private void assertStatus(String key, String state, Cookie cookie) throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketKey}", key).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state));
    }

    private String createTicketWithCsrf(Cookie authCookie, String body, String expectedInitialState) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tickets").with(csrf()).cookie(authCookie)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(expectedInitialState)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("ticketKey").asText();
    }

    private String createTicket(Cookie authCookie, String body, String expectedInitialState) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tickets").with(csrf())
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
