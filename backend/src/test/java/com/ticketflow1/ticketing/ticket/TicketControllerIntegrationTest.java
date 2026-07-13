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
        mockMvc.perform(post("/api/proposals/{id}/approve", proposalId).cookie(approverB)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/proposals/{id}/reject", proposalId).cookie(approverA)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", ticketKey).cookie(approverA)
                        .contentType("application/json").content("{\"toStatus\":\"PROPOSAL_APPROVED\"}"))
                .andExpect(status().isConflict());

        Integer commentsBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM comment c JOIN ticket t ON t.id=c.ticket_id WHERE t.ticket_key=?", Integer.class, ticketKey);
        jdbcTemplate.execute("ALTER TABLE audit_log ADD CONSTRAINT test_reject_decision_audit CHECK (action <> 'PROPOSAL_REJECTED') NOT VALID");
        try {
            mockMvc.perform(post("/api/proposals/{id}/reject", proposalId).cookie(approverA)
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
        mockMvc.perform(post("/api/proposals/{id}/reject", rejectedId).cookie(approver)
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
        mockMvc.perform(post("/api/proposals/{id}/approve", approvedId).cookie(approver)
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

        mockMvc.perform(post("/api/admin/workflows").cookie(internal).contentType("application/json").content("""
                {"name":"Invalid workflow","organizationId":%d,"states":[{"key":"OPEN","isInitial":false,"isTerminal":false,"sortOrder":1}],"transitions":[]}
                """.formatted(orgA))).andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/api/admin/workflows").cookie(internal)
                        .contentType("application/json").content("""
                {"name":"Access workflow","organizationId":%d,
                 "states":[{"key":"OPEN","isInitial":true,"isTerminal":false,"sortOrder":1},{"key":"CLOSED","isInitial":false,"isTerminal":true,"sortOrder":2}],
                 "transitions":[{"fromState":"OPEN","toState":"CLOSED","requiredPermission":"TICKET_TRANSITION","requiredParty":"TICKETFLOW1","operationKind":"STANDARD"}]}
                """.formatted(orgA))).andExpect(status().isCreated()).andReturn();
        JsonNode workflow = objectMapper.readTree(created.getResponse().getContentAsString());
        long workflowId = workflow.path("id").asLong();
        long version = workflow.path("version").asLong();

        mockMvc.perform(post("/api/admin/ticket-types").cookie(internal).contentType("application/json").content("""
                {"key":"ACCESS_REQUEST","name":"Access Request","workflowId":%d,"organizationId":%d,"requiresProposal":false}
                """.formatted(workflowId, orgA))).andExpect(status().isCreated());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/workflows/{id}", workflowId).cookie(internal).contentType("application/json")
                        .content("{\"version\":999,\"transitions\":[]}"))
                .andExpect(status().isConflict());
        MvcResult updated = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/workflows/{id}", workflowId).cookie(internal).contentType("application/json")
                        .content("""
                {"version":%d,"states":[{"key":"REVIEW","isInitial":false,"isTerminal":false,"sortOrder":2}],
                 "transitions":[{"fromState":"OPEN","toState":"REVIEW","requiredPermission":"TICKET_TRANSITION","operationKind":"STANDARD"},{"fromState":"REVIEW","toState":"CLOSED","requiredPermission":"TICKET_TRANSITION","operationKind":"STANDARD"}]}
                """.formatted(version))).andExpect(status().isOk())
                .andExpect(jsonPath("$.states.length()").value(3)).andReturn();
        long reorderedVersion = objectMapper.readTree(updated.getResponse().getContentAsString()).path("version").asLong();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/workflows/{id}", workflowId).cookie(internal).contentType("application/json")
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
        mockMvc.perform(post("/api/admin/workflows").cookie(clientAdmin).contentType("application/json").content("""
                {"name":"Cross org","organizationId":%d,"states":[{"key":"OPEN","isInitial":true,"isTerminal":false,"sortOrder":1},{"key":"CLOSED","isInitial":false,"isTerminal":true,"sortOrder":2}],"transitions":[]}
                """.formatted(orgB))).andExpect(status().isNotFound());
    }

    private void transition(String key, String to, Cookie cookie, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mockMvc.perform(post("/api/tickets/{ticketKey}/transition", key).cookie(cookie)
                .contentType("application/json").content("{\"toStatus\":\"" + to + "\"}")).andExpect(expected);
    }

    private MvcResult createProposal(String key, Cookie cookie, String description,
            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        return mockMvc.perform(post("/api/tickets/{ticketKey}/proposals", key).cookie(cookie)
                .contentType("application/json").content("{\"description\":\"" + description + "\"}"))
                .andExpect(expected).andReturn();
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
