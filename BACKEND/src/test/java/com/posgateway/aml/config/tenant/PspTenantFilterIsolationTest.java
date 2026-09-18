package com.posgateway.aml.config.tenant;

import com.posgateway.aml.config.RlsContextHolder;
import com.posgateway.aml.entity.TransactionEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Proves the Hibernate {@code pspTenantFilter} hides cross-tenant rows on {@code findById}
 * even without an explicit {@code WHERE psp_id = ?} in application code.
 */
class PspTenantFilterIsolationTest {

    private static SessionFactory sessionFactory;

    @BeforeAll
    static void bootHibernate() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.URL, "jdbc:h2:mem:pspfilter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
                .applySetting(AvailableSettings.DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .applySetting(AvailableSettings.SHOW_SQL, "false")
                .build();

        MetadataSources sources = new MetadataSources(registry);
        sources.addPackage("com.posgateway.aml.config.tenant");
        sources.addAnnotatedClass(TransactionEntity.class);
        sessionFactory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterEach
    void clearContext() {
        RlsContextHolder.clear();
    }

    @Test
    void hqlLoadCrossTenantRowIsHiddenWhenFilterEnabled() {
        Long foreignTxnId;
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            TransactionEntity foreign = new TransactionEntity();
            foreign.setPspId(2L);
            foreign.setMerchantId("M-FOREIGN");
            foreign.setCreatedAt(LocalDateTime.now());
            foreign.setTxnTs(LocalDateTime.now());
            foreignTxnId = (Long) session.save(foreign);
            session.getTransaction().commit();
        }

        RlsContextHolder.setCurrentPspId(1L);
        try (Session session = sessionFactory.openSession()) {
            PspTenantFilterSupport.enable(session);
            TransactionEntity loaded = session.createQuery(
                            "select t from TransactionEntity t where t.txnId = :id", TransactionEntity.class)
                    .setParameter("id", foreignTxnId)
                    .uniqueResult();
            assertNull(loaded, "PSP 1 must not load PSP 2 transaction when filter is enabled");
        }
    }

    @Test
    void hqlLoadSameTenantRowIsVisibleWhenFilterEnabled() {
        Long ownTxnId;
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            TransactionEntity own = new TransactionEntity();
            own.setPspId(1L);
            own.setMerchantId("M-OWN");
            own.setCreatedAt(LocalDateTime.now());
            own.setTxnTs(LocalDateTime.now());
            ownTxnId = (Long) session.save(own);
            session.getTransaction().commit();
        }

        RlsContextHolder.setCurrentPspId(1L);
        try (Session session = sessionFactory.openSession()) {
            PspTenantFilterSupport.enable(session);
            TransactionEntity loaded = session.createQuery(
                            "select t from TransactionEntity t where t.txnId = :id", TransactionEntity.class)
                    .setParameter("id", ownTxnId)
                    .uniqueResult();
            assertNotNull(loaded);
        }
    }
}
