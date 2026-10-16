package com.posgateway.aml.service.assessment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local buffer of finding drafts for synchronous orchestrator resolution during ingest.
 */
public final class AssessmentFindingBuffer {

    private static final ThreadLocal<List<FindingDraft>> BUFFER = ThreadLocal.withInitial(ArrayList::new);

    private AssessmentFindingBuffer() {
    }

    /**
     * Appends a copy of the draft to the in-flight buffer for the current thread.
     */
    public static void append(FindingDraft draft) {
        if (draft == null) {
            return;
        }
        BUFFER.get().add(copyOf(draft));
    }

    /**
     * Returns an immutable snapshot of buffered findings for resolution.
     */
    public static List<FindingDraft> snapshot() {
        return List.copyOf(BUFFER.get());
    }

    /**
     * Clears buffered findings (called when an assessment scope ends).
     */
    public static void clear() {
        BUFFER.remove();
    }

    private static FindingDraft copyOf(FindingDraft draft) {
        return FindingDraft.create()
                .assessmentId(draft.getAssessmentId())
                .txnId(draft.getTxnId())
                .partyId(draft.getPartyId())
                .sourceType(draft.getSourceType())
                .sourceId(draft.getSourceId())
                .sourceVersion(draft.getSourceVersion())
                .phase(draft.getPhase())
                .shadow(draft.isShadow())
                .nature(draft.getNature())
                .severity(draft.getSeverity())
                .triggered(draft.isTriggered())
                .score(draft.getScore())
                .proposedAction(draft.getProposedAction())
                .proposedActions(draft.getProposedActions())
                .evidence(draft.getEvidence())
                .featureReferences(draft.getFeatureReferences())
                .explanation(draft.getExplanation());
    }
}
