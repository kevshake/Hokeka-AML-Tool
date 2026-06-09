package com.posgateway.aml.service.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.rules.TransactionFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes SpEL (Spring Expression Language) based rules.
 * Thread-safe implementation with expression caching.
 * Supports #tx (TransactionFact), #features (velocity/screening context), #params (rule tunables).
 */
@Service
public class SpelRuleExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SpelRuleExecutor.class);

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper objectMapper;
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public SpelRuleExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean evaluate(RuleDefinition rule, TransactionFact fact) {
        return evaluate(rule, fact, Collections.emptyMap());
    }

    /**
     * Evaluate a SpEL rule against a transaction and optional feature map.
     */
    public boolean evaluate(RuleDefinition rule, TransactionFact fact, Map<String, Object> features) {
        if (rule.getRuleExpression() == null || rule.getRuleExpression().isBlank()) {
            return false;
        }

        try {
            Expression exp = expressionCache.computeIfAbsent(rule.getRuleExpression(), parser::parseExpression);

            StandardEvaluationContext context = new StandardEvaluationContext(fact);
            context.setVariable("tx", fact);
            context.setVariable("features", features != null ? features : Collections.emptyMap());
            context.setVariable("params", parseParameters(rule.getParameters()));

            Boolean result = exp.getValue(context, Boolean.class);
            return result != null && result;

        } catch (Exception e) {
            logger.warn("Error evaluating SpEL rule '{}': {}", rule.getName(), e.getMessage());
            return false;
        }
    }

    private Map<String, Object> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<>() {});
        } catch (Exception e) {
            logger.debug("Could not parse rule parameters JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public void clearCache() {
        expressionCache.clear();
    }
}
