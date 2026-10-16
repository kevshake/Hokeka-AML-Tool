package com.posgateway.aml.integration;

import org.flywaydb.core.Flyway;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.TruthValue;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.hibernate.tool.schema.extract.spi.ColumnInformation;
import org.hibernate.tool.schema.extract.spi.TableInformation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression guard for the production schema contract: applies the full Flyway history to an empty
 * PostgreSQL database and then boots Hibernate over every {@code @Entity} (including the Envers
 * {@code *_aud} tables and the revision entity) with {@code hbm2ddl.auto=validate} — exactly what the
 * {@code production} profile does at startup.
 *
 * <p>Database: by default a Testcontainers {@code postgres:16-alpine}. To run against an existing
 * empty database instead (e.g. when the Docker API is not reachable from Testcontainers), pass
 * {@code -Dschema.it.jdbc-url=jdbc:postgresql://127.0.0.1:55434/aml -Dschema.it.user=postgres
 * -Dschema.it.password=...}.
 *
 * <p>Before Hibernate's own validator (which stops at the first problem) the test computes a full drift
 * report — every missing table/column/sequence and type mismatch — so a single run tells you
 * everything the next migration needs.
 */
class ProductionSchemaValidationIntegrationTest {

    private static PostgreSQLContainer<?> container;
    private static String jdbcUrl;
    private static String user;
    private static String password;

    /** Resolves the target database: explicit system properties first, otherwise Testcontainers. */
    @BeforeAll
    @SuppressWarnings("resource")
    static void startDatabase() {
        jdbcUrl = System.getProperty("schema.it.jdbc-url");
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            user = System.getProperty("schema.it.user", "postgres");
            password = System.getProperty("schema.it.password", "");
            return;
        }
        // No explicit database and no Docker for Testcontainers: skip rather than fail (CI has Docker).
        org.junit.jupiter.api.Assumptions.assumeTrue(
                org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                "Docker not available to Testcontainers; pass -Dschema.it.jdbc-url to run against a database");
        container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        jdbcUrl = container.getJdbcUrl();
        user = container.getUsername();
        password = container.getPassword();
    }

    /** Stops the container if this class started one. */
    @AfterAll
    static void stopDatabase() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void flywaySchemaPassesHibernateValidationIncludingEnvers() throws Exception {
        Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .placeholderReplacement(false)
                .load()
                .migrate();

        List<String> drift = schemaDrift();
        if (!drift.isEmpty()) {
            fail(drift.size() + " entity/schema mismatches:\n" + String.join("\n", drift));
        }
        try {
            buildEntityManagerFactory("validate", null).destroy();
        } catch (Exception validationFailure) {
            fail("Hibernate schema validation failed: " + rootMessage(validationFailure));
        }

        try (Connection c = dataSource().getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT is_identity FROM information_schema.columns "
                             + "WHERE table_name = 'revinfo' AND column_name = 'rev'")) {
            assertTrue(rs.next(), "revinfo.rev must exist");
            assertEquals("YES", rs.getString(1), "revinfo.rev must stay an identity column");
        }

        // V253: the V127 demo accounts (published password) must be disabled with an unusable hash.
        try (Connection c = dataSource().getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT COUNT(*) FILTER (WHERE enabled OR password_hash LIKE '$2%'), COUNT(*) "
                             + "FROM platform_users WHERE username IN ('super.admin','platform.admin',"
                             + "'velocity.admin','velocity.compliance','velocity.user','mwananchi.admin',"
                             + "'mwananchi.compliance','apex.admin')")) {
            assertTrue(rs.next());
            assertEquals(8, rs.getInt(2), "V127 seeds eight demo accounts on a fresh database");
            assertEquals(0, rs.getInt(1), "no V127 demo account may stay enabled or keep a BCrypt hash");
        }
    }

    /**
     * An audited write must create a revinfo row through the identity column (no revinfo_seq) and
     * the matching *_aud row — this is the path that returned 503 on a fresh production database.
     */
    @Test
    void auditedWriteCreatesRevisionWithoutSequence() throws Exception {
        Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .placeholderReplacement(false)
                .load()
                .migrate();
        LocalContainerEntityManagerFactoryBean emf = buildEntityManagerFactory("validate", null);
        Long id;
        try {
            jakarta.persistence.EntityManager em = emf.getNativeEntityManagerFactory().createEntityManager();
            try {
                em.getTransaction().begin();
                com.posgateway.aml.entity.compliance.ComplianceDeadline d =
                        new com.posgateway.aml.entity.compliance.ComplianceDeadline();
                d.setDeadlineType("SAR_FILING");
                d.setDeadlineDate(java.time.LocalDateTime.now().plusDays(3));
                d.setCompleted(false);
                d.setCreatedAt(java.time.LocalDateTime.now());
                em.persist(d);
                em.getTransaction().commit();
                id = d.getId();
            } finally {
                em.close();
            }
        } finally {
            emf.destroy();
        }
        try (Connection c = dataSource().getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT a.rev, r.revtstmp FROM compliance_deadlines_aud a JOIN revinfo r ON r.rev = a.rev "
                             + "WHERE a.id = " + id)) {
            assertTrue(rs.next(), "audited insert must produce a compliance_deadlines_aud row linked to revinfo");
            assertTrue(rs.getLong(2) > 0, "revision timestamp must be recorded");
        }
    }

    /**
     * R8 / V264: a SAR draft's transaction references (SarTransaction, Envers-audited, PK id+rev) and
     * a case's transaction link persist by ID only and are audited with their reference columns.
     */
    @Test
    void auditedTransactionReferencesPersistByIdOnly() throws Exception {
        Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .placeholderReplacement(false)
                .load()
                .migrate();
        java.util.UUID edgeId = java.util.UUID.randomUUID();
        Long sarId;
        Long linkId;
        LocalContainerEntityManagerFactoryBean emf = buildEntityManagerFactory("validate", null);
        try {
            jakarta.persistence.EntityManager em = emf.getNativeEntityManagerFactory().createEntityManager();
            try {
                em.getTransaction().begin();
                com.posgateway.aml.entity.compliance.ComplianceCase c = new com.posgateway.aml.entity.compliance.ComplianceCase();
                c.setCaseReference("CASE-R8-" + edgeId.toString().substring(0, 8));
                c.setPspId(1L);
                em.persist(c);
                com.posgateway.aml.model.TransactionReference ref =
                        com.posgateway.aml.model.TransactionReference.ofDecisionRecord(edgeId, "AUTH-R8", "site-r8");
                com.posgateway.aml.entity.compliance.CaseTransaction link =
                        new com.posgateway.aml.entity.compliance.CaseTransaction(c, ref, "PRIMARY", null);
                em.persist(link);
                com.posgateway.aml.entity.compliance.SuspiciousActivityReport sar =
                        new com.posgateway.aml.entity.compliance.SuspiciousActivityReport();
                sar.setSarReference("SAR-R8-" + edgeId);
                sar.setPspId(1L);
                sar.setJurisdiction("KE");
                sar.setNarrative("refs only");
                sar.setSuspiciousActivityType("STRUCTURING");
                sar.setComplianceCase(c);
                sar.setTransactionReferences(java.util.List.of(ref));
                em.persist(sar);
                em.getTransaction().commit();
                sarId = sar.getId();
                linkId = link.getId();
            } finally {
                em.close();
            }
        } finally {
            emf.destroy();
        }
        try (Connection c = dataSource().getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT st.txn_id, st.txn_ref, a.edge_assessment_id, a.site_id FROM sar_transactions st "
                             + "JOIN sar_transactions_aud a ON a.id = st.id WHERE st.sar_id = " + sarId)) {
            assertTrue(rs.next(), "SAR reference row and its audit row");
            assertEquals(null, rs.getObject(1), "no legacy transaction id for an Edge decision record");
            assertEquals("AUTH-R8", rs.getString(2));
            assertEquals(edgeId, rs.getObject(3));
            assertEquals("site-r8", rs.getString(4));
        }
        try (Connection c = dataSource().getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT edge_assessment_id, transaction_id FROM case_transactions_aud WHERE id = " + linkId)) {
            assertTrue(rs.next(), "case link audited");
            assertEquals(edgeId, rs.getObject(1));
            assertEquals(null, rs.getObject(2));
        }
    }

    /** Builds a JPA EntityManagerFactory over all application entities with Spring Boot's naming rules. */
    private static LocalContainerEntityManagerFactoryBean buildEntityManagerFactory(String hbm2ddl,
                                                                                   Map<String, Object> extra) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource());
        emf.setPackagesToScan("com.posgateway.aml");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", hbm2ddl);
        props.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        props.put("hibernate.implicit_naming_strategy",
                "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        props.put("hibernate.envers.store_data_at_delete", "false");
        if (extra != null) {
            props.putAll(extra);
        }
        emf.setJpaPropertyMap(props);
        emf.afterPropertiesSet();
        return emf;
    }

    /**
     * Lists every missing table, missing column, column type mismatch and missing sequence between the
     * Hibernate boot metadata and the live database, using Hibernate's own type-matching rule
     * ({@code ColumnDefinitions.hasMatchingType}, package-private, called reflectively) so the result
     * agrees with {@code hbm2ddl.auto=validate}.
     */
    private static List<String> schemaDrift() throws Exception {
        AtomicReference<Metadata> metadataRef = new AtomicReference<>();
        Integrator capture = new Integrator() {
            @Override
            public void integrate(Metadata metadata, BootstrapContext bootstrapContext,
                                  SessionFactoryImplementor sessionFactory) {
                metadataRef.set(metadata);
            }

            @Override
            public void disintegrate(SessionFactoryImplementor sessionFactory,
                                     SessionFactoryServiceRegistry serviceRegistry) {
                // nothing to release: the captured Metadata is a plain object graph
            }
        };
        Map<String, Object> extra = new HashMap<>();
        extra.put("hibernate.integrator_provider", (IntegratorProvider) () -> List.of(capture));
        LocalContainerEntityManagerFactoryBean emf = buildEntityManagerFactory("none", extra);
        try {
            Metadata metadata = metadataRef.get();
            Dialect dialect = emf.getNativeEntityManagerFactory()
                    .unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect();
            Method hasMatchingType = Class.forName("org.hibernate.tool.schema.internal.ColumnDefinitions")
                    .getDeclaredMethod("hasMatchingType", Column.class, ColumnInformation.class,
                            Metadata.class, Dialect.class);
            hasMatchingType.setAccessible(true);

            List<String> problems = new ArrayList<>();
            try (Connection c = dataSource().getConnection()) {
                DatabaseMetaData md = c.getMetaData();
                for (Table table : metadata.collectTableMappings()) {
                    if (!table.isPhysicalTable()) {
                        continue;
                    }
                    String tableName = unquote(table.getName());
                    Map<String, ColumnInformation> dbColumns = new HashMap<>();
                    try (ResultSet rs = md.getColumns(null, "public", tableName, null)) {
                        while (rs.next()) {
                            dbColumns.put(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                                    jdbcColumn(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"),
                                            rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"),
                                            rs.getInt("DECIMAL_DIGITS")));
                        }
                    }
                    if (dbColumns.isEmpty()) {
                        problems.add("missing table " + tableName);
                        continue;
                    }
                    for (Column column : table.getColumns()) {
                        String colName = unquote(column.getName());
                        ColumnInformation info = dbColumns.get(colName);
                        if (info == null) {
                            problems.add("missing column " + tableName + "." + colName + " "
                                    + column.getSqlType(metadata));
                        } else if (!(Boolean) hasMatchingType.invoke(null, column, info, metadata, dialect)) {
                            problems.add("type mismatch " + tableName + "." + colName + ": db="
                                    + info.getTypeName() + " entity=" + column.getSqlType(metadata));
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM pg_class WHERE relkind = 'S' AND relname = ?")) {
                    for (Namespace ns : metadata.getDatabase().getNamespaces()) {
                        for (Sequence seq : ns.getSequences()) {
                            ps.setString(1, unquote(seq.getName().getSequenceName().getText()));
                            try (ResultSet rs = ps.executeQuery()) {
                                if (!rs.next()) {
                                    problems.add("missing sequence " + seq.getName().getSequenceName().getText());
                                }
                            }
                        }
                    }
                }
            }
            Collections.sort(problems);
            return problems;
        } finally {
            emf.destroy();
        }
    }

    /** Lower-cases and strips quoting, matching how PostgreSQL stores unquoted identifiers. */
    private static String unquote(String name) {
        return name.replace("\"", "").replace("`", "").toLowerCase(Locale.ROOT);
    }

    /** Minimal {@link ColumnInformation} view of one JDBC metadata row. */
    private static ColumnInformation jdbcColumn(String name, int typeCode, String typeName, int size, int digits) {
        return new ColumnInformation() {
            @Override public TableInformation getContainingTableInformation() { return null; }
            @Override public Identifier getColumnIdentifier() { return Identifier.toIdentifier(name); }
            @Override public TruthValue getNullable() { return TruthValue.UNKNOWN; }
            @Override public int getTypeCode() { return typeCode; }
            @Override public String getTypeName() { return typeName; }
            @Override public int getColumnSize() { return size; }
            @Override public int getDecimalDigits() { return digits; }
        };
    }

    /** Plain JDBC datasource for the target database. */
    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource(jdbcUrl, user, password);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    /** Innermost exception message, which carries Hibernate's "missing column ..." detail. */
    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }
}
