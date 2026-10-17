package com.posgateway.aml.service.edge.typology;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the SpEL subset the rule catalog uses ({@code rule_definitions.rule_expression}),
 * producing an expression tree that {@link SpelEdgeTranslator} turns into Edge Rule IR.
 *
 * <p>Grammar (lowest precedence first):
 * <pre>
 *   expr    := or ( '?:' or )?
 *   or      := and ( ('||' | 'or') and )*
 *   and     := not ( ('&amp;&amp;' | 'and') not )*
 *   not     := ('!' | 'not') not | cmp
 *   cmp     := sum ( ('=='|'!='|'&gt;='|'&lt;='|'&gt;'|'&lt;'|'eq'|'ne'|'gt'|'ge'|'lt'|'le') sum )?
 *   sum     := prod ( ('+'|'-') prod )*
 *   prod    := unary ( ('*'|'/') unary )*
 *   unary   := '-' unary | postfix
 *   postfix := primary ( '.' ident [ '(' args ')' ] | '[' expr ']' )*
 *   primary := '#' ident | number | 'string' | true | false | null | '(' expr ')' | '{' args '}'
 * </pre>
 * Anything outside it (type references, constructors, bean references, projections, assignment,
 * ternaries, regex matching) is a {@link UncompilableRuleException}: such a rule is reported as not
 * deployable to the Edge rather than approximated.
 */
public final class SpelSubsetParser {

    /** Expression tree node. */
    public sealed interface Expr permits Var, Index, Prop, Call, Lit, ListLit, Unary, Binary, Elvis {
    }

    /** {@code #name}. */
    public record Var(String name) implements Expr {
    }

    /** {@code target[key]}. */
    public record Index(Expr target, Expr key) implements Expr {
    }

    /** {@code target.name}. */
    public record Prop(Expr target, String name) implements Expr {
    }

    /** {@code target.name(args)}. */
    public record Call(Expr target, String name, List<Expr> args) implements Expr {
    }

    /** A literal: Double, String, Boolean or null. */
    public record Lit(Object value) implements Expr {
    }

    /** {@code {a, b, ...}}. */
    public record ListLit(List<Expr> items) implements Expr {
    }

    /** {@code !e} or {@code -e}. */
    public record Unary(String op, Expr operand) implements Expr {
    }

    /** A binary operator. */
    public record Binary(String op, Expr left, Expr right) implements Expr {
    }

    /** {@code left ?: right}. */
    public record Elvis(Expr left, Expr right) implements Expr {
    }

    private final String src;
    private int pos;

    /** @param src the expression text */
    private SpelSubsetParser(String src) {
        this.src = src;
    }

    /**
     * Parses one expression.
     *
     * @throws UncompilableRuleException when the text is outside the supported subset
     */
    public static Expr parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new UncompilableRuleException("rule has no SpEL expression");
        }
        SpelSubsetParser p = new SpelSubsetParser(expression);
        Expr e = p.expr();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw p.error("unexpected text");
        }
        return e;
    }

    /** {@code expr := or ('?:' or)?}. */
    private Expr expr() {
        Expr left = or();
        if (eat("?:")) {
            return new Elvis(left, or());
        }
        skipWs();
        if (peek() == '?') {
            throw error("ternary/safe-navigation is not supported");
        }
        return left;
    }

    /** {@code or := and ('||' and)*}. */
    private Expr or() {
        Expr left = and();
        while (eat("||") || eatWord("or")) {
            left = new Binary("||", left, and());
        }
        return left;
    }

    /** {@code and := not ('&&' not)*}. */
    private Expr and() {
        Expr left = not();
        while (eat("&&") || eatWord("and")) {
            left = new Binary("&&", left, not());
        }
        return left;
    }

    /** {@code not := '!' not | cmp}. */
    private Expr not() {
        skipWs();
        if (peek() == '!' && peekAt(1) != '=') {
            pos++;
            return new Unary("!", not());
        }
        if (eatWord("not")) {
            return new Unary("!", not());
        }
        return cmp();
    }

    /** {@code cmp := sum (op sum)?}. */
    private Expr cmp() {
        Expr left = sum();
        String op = comparison();
        if (op == null) {
            return left;
        }
        return new Binary(op, left, sum());
    }

    /** Consumes a comparison operator, returning its canonical symbol, or null. */
    private String comparison() {
        for (String sym : new String[]{"==", "!=", ">=", "<="}) {
            if (eat(sym)) {
                return sym;
            }
        }
        skipWs();
        if (peek() == '>' ) {
            pos++;
            return ">";
        }
        if (peek() == '<') {
            pos++;
            return "<";
        }
        String[][] words = {{"eq", "=="}, {"ne", "!="}, {"ge", ">="}, {"le", "<="}, {"gt", ">"}, {"lt", "<"}};
        for (String[] w : words) {
            if (eatWord(w[0])) {
                return w[1];
            }
        }
        if (eatWord("matches") || eatWord("instanceof") || eatWord("between")) {
            throw error("operator is not supported");
        }
        return null;
    }

    /** {@code sum := prod (('+'|'-') prod)*}. */
    private Expr sum() {
        Expr left = prod();
        while (true) {
            skipWs();
            char c = peek();
            if ((c == '+' || c == '-') && peekAt(1) != '+' && peekAt(1) != '-') {
                pos++;
                left = new Binary(String.valueOf(c), left, prod());
            } else {
                return left;
            }
        }
    }

    /** {@code prod := unary (('*'|'/') unary)*}. */
    private Expr prod() {
        Expr left = unary();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '*' || c == '/') {
                pos++;
                left = new Binary(String.valueOf(c), left, unary());
            } else if (c == '%' || c == '^') {
                throw error("operator '" + c + "' is not supported");
            } else {
                return left;
            }
        }
    }

    /** {@code unary := '-' unary | postfix}. */
    private Expr unary() {
        skipWs();
        if (peek() == '-') {
            pos++;
            return new Unary("-", unary());
        }
        return postfix();
    }

    /** {@code postfix := primary ('.' ident ['(' args ')'] | '[' expr ']')*}. */
    private Expr postfix() {
        Expr e = primary();
        while (true) {
            skipWs();
            if (peek() == '.' && !Character.isDigit(peekAt(1))) {
                pos++;
                skipWs();
                if (peek() == '?' || peek() == '!' || peek() == '^' || peek() == '$') {
                    throw error("selection/projection is not supported");
                }
                String name = ident();
                skipWs();
                if (peek() == '(') {
                    pos++;
                    e = new Call(e, name, args(')'));
                } else {
                    e = new Prop(e, name);
                }
            } else if (peek() == '[') {
                pos++;
                Expr key = expr();
                expect(']');
                e = new Index(e, key);
            } else if (peek() == '?' && peekAt(1) == '.') {
                throw error("safe navigation is not supported");
            } else {
                return e;
            }
        }
    }

    /** {@code primary}. */
    private Expr primary() {
        skipWs();
        char c = peek();
        if (c == '#') {
            pos++;
            return new Var(ident());
        }
        if (c == '(') {
            pos++;
            Expr e = expr();
            expect(')');
            return e;
        }
        if (c == '{') {
            pos++;
            return new ListLit(args('}'));
        }
        if (c == '\'' || c == '"') {
            return new Lit(string(c));
        }
        if (Character.isDigit(c) || (c == '.' && Character.isDigit(peekAt(1)))) {
            return new Lit(number());
        }
        if (Character.isLetter(c)) {
            String word = ident();
            switch (word) {
                case "true" -> {
                    return new Lit(Boolean.TRUE);
                }
                case "false" -> {
                    return new Lit(Boolean.FALSE);
                }
                case "null" -> {
                    return new Lit(null);
                }
                case "T", "new" -> throw error("type references and constructors are not supported");
                default -> throw error("bare identifier '" + word + "' is not supported");
            }
        }
        if (c == '@' || c == '&') {
            throw error("bean references are not supported");
        }
        throw error("unexpected character '" + (pos < src.length() ? String.valueOf(c) : "end") + "'");
    }

    /** Comma-separated expressions up to {@code close}. */
    private List<Expr> args(char close) {
        List<Expr> out = new ArrayList<>();
        skipWs();
        if (peek() == close) {
            pos++;
            return out;
        }
        while (true) {
            out.add(expr());
            skipWs();
            if (peek() == ',') {
                pos++;
            } else {
                expect(close);
                return out;
            }
        }
    }

    /** A quoted string; a doubled quote is an escaped quote. */
    private String string(char quote) {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == quote) {
                if (pos < src.length() && src.charAt(pos) == quote) {
                    sb.append(quote);
                    pos++;
                } else {
                    return sb.toString();
                }
            } else {
                sb.append(c);
            }
        }
        throw error("unterminated string");
    }

    /** A decimal number literal (with optional L/D/F suffix), as a Double. */
    private Double number() {
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
            pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        String text = src.substring(start, pos);
        if (pos < src.length() && "LlDdFf".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            throw error("bad number '" + text + "'");
        }
    }

    /** An identifier. */
    private String ident() {
        skipWs();
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        if (start == pos) {
            throw error("identifier expected");
        }
        return src.substring(start, pos);
    }

    /** Consumes {@code c} or fails. */
    private void expect(char c) {
        skipWs();
        if (peek() != c) {
            throw error("'" + c + "' expected");
        }
        pos++;
    }

    /** Consumes a symbol when it is next. */
    private boolean eat(String sym) {
        skipWs();
        if (src.startsWith(sym, pos)) {
            pos += sym.length();
            return true;
        }
        return false;
    }

    /** Consumes a keyword when it is next and not part of a longer identifier. */
    private boolean eatWord(String word) {
        skipWs();
        if (src.regionMatches(true, pos, word, 0, word.length())) {
            int end = pos + word.length();
            if (end == src.length() || !(Character.isLetterOrDigit(src.charAt(end)) || src.charAt(end) == '_')) {
                pos = end;
                return true;
            }
        }
        return false;
    }

    /** Skips whitespace. */
    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    /** The current character, or NUL at the end. */
    private char peek() {
        return peekAt(0);
    }

    /** The character {@code offset} ahead, or NUL past the end. */
    private char peekAt(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    /** A parse error at the current position (no expression text beyond the position is echoed). */
    private UncompilableRuleException error(String what) {
        return new UncompilableRuleException("SpEL " + what + " at position " + pos);
    }
}
