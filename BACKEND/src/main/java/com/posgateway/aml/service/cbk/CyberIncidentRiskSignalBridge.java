package com.posgateway.aml.service.cbk;

import com.posgateway.aml.entity.multiasset.FinancialCrimeSignalType;
import com.posgateway.aml.entity.multiasset.MultiAssetRiskSignal;
import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import com.posgateway.aml.entity.psp.cbk.PspCyberIncident;
import com.posgateway.aml.repository.multiasset.MultiAssetRiskSignalRepository;
import com.posgateway.aml.repository.multiasset.MultiAssetTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits {@code CYBER} {@link MultiAssetRiskSignal} rows when a CBK cyber incident is linked to
 * multi-asset transactions via persisted metadata — never fabricates signals without that evidence.
 */
@Service
public class CyberIncidentRiskSignalBridge {

    private static final Logger log = LoggerFactory.getLogger(CyberIncidentRiskSignalBridge.class);
    static final String SIGNAL_CODE = "CYBER_INCIDENT_LINKED";

    private final MultiAssetTransactionRepository transactionRepository;
    private final MultiAssetRiskSignalRepository signalRepository;

    public CyberIncidentRiskSignalBridge(MultiAssetTransactionRepository transactionRepository,
            MultiAssetRiskSignalRepository signalRepository) {
        this.transactionRepository = transactionRepository;
        this.signalRepository = signalRepository;
    }

    @Transactional
    public int emitSignalsForIncident(PspCyberIncident incident) {
        if (incident == null || incident.getPspId() == null || incident.getIncidentNumber() == null) {
            return 0;
        }
        List<MultiAssetTransaction> linked = transactionRepository.findLinkedToCyberIncident(
                incident.getPspId(), incident.getId(), incident.getIncidentNumber());
        int created = 0;
        for (MultiAssetTransaction transaction : linked) {
            if (signalRepository.existsByTransactionIdAndSignalCode(transaction.getId(), SIGNAL_CODE)) {
                continue;
            }
            MultiAssetRiskSignal signal = new MultiAssetRiskSignal();
            signal.setTransaction(transaction);
            signal.setCustomer(transaction.getCustomer());
            signal.setPspId(incident.getPspId());
            signal.setSignalCode(SIGNAL_CODE);
            signal.setSignalType(FinancialCrimeSignalType.CYBER);
            signal.setProductDomain(transaction.getProductDomain());
            signal.setSeverity("HIGH");
            signal.setScoreImpact(45);
            signal.setDescription("Transaction metadata links to CBK cyber incident "
                    + incident.getIncidentNumber() + ".");
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("cyberIncidentId", incident.getId());
            evidence.put("cyberIncidentNumber", incident.getIncidentNumber());
            evidence.put("incidentDate", incident.getIncidentDate());
            evidence.put("transactionId", transaction.getId());
            signal.setEvidence(evidence);
            signalRepository.save(signal);
            created++;
        }
        if (created > 0) {
            log.info("Created {} CYBER risk signal(s) for incident {} (pspId={})",
                    created, incident.getIncidentNumber(), incident.getPspId());
        }
        return created;
    }
}
