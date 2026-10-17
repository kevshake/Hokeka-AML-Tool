package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.EdgeMerchantBlocklistEntry;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.residency.EdgeMerchantBlocklistRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.sanctions.SanctionsHitClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R10b: cloud merchant screening drives the Edge merchant blocklist. A confirmed sanctions hit adds a
 * SANCTIONED block (and a new config version through PspDataSettingsChangedEvent); a clear rescreen
 * lifts it; PEP-only matches are not sanctions hits; potential matches and outages change nothing;
 * manual blocks for other reasons are never lifted automatically.
 */
class MerchantSanctionsBlocklistServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

    private EdgeMerchantBlocklistRepository repository;
    private ApplicationEventPublisher events;
    private MerchantSanctionsBlocklistService service;
    private Merchant merchant;
    private final List<EdgeMerchantBlocklistEntry> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(EdgeMerchantBlocklistRepository.class);
        when(repository.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(repository.findFirstByPspIdAndMerchantIdAndRemovedAtIsNull(12L, "421")).thenReturn(Optional.empty());
        events = mock(ApplicationEventPublisher.class);
        service = new MerchantSanctionsBlocklistService(repository, mock(AuditLogService.class), events, 0.95,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Psp psp = new Psp();
        psp.setPspId(12L);
        merchant = new Merchant();
        merchant.setMerchantId(421L);
        merchant.setPsp(psp);
    }

    /** A screening result with one match. */
    private static ScreeningResult result(ScreeningResult.ScreeningStatus status, double score, String listName,
                                          String pepLevel) {
        ScreeningResult.Match match = new ScreeningResult.Match();
        match.setSimilarityScore(score);
        match.setListName(listName);
        match.setPepLevel(pepLevel);
        match.setMatchType(ScreeningResult.MatchType.NAME_MATCH);
        return ScreeningResult.builder().status(status).matches(List.of(match)).highestMatchScore(score).build();
    }

    /** A clean result. */
    private static ScreeningResult clear() {
        return ScreeningResult.builder().status(ScreeningResult.ScreeningStatus.CLEAR).matches(List.of()).build();
    }

    @Test
    void aConfirmedSanctionsHitBlocksTheMerchantOnItsPspsEdges() {
        assertEquals(MerchantSanctionsBlocklistService.Change.BLOCKED,
                service.apply(merchant, result(ScreeningResult.ScreeningStatus.MATCH, 0.97, "us_ofac_sdn", null)));
        assertEquals(1, saved.size());
        EdgeMerchantBlocklistEntry entry = saved.get(0);
        assertEquals(12L, entry.getPspId());
        assertEquals("421", entry.getMerchantId(), "the cloud merchant id the Edge sees as merchant_id");
        assertEquals("SANCTIONED", entry.getReasonCode());
        assertEquals(NOW, entry.getSince());
        ArgumentCaptor<PspDataSettingsChangedEvent> event = ArgumentCaptor.forClass(PspDataSettingsChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(12L, event.getValue().pspId());
        assertEquals("MERCHANT_BLOCKLIST", event.getValue().reason());
    }

    @Test
    void aPepOnlyMatchIsNotASanctionsHit() {
        ScreeningResult pep = result(ScreeningResult.ScreeningStatus.MATCH, 1.0, "peps", "PEP");
        assertEquals(SanctionsHitClassifier.Verdict.CLEAR, SanctionsHitClassifier.classify(pep, 0.95));
        assertEquals(SanctionsHitClassifier.Verdict.CLEAR, SanctionsHitClassifier.classify(
                result(ScreeningResult.ScreeningStatus.MATCH, 1.0, "EU_PEP_REGISTER", null), 0.95));
        assertEquals(MerchantSanctionsBlocklistService.Change.NONE, service.apply(merchant, pep));
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void potentialMatchesAndOutagesChangeNothing() {
        assertEquals(MerchantSanctionsBlocklistService.Change.NONE,
                service.apply(merchant, result(ScreeningResult.ScreeningStatus.POTENTIAL_MATCH, 0.86, "us_ofac_sdn", null)));
        assertEquals(MerchantSanctionsBlocklistService.Change.NONE, service.apply(merchant,
                ScreeningResult.builder().status(ScreeningResult.ScreeningStatus.UNAVAILABLE).build()));
        assertEquals(MerchantSanctionsBlocklistService.Change.NONE, service.apply(merchant, null));
        verify(repository, never()).save(any());
    }

    @Test
    void aDobConfirmedMatchBelowTheScoreBarIsConfirmed() {
        ScreeningResult dob = result(ScreeningResult.ScreeningStatus.POTENTIAL_MATCH, 0.82, "un_sc_sanctions", null);
        dob.getMatches().get(0).setMatchType(ScreeningResult.MatchType.DOB_CONFIRMED);
        assertEquals(SanctionsHitClassifier.Verdict.CONFIRMED, SanctionsHitClassifier.classify(dob, 0.95));
    }

    @Test
    void aClearRescreenLiftsAnAutomaticBlockButNeverAManualOne() {
        EdgeMerchantBlocklistEntry automatic = new EdgeMerchantBlocklistEntry();
        automatic.setPspId(12L);
        automatic.setMerchantId("421");
        automatic.setReasonCode("SANCTIONED");
        when(repository.findFirstByPspIdAndMerchantIdAndRemovedAtIsNull(12L, "421")).thenReturn(Optional.of(automatic));
        assertEquals(MerchantSanctionsBlocklistService.Change.UNBLOCKED, service.apply(merchant, clear()));
        assertNotNull(automatic.getRemovedAt());
        assertEquals(MerchantSanctionsBlocklistService.ACTOR, automatic.getRemovedBy());

        EdgeMerchantBlocklistEntry manual = new EdgeMerchantBlocklistEntry();
        manual.setPspId(12L);
        manual.setMerchantId("421");
        manual.setReasonCode("REGULATOR_ORDER");
        when(repository.findFirstByPspIdAndMerchantIdAndRemovedAtIsNull(12L, "421")).thenReturn(Optional.of(manual));
        assertEquals(MerchantSanctionsBlocklistService.Change.NONE, service.apply(merchant, clear()));
        assertEquals(null, manual.getRemovedAt());
        // Already blocked (any reason): a new confirmed hit adds no second row.
        assertEquals(MerchantSanctionsBlocklistService.Change.NONE,
                service.apply(merchant, result(ScreeningResult.ScreeningStatus.MATCH, 0.99, "us_ofac_sdn", null)));
        assertEquals(1, saved.size());
    }
}
