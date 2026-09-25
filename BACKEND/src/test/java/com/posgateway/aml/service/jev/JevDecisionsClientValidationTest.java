package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JevDecisionsClientValidationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validatesNoulChoiceAndScorePrimitives() throws Exception {
        Map<String, String> expected = Map.of(
                "laundering_suspicion", "noul",
                "typology", "choice",
                "activity_risk", "score");
        var answers = mapper.readTree("""
                {
                  "laundering_suspicion": {"type":"noul","noul":0.5},
                  "typology": {"type":"choice","choice":"structuring","probabilities":{"structuring":1.0}},
                  "activity_risk": {"type":"score","score":0.8,"legend":{"0":"a","1":"b"},"probabilities":{"0":0.2,"1":0.8}}
                }
                """);
        assertDoesNotThrow(() -> JevDecisionsClient.validateAnswers(answers, expected));
    }

    @Test
    void rejectsMissingAnswer() {
        Map<String, String> expected = Map.of("same_entity", "noul");
        var answers = mapper.createObjectNode();
        assertThrows(IllegalStateException.class,
                () -> JevDecisionsClient.validateAnswers(answers, expected));
    }

    @Test
    void rejectsBadProbabilitySum() throws Exception {
        Map<String, String> expected = Map.of("typology", "choice");
        var answers = mapper.createObjectNode();
        answers.set("typology", mapper.readTree("""
                {"type":"choice","choice":"structuring","probabilities":{"structuring":0.5,"other":0.3}}
                """));
        assertThrows(IllegalStateException.class,
                () -> JevDecisionsClient.validateAnswers(answers, expected));
    }
}
