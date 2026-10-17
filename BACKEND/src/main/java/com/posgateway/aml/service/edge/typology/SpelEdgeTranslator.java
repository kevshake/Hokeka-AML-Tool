package com.posgateway.aml.service.edge.typology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Binary;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Call;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Elvis;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Expr;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Index;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Lit;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.ListLit;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Prop;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Unary;
import com.posgateway.aml.service.edge.typology.SpelSubsetParser.Var;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Translates a parsed catalog SpEL expression into an Edge Rule IR condition.
 *
 * <p>{@code #params} are folded at compile time from the rule's {@code parameters} JSON (so
 * {@code (#params['threshold_amount'] ?: 9000) * 0.9} becomes the constant 8100), {@code #tx} and
 * {@code #features} references become Edge feature names via {@link EdgeFeatureCatalog}, list-hit
 * features become {@code in_list} conditions over lists recorded in {@link #lists()}, and
 * comparisons keep SpEL's null semantics: in SpEL {@code null < x}, {@code null <= x} and
 * {@code null != x} are true, so those compile to {@code any(not exists f, cmp)} — a missing Edge
 * feature then behaves exactly as a missing cloud feature did.
 *
 * <p>Anything with no faithful Edge form throws {@link UncompilableRuleException} with the reason.
 */
public final class SpelEdgeTranslator {

    /** Symbolic value of a sub-expression. */
    private sealed interface Sym permits Const, Ref, Cond, Macro, Root {
    }

    /** A compile-time constant (null, Double, String, Boolean or List). */
    private record Const(Object value) implements Sym {
    }

    /** A reference to an Edge feature. */
    private record Ref(String field) implements Sym {
    }

    /** A boolean IR condition. */
    private record Cond(ObjectNode node) implements Sym {
    }

    /** A boolean cloud feature that is a list-membership condition on the Edge (never null). */
    private record Macro(ObjectNode node) implements Sym {
    }

    /** One of {@code #tx}, {@code #features}, {@code #params}. */
    private record Root(String name) implements Sym {
    }

    private final EdgeBundleService ir;
    private final JsonNode params;
    private final Long pspId;
    private final EdgeReferenceLists referenceLists;
    private final Map<String, List<String>> lists;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param ir             IR node builders
     * @param params         the rule's parameters JSON object
     * @param pspId          PSP the bundle is compiled for (null = global defaults)
     * @param referenceLists source of list values
     * @param lists          bundle list section, filled with every list a translated rule uses
     */
    public SpelEdgeTranslator(EdgeBundleService ir, JsonNode params, Long pspId, EdgeReferenceLists referenceLists,
                              Map<String, List<String>> lists) {
        this.ir = ir;
        this.params = params == null ? mapper.createObjectNode() : params;
        this.pspId = pspId;
        this.referenceLists = referenceLists;
        this.lists = lists;
    }

    /** Parses and translates one expression to an IR condition. */
    public ObjectNode translate(String expression) {
        return toCond(eval(SpelSubsetParser.parse(expression)));
    }

    /** The bundle lists the translated rules reference. */
    public Map<String, List<String>> lists() {
        return lists;
    }

    /** Evaluates an expression node symbolically. */
    private Sym eval(Expr e) {
        if (e instanceof Lit lit) {
            return new Const(lit.value());
        }
        if (e instanceof ListLit list) {
            List<Object> items = new ArrayList<>();
            for (Expr item : list.items()) {
                Sym s = eval(item);
                if (!(s instanceof Const c)) {
                    throw new UncompilableRuleException("inline list items must be constants");
                }
                items.add(c.value());
            }
            return new Const(items);
        }
        if (e instanceof Var v) {
            return switch (v.name()) {
                case "tx", "features", "params" -> new Root(v.name());
                case "this", "root" -> new Root("tx");
                default -> throw new UncompilableRuleException("variable #" + v.name() + " is not supported");
            };
        }
        if (e instanceof Index idx) {
            return index(eval(idx.target()), eval(idx.key()));
        }
        if (e instanceof Prop p) {
            return property(eval(p.target()), p.name());
        }
        if (e instanceof Call call) {
            return call(eval(call.target()), call.name(), call.args());
        }
        if (e instanceof Unary u) {
            Sym operand = eval(u.operand());
            if ("!".equals(u.op())) {
                if (operand instanceof Const c && c.value() instanceof Boolean b) {
                    return new Const(!b);
                }
                return new Cond(ir.not(toCond(operand)));
            }
            if (operand instanceof Const c && c.value() instanceof Number n) {
                return new Const(-n.doubleValue());
            }
            throw new UncompilableRuleException("negation of a feature value is not supported");
        }
        if (e instanceof Elvis el) {
            Sym left = eval(el.left());
            if (left instanceof Const c) {
                return c.value() != null ? c : eval(el.right());
            }
            throw new UncompilableRuleException("'?:' on a feature value is not supported");
        }
        if (e instanceof Binary b) {
            return binary(b.op(), b.left(), b.right());
        }
        throw new UncompilableRuleException("unsupported expression");
    }

    /** {@code target[key]}: a feature, a parameter, or a constant map/list lookup. */
    private Sym index(Sym target, Sym key) {
        if (!(key instanceof Const k) || !(k.value() instanceof String name)) {
            throw new UncompilableRuleException("index keys must be string constants");
        }
        if (target instanceof Root r && "features".equals(r.name())) {
            return feature(name);
        }
        if (target instanceof Root r && "params".equals(r.name())) {
            return new Const(javaValue(params.get(name)));
        }
        throw new UncompilableRuleException("indexing is supported only on #features and #params");
    }

    /** A {@code #features['k']} reference. */
    private Sym feature(String key) {
        EdgeFeatureCatalog.ListHit hit = EdgeFeatureCatalog.listHit(key);
        if (hit != null) {
            return new Macro(listHitCondition(hit));
        }
        String field = EdgeFeatureCatalog.feature(key);
        if (field == null) {
            throw new UncompilableRuleException(EdgeFeatureCatalog.notOnEdgeReason(key));
        }
        return new Ref(field);
    }

    /** The IR condition of a list-hit macro, recording the lists it uses. */
    private ObjectNode listHitCondition(EdgeFeatureCatalog.ListHit hit) {
        return switch (hit.kind()) {
            case SINGLE -> ir.inList(hit.field(), useList(hit.list()));
            case PAN_OR_DEVICE -> ir.any(
                    ir.inList("pan_hash", useList(EdgeReferenceLists.BLACKLIST_PREFIX + "pan")),
                    ir.inList(EdgeFeatureCatalog.DEVICE_KEY, useList(EdgeReferenceLists.BLACKLIST_PREFIX + "device")));
            case KEYWORDS -> {
                TreeSet<String> active = new TreeSet<>();
                for (String v : referenceLists.values(pspId, hit.list())) {
                    String kw = v.toLowerCase(java.util.Locale.ROOT);
                    if (EdgeFeatureCatalog.REFERENCE_KEYWORDS.contains(kw)) {
                        active.add(kw);
                    }
                }
                if (active.isEmpty()) {
                    yield ir.constant(false);
                }
                List<ObjectNode> leaves = new ArrayList<>();
                for (String kw : active) {
                    leaves.add(ir.cmp(hit.field(), "CONTAINS", kw));
                }
                yield leaves.size() == 1 ? leaves.get(0) : ir.any(leaves.toArray(new ObjectNode[0]));
            }
        };
    }

    /** Fetches a list once into the bundle's list section and returns its name. */
    private String useList(String name) {
        if (!lists.containsKey(name)) {
            List<String> values = new ArrayList<>(new TreeSet<>(referenceLists.values(pspId, name)));
            lists.put(name, values);
        }
        return name;
    }

    /** {@code target.name}: a {@code #tx} property, or a no-op accessor on a feature. */
    private Sym property(Sym target, String name) {
        if (target instanceof Root r && "tx".equals(r.name())) {
            return txProperty(name);
        }
        throw new UncompilableRuleException("property access '." + name + "' is supported only on #tx");
    }

    /** A {@code #tx} bean property. */
    private Sym txProperty(String name) {
        if ("highRiskCountry".equals(name)) {
            return new Macro(ir.inList("country_code", useList(EdgeReferenceLists.HIGH_RISK_COUNTRIES)));
        }
        if ("highValueTransaction".equals(name)) {
            return new Cond(ir.cmp("amount", "GTE", 10000));
        }
        String field = EdgeFeatureCatalog.txProperty(name);
        if (field == null) {
            throw new UncompilableRuleException(EdgeFeatureCatalog.txNotOnEdgeReason(name));
        }
        return new Ref(field);
    }

    /** {@code target.name(args)}. */
    private Sym call(Sym target, String name, List<Expr> args) {
        if (target instanceof Root r && "tx".equals(r.name()) && args.isEmpty()) {
            if (name.startsWith("is") && name.length() > 2) {
                return txProperty(Character.toLowerCase(name.charAt(2)) + name.substring(3));
            }
            if (name.startsWith("get") && name.length() > 3) {
                return txProperty(Character.toLowerCase(name.charAt(3)) + name.substring(4));
            }
        }
        if (target instanceof Ref ref && args.isEmpty()
                && List.of("doubleValue", "longValue", "intValue", "floatValue").contains(name)) {
            return ref;
        }
        if ("contains".equals(name) && args.size() == 1 && target instanceof Const c) {
            return contains(c.value(), eval(args.get(0)));
        }
        throw new UncompilableRuleException("method ." + name + "(...) has no Edge equivalent");
    }

    /** {@code list.contains(x)} with a constant list. */
    private Sym contains(Object list, Sym item) {
        if (list == null) {
            throw new UncompilableRuleException(".contains on a null parameter");
        }
        if (!(list instanceof List<?> values)) {
            throw new UncompilableRuleException(".contains is supported only on lists");
        }
        if (item instanceof Const c) {
            return new Const(values.stream().anyMatch(v -> Objects.equals(v, c.value())));
        }
        if (!(item instanceof Ref ref)) {
            throw new UncompilableRuleException(".contains argument must be a feature");
        }
        if (values.isEmpty()) {
            return new Const(false);
        }
        boolean csvSafe = values.stream().allMatch(v -> v instanceof String s && !s.contains(",")
                && s.equals(s.trim()) && !s.isEmpty());
        if (csvSafe) {
            return new Cond(ir.cmp(ref.field(), "IN", String.join(",", values.stream().map(String::valueOf).toList())));
        }
        List<ObjectNode> eqs = new ArrayList<>();
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            eqs.add(ir.cmp(ref.field(), "EQ", v));
        }
        if (eqs.isEmpty()) {
            return new Const(false);
        }
        return new Cond(eqs.size() == 1 ? eqs.get(0) : ir.any(eqs.toArray(new ObjectNode[0])));
    }

    /** Logical, arithmetic and comparison operators. */
    private Sym binary(String op, Expr leftExpr, Expr rightExpr) {
        if ("&&".equals(op) || "||".equals(op)) {
            Sym left = eval(leftExpr);
            Sym right = eval(rightExpr);
            boolean and = "&&".equals(op);
            Boolean lc = constBool(left);
            Boolean rc = constBool(right);
            if (lc != null) {
                return lc == and ? asBooleanSym(right) : new Const(lc);
            }
            if (rc != null) {
                return rc == and ? asBooleanSym(left) : new Const(rc);
            }
            ObjectNode l = toCond(left);
            ObjectNode r = toCond(right);
            return new Cond(and ? flatten("all", l, r) : flatten("any", l, r));
        }
        Sym left = eval(leftExpr);
        Sym right = eval(rightExpr);
        switch (op) {
            case "+", "-", "*", "/" -> {
                return arithmetic(op, left, right);
            }
            default -> {
                return compare(op, left, right);
            }
        }
    }

    /** Folds arithmetic on two numeric constants; anything else is unsupported. */
    private Sym arithmetic(String op, Sym left, Sym right) {
        if (left instanceof Const a && right instanceof Const b
                && a.value() instanceof Number x && b.value() instanceof Number y) {
            double l = x.doubleValue();
            double r = y.doubleValue();
            return new Const(switch (op) {
                case "+" -> l + r;
                case "-" -> l - r;
                case "*" -> l * r;
                default -> {
                    if (r == 0.0) {
                        throw new UncompilableRuleException("division by zero in parameters");
                    }
                    yield l / r;
                }
            });
        }
        if (left instanceof Const a && right instanceof Const b && "+".equals(op)
                && (a.value() instanceof String || b.value() instanceof String)) {
            return new Const(String.valueOf(a.value()) + b.value());
        }
        throw new UncompilableRuleException("arithmetic on feature values is not supported");
    }

    /** A comparison, keeping SpEL's null semantics. */
    private Sym compare(String op, Sym left, Sym right) {
        if (left instanceof Const a && right instanceof Const b) {
            return new Const(constCompare(op, a.value(), b.value()));
        }
        if (left instanceof Const && !(right instanceof Const)) {
            return compare(flip(op), right, left);
        }
        if (!(right instanceof Const c)) {
            throw new UncompilableRuleException("comparing two features is not supported");
        }
        Object v = c.value();
        if (left instanceof Macro m) {
            if (v == null) {
                // List-hit features are always set (true/false) by the cloud producer.
                return new Const(!"==".equals(op));
            }
            if (!(v instanceof Boolean b) || !("==".equals(op) || "!=".equals(op))) {
                throw new UncompilableRuleException("a list-hit feature can only be compared with true/false");
            }
            boolean wantTrue = b == "==".equals(op);
            return new Cond(wantTrue ? m.node() : ir.not(m.node()));
        }
        if (left instanceof Cond cond) {
            if (v instanceof Boolean b && ("==".equals(op) || "!=".equals(op))) {
                boolean wantTrue = b == "==".equals(op);
                return new Cond(wantTrue ? cond.node() : ir.not(cond.node()));
            }
            throw new UncompilableRuleException("a boolean condition can only be compared with true/false");
        }
        if (!(left instanceof Ref ref)) {
            throw new UncompilableRuleException("unsupported comparison operand");
        }
        String field = ref.field();
        if (v == null) {
            return switch (op) {
                case "==" -> new Cond(ir.not(ir.exists(field)));
                case "!=" -> new Cond(ir.exists(field));
                // SpEL: null <= null and null >= null are true; a present value vs null compares as greater.
                case ">", ">=" -> new Cond(ir.exists(field));
                default -> new Cond(ir.not(ir.exists(field)));
            };
        }
        if (v instanceof List<?>) {
            throw new UncompilableRuleException("comparing a feature with a list is not supported");
        }
        String irOp = switch (op) {
            case "==" -> "EQ";
            case "!=" -> "NE";
            case ">" -> "GT";
            case ">=" -> "GTE";
            case "<" -> "LT";
            default -> "LTE";
        };
        ObjectNode cmp = ir.cmp(field, irOp, v);
        // SpEL orders null below every value and treats null != x as true.
        if ("NE".equals(irOp) || "LT".equals(irOp) || "LTE".equals(irOp)) {
            return new Cond(ir.any(ir.not(ir.exists(field)), cmp));
        }
        return new Cond(cmp);
    }

    /** SpEL comparison of two constants (numbers numerically, otherwise equality / natural order). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean constCompare(String op, Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            int cmp = Double.compare(x.doubleValue(), y.doubleValue());
            return result(op, cmp);
        }
        if ("==".equals(op)) {
            return Objects.equals(a, b);
        }
        if ("!=".equals(op)) {
            return !Objects.equals(a, b);
        }
        if (a == null || b == null) {
            return result(op, a == null ? (b == null ? 0 : -1) : 1);
        }
        if (a instanceof Comparable ca && a.getClass().equals(b.getClass())) {
            return result(op, ca.compareTo(b));
        }
        throw new UncompilableRuleException("constants of different types cannot be ordered");
    }

    /** Maps a compareTo result through an ordering operator. */
    private static boolean result(String op, int cmp) {
        return switch (op) {
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            default -> cmp <= 0;
        };
    }

    /** The operator with its operands swapped. */
    private static String flip(String op) {
        return switch (op) {
            case ">" -> "<";
            case ">=" -> "<=";
            case "<" -> ">";
            case "<=" -> ">=";
            default -> op;
        };
    }

    /** A boolean constant's value, or null. */
    private static Boolean constBool(Sym s) {
        return s instanceof Const c && c.value() instanceof Boolean b ? b : null;
    }

    /** Keeps constants as constants and turns anything else into a condition. */
    private Sym asBooleanSym(Sym s) {
        return s instanceof Const ? s : new Cond(toCond(s));
    }

    /** Boolean view of a symbol: a feature used bare means {@code feature == true}. */
    private ObjectNode toCond(Sym s) {
        if (s instanceof Cond c) {
            return c.node();
        }
        if (s instanceof Macro m) {
            return m.node();
        }
        if (s instanceof Ref r) {
            return ir.cmp(r.field(), "EQ", true);
        }
        if (s instanceof Const c) {
            if (c.value() instanceof Boolean b) {
                return ir.constant(b);
            }
            throw new UncompilableRuleException("expression does not evaluate to a boolean");
        }
        throw new UncompilableRuleException("expression does not evaluate to a boolean");
    }

    /** {@code all}/{@code any} of two conditions, merging nested groups of the same kind. */
    private ObjectNode flatten(String type, ObjectNode l, ObjectNode r) {
        List<ObjectNode> parts = new ArrayList<>();
        for (ObjectNode n : List.of(l, r)) {
            if (type.equals(n.path("type").asText())) {
                n.get(type).forEach(child -> parts.add((ObjectNode) child));
            } else {
                parts.add(n);
            }
        }
        ObjectNode[] arr = parts.toArray(new ObjectNode[0]);
        return "all".equals(type) ? ir.all(arr) : ir.any(arr);
    }

    /** A parameter JSON value as a Java constant (numbers as Double, arrays as lists). */
    private static Object javaValue(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isNumber()) {
            return n.doubleValue();
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        if (n.isTextual()) {
            return n.textValue();
        }
        if (n.isArray()) {
            List<Object> out = new ArrayList<>();
            n.forEach(item -> out.add(javaValue(item)));
            return out;
        }
        throw new UncompilableRuleException("object-valued parameters are not supported");
    }
}
