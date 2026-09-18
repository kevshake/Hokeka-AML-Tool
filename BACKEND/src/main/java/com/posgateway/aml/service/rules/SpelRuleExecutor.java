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
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes SpEL (Spring Expression Language) rules over #tx (TransactionFact), #features
 * (velocity/screening context) and #params (rule tunables).
 *
 * <p><b>Security:</b> rule expressions are operator- and (via {@code AiRuleGeneratorService})
 * LLM-authored, i.e. untrusted. They are evaluated in a <b>sandboxed {@link SimpleEvaluationContext}</b>,
 * NOT a {@code StandardEvaluationContext}. The sandbox excludes Java type references
 * ({@code T(java.lang.Runtime)}), constructors ({@code new ProcessBuilder(...)}), bean references
 * ({@code @beanName}) and static-method invocation — closing the authenticated-RCE path
 * ({@code T(java.lang.Runtime).getRuntime().exec(...)}) while still allowing property access,
 * map indexing, operators and instance-method calls the real rules need
 * ({@code #tx.isHighRiskCountry()}, {@code #tx.amount.doubleValue()}, {@code {'a','b'}.contains(x)}).
 * As defence-in-depth and for clear author-time errors, {@link #assertSafe(String)} additionally
 * rejects reflection-shaped tokens before an expression is ever parsed or stored.
 */
@Service
public class SpelRuleExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SpelRuleExecutor.class);

    /**
     * Substrings that must never appear in a rule expression. The runtime sandbox already blocks
     * type refs / constructors / statics, but rejecting these at authoring time gives a clear error
     * and also blocks the residual reflection route ({@code #this.getClass().getClassLoader()...}).
     */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "T(", "new ", "getClass", "getClassLoader", "forName", ".class", "@",
            "Runtime", "ProcessBuilder", "System.", "Thread", "exec(", "loadClass");

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
            throw new RuleEvaluationException("Rule expression is blank");
        }

        try {
            Expression exp = expressionCache.computeIfAbsent(rule.getRuleExpression(), this::parseSafe);

            // Sandboxed context: no T()/constructors/beans/static methods, but property access,
            // map indexing and instance-method calls are permitted.
            SimpleEvaluationContext context = SimpleEvaluationContext
                    .forReadOnlyDataBinding()
                    .withInstanceMethods()
                    .build();
            context.setVariable("tx", fact);
            context.setVariable("features", features != null ? features : Collections.emptyMap());
            context.setVariable("params", parseParameters(rule.getParameters()));

            Boolean result = exp.getValue(context, fact, Boolean.class);
            return result != null && result;

        } catch (Exception e) {
            logger.error("Error evaluating SpEL rule '{}': {}", rule.getName(), e.getMessage());
            if (e instanceof RuleEvaluationException evaluationException) {
                throw evaluationException;
            }
            throw new RuleEvaluationException(
                    "Could not evaluate rule '" + rule.getName() + "'", e);
        }
    }

    /**
     * Validate an expression at authoring time: it must contain no forbidden construct AND parse
     * cleanly. Call this before persisting an operator/LLM-authored rule so a malformed or unsafe
     * expression is rejected with a clear error instead of forcing every transaction to HOLD (or,
     * previously, executing arbitrary code) at runtime.
     *
     * @throws RuleEvaluationException if the expression is unsafe or syntactically invalid
     */
    public void validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new RuleEvaluationException("Rule expression is blank");
        }
        parseSafe(expression);
    }

    private Expression parseSafe(String expression) {
        assertSafe(expression);
        try {
            return parser.parseExpression(expression);
        } catch (RuntimeException e) {
            throw new RuleEvaluationException("Rule expression is not valid SpEL: " + e.getMessage(), e);
        }
    }

    private static void assertSafe(String expression) {
        for (String token : FORBIDDEN_TOKENS) {
            if (expression.contains(token)) {
                throw new RuleEvaluationException(
                        "Rule expression uses a forbidden construct '" + token
                                + "'. Rules may only read #tx / #features / #params and call safe "
                                + "instance methods — no type references, reflection, constructors or beans.");
            }
        }
    }

    private Map<String, Object> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuleEvaluationException("Rule parameters are not valid JSON", e);
        }
    }

    public void clearCache() {
        expressionCache.clear();
    }

    public static class RuleEvaluationException extends RuntimeException {
        public RuleEvaluationException(String message) {
            super(message);
        }

        public RuleEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
