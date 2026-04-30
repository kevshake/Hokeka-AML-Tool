package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.rules.TransactionFact;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes SpEL (Spring Expression Language) based rules.
 * Thread-safe implementation with expression caching.
 */
@Service
public class SpelRuleExecutor {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Evaluate a SpEL rule against a transaction.
     *
     * @param rule The rule definition containing the SpEL expression.
     * @param fact The transaction fact to evaluate against.
     * @return true if the rule matches (expression evaluates to true), false otherwise.
     */
    public boolean evaluate(RuleDefinition rule, TransactionFact fact) {
        if (rule.getRuleExpression() == null || rule.getRuleExpression().isBlank()) {
            return false; // Empty rules don't match
        }

        try {
            // Get or parse expression
            Expression exp = expressionCache.computeIfAbsent(rule.getRuleExpression(), parser::parseExpression);

            // Create context with transaction as root object (or variable)
            StandardEvaluationContext context = new StandardEvaluationContext(fact);
            // Allow accessing properties directly or via #tx alias
            context.setVariable("tx", fact); 

            // Evaluates to boolean
            Boolean result = exp.getValue(context, Boolean.class);
            return result != null && result;

        } catch (Exception e) {
            // Log error but don't fail the whole transaction
            // Ideally use a Logger here
            System.err.println("Error evaluating SpEL rule '" + rule.getName() + "': " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Clear cache (useful when reloading rules)
     */
    public void clearCache() {
        expressionCache.clear();
    }
}
