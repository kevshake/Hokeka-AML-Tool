package com.posgateway.aml.service.residency;

import com.fasterxml.jackson.databind.JsonNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.contracts.ContractFixtures;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.billing.CurrencyRate;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.EdgeLimit;
import com.posgateway.aml.entity.residency.EdgeMerchantBlocklistEntry;
import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.entity.sanctions.SanctionsList;
import com.posgateway.aml.repository.CurrencyRateRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.residency.EdgeLimitRepository;
import com.posgateway.aml.repository.residency.EdgeMerchantBlocklistRepository;
import com.posgateway.aml.repository.residency.PspEdgeSettingsRepository;
import com.posgateway.aml.repository.sanctions.SanctionsListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Edge config document built from cloud state conforms to edge-config.schema.json, sources each
 * section from its table, and gets a new configVersion exactly when its content changes.
 */
class EdgeConfigServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T08:00:00Z");

    private Psp psp;
    private PspDataSettingsService dataSettings;
    private PspEdgeSettingsRepository edgeSettings;
    private EdgeLimitRepository limits;
    private EdgeMerchantBlocklistRepository blocklist;
    private CurrencyRateRepository rates;
    private SanctionsListRepository sanctions;
    private ApplicationEventPublisher events;
    private EdgeConfigService service;
    private final Map<Long, List<String>> issued = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        psp = new Psp();
        psp.setPspId(12L);
        psp.setSupportAccessEnabled(false);
        psp.setSupportAccessChangedAt(Instant.parse("2026-10-01T00:00:00Z"));
        psp.setCrossPspOptIn(false);
        psp.setCrossPspOptInChangedAt(Instant.parse("2026-10-01T00:00:00Z"));
        PspRepository psps = mock(PspRepository.class);
        when(psps.findById(12L)).thenReturn(Optional.of(psp));
        dataSettings = mock(PspDataSettingsService.class);
        when(dataSettings.retentionState(12L)).thenReturn(
                new PspDataSettingsService.RetentionState(5, Instant.parse("2026-01-01T00:00:00Z"), null));
        edgeSettings = mock(PspEdgeSettingsRepository.class);
        when(edgeSettings.findById(12L)).thenReturn(Optional.empty());
        limits = mock(EdgeLimitRepository.class);
        when(limits.findByPspIdAndActiveTrueOrderByIdAsc(12L)).thenReturn(List.of());
        blocklist = mock(EdgeMerchantBlocklistRepository.class);
        when(blocklist.findByPspIdAndRemovedAtIsNullOrderByMerchantIdAsc(12L)).thenReturn(List.of());
        rates = mock(CurrencyRateRepository.class);
        when(rates.findAll()).thenReturn(List.of());
        sanctions = mock(SanctionsListRepository.class);
        when(sanctions.findFirstByOrderByDownloadedAtDescListIdDesc()).thenReturn(Optional.empty());

        EdgeConfigDocumentBuilder builder = new EdgeConfigDocumentBuilder(psps, dataSettings, edgeSettings, limits,
                blocklist, rates, sanctions, new BigDecimal("15000"), 72);
        EdgeConfigVersionStore versions = mock(EdgeConfigVersionStore.class);
        when(versions.ensureVersion(anyLong(), anyString())).thenAnswer(i -> {
            List<String> hashes = issued.computeIfAbsent(i.getArgument(0), k -> new ArrayList<>());
            String hash = i.getArgument(1);
            if (!hashes.isEmpty() && hashes.get(hashes.size() - 1).equals(hash)) {
                return new EdgeConfigVersionStore.VersionResult(hashes.size(), false);
            }
            hashes.add(hash);
            return new EdgeConfigVersionStore.VersionResult(hashes.size(), true);
        });
        events = mock(ApplicationEventPublisher.class);
        service = new EdgeConfigService(builder, versions, new EdgeControlPlaneKeys(new EdgeProperties()),
                new HokekaSecureEnvelope(), events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void defaultDocumentConformsToTheContractSchema() {
        JsonNode doc = service.current(12L).document();
        assertConforms(doc);
        assertEquals(1, doc.get("v").asInt());
        assertEquals(1, doc.get("configVersion").asLong());
        assertEquals(12, doc.get("pspId").asLong());
        assertEquals(5, doc.at("/retention/years").asInt());
        assertFalse(doc.at("/supportAccess/enabled").asBoolean());
        assertFalse(doc.at("/ctr/enabled").asBoolean(), "CTR stays off until configured (Appendix A.20)");
        assertEquals(1500000, doc.at("/ctr/thresholds/0/amountMinor").asLong(), "platform USD 15,000 threshold");
        assertEquals("1", doc.at("/ctr/fx/rates/USD").asText());
        assertEquals("none", doc.at("/sanctions/listVersion").asText());
        assertEquals(Set.copyOf(PspEdgeSettings.DEFAULT_TUNNEL_OPS), toSet(doc.at("/tunnel/opsAllowed")));
        assertFalse(doc.at("/tunnel/backfillAllowed").asBoolean());
        assertEquals(16, doc.at("/tunnel/maxInflightPerSession").asInt());
    }

    @Test
    void everySectionIsSourcedFromItsTable() {
        psp.setSupportAccessEnabled(true);
        psp.setCrossPspOptIn(true);
        when(dataSettings.retentionState(12L)).thenReturn(new PspDataSettingsService.RetentionState(10,
                Instant.parse("2026-01-01T00:00:00Z"),
                new PspDataSettingsService.PendingRetention(7, NOW, NOW.plusSeconds(30L * 86400))));
        PspEdgeSettings settings = PspEdgeSettings.defaultsFor(12L);
        settings.setCtrEnabled(true);
        settings.setCtrBusinessDayTimezone("Africa/Nairobi");
        settings.setCtrCashChannels(List.of("ATM", "CASH_IN", "AGENT"));
        settings.setTunnelBackfillAllowed(true);
        settings.setTunnelOpsAllowed(List.of("txn.search", "ledger.backfill"));
        when(edgeSettings.findById(12L)).thenReturn(Optional.of(settings));
        EdgeLimit amount = new EdgeLimit();
        amount.setId(2L);
        amount.setScope("MERCHANT");
        amount.setMetric("AMOUNT");
        amount.setWindowCode("P1D");
        amount.setCurrency("KES");
        amount.setMaxValue(5_000_000_000L);
        amount.setAction("ALERT");
        amount.setMerchantId("mrc_00421");
        when(limits.findByPspIdAndActiveTrueOrderByIdAsc(12L)).thenReturn(List.of(amount));
        EdgeMerchantBlocklistEntry block = new EdgeMerchantBlocklistEntry();
        block.setMerchantId("mrc_09917");
        block.setReasonCode("SANCTIONED");
        block.setSince(Instant.parse("2026-09-30T12:00:00Z"));
        when(blocklist.findByPspIdAndRemovedAtIsNullOrderByMerchantIdAsc(12L)).thenReturn(List.of(block));
        when(blocklist.version(12L)).thenReturn(3L);
        LocalDateTime localNow = LocalDateTime.ofInstant(NOW, ZoneId.systemDefault());
        when(rates.findAll()).thenReturn(List.of(
                rate("KES", "0.007700", true, localNow.minusHours(2)),
                rate("EUR", "1.085200", true, localNow.minusHours(3)),
                rate("GBP", "1.300000", false, localNow.minusHours(1)),     // not regulatory-approved
                rate("TZS", "0.000400", true, localNow.minusHours(100))));  // stale (> 72 h)
        SanctionsList list = new SanctionsList();
        list.setListId(4);
        list.setListName("OPENSANCTIONS_ALL");
        list.setListSource("OpenSanctions");
        list.setVersion("20261016050000-abc");
        list.setRecordCount(1200);
        list.setDownloadedAt(localNow.minusHours(3));
        when(sanctions.findFirstByOrderByDownloadedAtDescListIdDesc()).thenReturn(Optional.of(list));

        JsonNode doc = service.current(12L).document();
        assertConforms(doc);
        assertEquals(10, doc.at("/retention/years").asInt());
        assertEquals(7, doc.at("/retention/pendingChange/years").asInt());
        assertTrue(doc.at("/supportAccess/enabled").asBoolean());
        assertTrue(doc.at("/crossPspIntelligence/optIn").asBoolean());
        assertEquals("Africa/Nairobi", doc.at("/ctr/businessDayTimezone").asText());
        assertEquals("0.0077", doc.at("/ctr/fx/rates/KES").asText());
        assertEquals("1.0852", doc.at("/ctr/fx/rates/EUR").asText());
        assertTrue(doc.at("/ctr/fx/rates/GBP").isMissingNode(), "unapproved rates are not distributed");
        assertTrue(doc.at("/ctr/fx/rates/TZS").isMissingNode(), "stale rates are not distributed");
        assertEquals(2, doc.at("/limits/0/limitId").asLong());
        assertEquals("mrc_09917", doc.at("/merchantBlocklist/entries/0/merchantId").asText());
        assertEquals(3, doc.at("/merchantBlocklist/version").asLong());
        assertEquals("20261016050000-abc", doc.at("/sanctions/listVersion").asText());
        assertTrue(doc.at("/tunnel/backfillAllowed").asBoolean());
    }

    @Test
    void versionChangesExactlyWhenTheContentChanges() {
        long first = service.current(12L).version();
        assertEquals(first, service.current(12L).version(), "same inputs, same configVersion (ETag stable)");
        verify(events, times(1)).publishEvent(any(EdgeConfigUpdatedEvent.class));

        psp.setSupportAccessEnabled(true);
        psp.setSupportAccessChangedAt(NOW);
        long second = service.current(12L).version();
        assertEquals(first + 1, second, "support-access toggle bumps the version");
        verify(events, times(2)).publishEvent(any(EdgeConfigUpdatedEvent.class));

        when(dataSettings.retentionState(12L)).thenReturn(new PspDataSettingsService.RetentionState(5,
                Instant.parse("2026-01-01T00:00:00Z"),
                new PspDataSettingsService.PendingRetention(5, NOW, NOW.plusSeconds(86400))));
        assertEquals(second + 1, service.current(12L).version(), "a pending reduction is part of the content");
    }

    @Test
    void sealingRefusesAnotherPspsDocument() {
        EdgeConfigService.IssuedConfig issuedConfig = service.current(12L);
        com.posgateway.aml.entity.edge.EdgeNode foreign = new com.posgateway.aml.entity.edge.EdgeNode();
        foreign.setPspId(13L);
        foreign.setEdgeId("other-1");
        assertThrows(IllegalStateException.class, () -> service.seal(foreign, issuedConfig));
    }

    @Test
    void contractConfigFixturesAreSchemaValid() {
        for (String name : ContractFixtures.fixtureNames("edge-config--")) {
            Set<String> errors = ContractFixtures.validate("edge-config.schema.json", ContractFixtures.fixture(name));
            assertEquals(!name.endsWith(".invalid.json"), errors.isEmpty(), name + " " + errors);
        }
    }

    @Test
    void decimalStringsNeverUseExponentsOrBinaryFloatingPoint() {
        assertEquals("0.0077", EdgeConfigDocumentBuilder.decimalString(new BigDecimal("0.007700")));
        assertEquals("1", EdgeConfigDocumentBuilder.decimalString(new BigDecimal("1.000000")));
        assertEquals("0.0000001", EdgeConfigDocumentBuilder.decimalString(new BigDecimal("1E-7")));
        assertEquals("v1.2-x-y", EdgeConfigDocumentBuilder.listVersion("v1.2 x+y", NOW));
        assertEquals(64, EdgeConfigDocumentBuilder.listVersion("a".repeat(80), NOW).length());
    }

    private static CurrencyRate rate(String code, String usd, boolean approved, LocalDateTime effective) {
        CurrencyRate rate = new CurrencyRate();
        rate.setCurrencyCode(code);
        rate.setRateToUsd(new BigDecimal(usd));
        rate.setIsActive(true);
        rate.setRegulatoryApproved(approved);
        rate.setRateSource("CBK");
        rate.setEffectiveAt(effective);
        return rate;
    }

    private static Set<String> toSet(JsonNode array) {
        Set<String> out = new java.util.HashSet<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static void assertConforms(JsonNode doc) {
        Set<String> errors = ContractFixtures.validate("edge-config.schema.json", doc);
        assertTrue(errors.isEmpty(), "document conforms to edge-config.schema.json: " + errors + "\n" + doc);
    }
}
