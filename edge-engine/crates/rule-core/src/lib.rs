//! Hokeka edge rule-evaluation core.
//!
//! Executes a signed **Rule IR** (compiled centrally from the `RuleDefinition` catalog and pulled
//! to the edge) against a transaction's features, producing a decision. This is the CPU-bound
//! kernel invoked once per transaction from the Java host over JNI. It has **no I/O and no
//! network** — feature values are supplied by the caller — so it is deterministic and fast.
//!
//! The IR mirrors the control-plane rule model: `all` (AND) / `any` (OR) / `not` groups over
//! field comparisons, each rule carrying an action + score, exactly as the Java
//! `DynamicRuleConverter` emits. IR version 2 (work package R10a) adds three leaf types the
//! compiled SpEL typology library needs:
//!
//! - `exists`: the field is present with a scalar value (SpEL `x != null`);
//! - `in_list`: the field's string form is in a named list carried in the bundle's `lists`
//!   section (high-risk countries, payment blacklists, merchant lists), compared ASCII
//!   case-insensitively;
//! - `const`: a constant truth value (a rule whose parameters make it inert compiles to `false`).
//!
//! The Java interpreter `EdgeRuleInterpreter` implements the same semantics; the shared vectors in
//! `test-vectors/rule_ir_parity.json` are run by both.

use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap, HashSet};

/// Tolerance of numeric equality (`EQ` / `NE` / `IN` on numbers), identical to the Java interpreter.
const EQ_TOLERANCE: f64 = 1e-9;

/// A feature/field value available to rule conditions.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(untagged)]
pub enum Value {
    Num(f64),
    Bool(bool),
    Str(String),
}

impl Value {
    /// Numeric view: numbers as-is, booleans as 1/0, strictly decimal strings parsed.
    fn as_num(&self) -> Option<f64> {
        match self {
            Value::Num(n) => Some(*n),
            Value::Bool(b) => Some(if *b { 1.0 } else { 0.0 }),
            Value::Str(s) => parse_num(s),
        }
    }
}

/// Parses a string as a number only when it is a plain decimal literal
/// (`[+-]digits[.digits][e[+-]digits]`). No whitespace, no `inf`/`NaN`, no hex — the Java
/// interpreter applies the same grammar, so both accept exactly the same strings.
pub fn parse_num(s: &str) -> Option<f64> {
    let b = s.as_bytes();
    let mut i = 0;
    if i < b.len() && (b[i] == b'+' || b[i] == b'-') {
        i += 1;
    }
    let int_start = i;
    while i < b.len() && b[i].is_ascii_digit() {
        i += 1;
    }
    let int_digits = i - int_start;
    let mut frac_digits = 0;
    if i < b.len() && b[i] == b'.' {
        i += 1;
        let frac_start = i;
        while i < b.len() && b[i].is_ascii_digit() {
            i += 1;
        }
        frac_digits = i - frac_start;
    }
    if int_digits == 0 && frac_digits == 0 {
        return None;
    }
    if i < b.len() && (b[i] == b'e' || b[i] == b'E') {
        i += 1;
        if i < b.len() && (b[i] == b'+' || b[i] == b'-') {
            i += 1;
        }
        let exp_start = i;
        while i < b.len() && b[i].is_ascii_digit() {
            i += 1;
        }
        if i == exp_start {
            return None;
        }
    }
    if i != b.len() {
        return None;
    }
    s.parse::<f64>().ok().filter(|v| v.is_finite())
}

/// Comparison operators supported by the IR.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "UPPERCASE")]
pub enum Op {
    Eq,
    Ne,
    Gt,
    Gte,
    Lt,
    Lte,
    In,
    Contains,
}

/// A boolean condition tree. `all` = AND, `any` = OR, `not` = negation, `cmp` = field comparison,
/// `exists` = field present, `in_list` = membership in a bundle list, `const` = fixed truth value.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum Condition {
    All {
        all: Vec<Condition>,
    },
    Any {
        any: Vec<Condition>,
    },
    Not {
        not: Box<Condition>,
    },
    Cmp {
        field: String,
        op: Op,
        value: Value,
    },
    Exists {
        field: String,
    },
    #[serde(rename = "in_list")]
    InList {
        field: String,
        list: String,
    },
    Const {
        value: bool,
    },
}

/// Terminal action a rule can assert; ordered by severity (Block strongest).
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "UPPERCASE")]
pub enum Action {
    Allow,
    Alert,
    Flag,
    Hold,
    Suspend,
    Block,
}

impl Action {
    /// Severity rank used to pick the strongest action.
    fn severity(self) -> u8 {
        match self {
            Action::Allow => 0,
            Action::Alert => 1,
            Action::Flag => 2,
            Action::Hold => 3,
            Action::Suspend => 4,
            Action::Block => 5,
        }
    }
    /// The stronger of two actions.
    fn max(self, other: Action) -> Action {
        if other.severity() > self.severity() {
            other
        } else {
            self
        }
    }
}

/// One compiled rule of the bundle.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    pub id: u64,
    pub name: String,
    #[serde(default)]
    pub description: Option<String>,
    pub condition: Condition,
    pub action: Action,
    #[serde(default)]
    pub score: i32,
    #[serde(default)]
    pub priority: i32,
    /// Control-plane rule_versions.id when the bundle carried one. Absent on older bundles.
    #[serde(default)]
    pub rule_version_id: Option<u64>,
}

/// A versioned, per-PSP bundle of rules — the plaintext inside an HSE-1 envelope.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuleBundle {
    pub version: u64,
    pub psp_id: i64,
    /// Content hash of the rule-version manifest. Empty on bundles sealed before WP-04.
    #[serde(default)]
    pub manifest_hash: String,
    /// IR revision the compiler emitted (2 = `exists` / `in_list` / `const`); 0 on older bundles.
    #[serde(default)]
    pub ir_version: u32,
    /// Named string lists referenced by `in_list` conditions.
    #[serde(default)]
    pub lists: BTreeMap<String, Vec<String>>,
    pub rules: Vec<Rule>,
    /// ASCII-uppercased lookup sets built from `lists` at load time.
    #[serde(skip)]
    list_index: HashMap<String, HashSet<String>>,
}

impl RuleBundle {
    /// Builds a bundle in code (tests, tools) with no lists; the lookup index is built here.
    pub fn new(version: u64, psp_id: i64, rules: Vec<Rule>) -> Self {
        Self::with_lists(version, psp_id, rules, BTreeMap::new())
    }

    /// Builds a bundle with named lists and its lookup index.
    pub fn with_lists(
        version: u64,
        psp_id: i64,
        rules: Vec<Rule>,
        lists: BTreeMap<String, Vec<String>>,
    ) -> Self {
        let mut bundle = RuleBundle {
            version,
            psp_id,
            manifest_hash: String::new(),
            ir_version: 2,
            lists,
            rules,
            list_index: HashMap::new(),
        };
        bundle.build_index();
        bundle
    }

    /// Parses the IR JSON, rejects a structurally inconsistent bundle (an `in_list` naming an
    /// undefined list) and builds the list lookup index. Any error leaves the caller's active
    /// bundle untouched.
    pub fn from_json(bytes: &[u8]) -> Result<Self, String> {
        let mut bundle: RuleBundle = serde_json::from_slice(bytes).map_err(|e| e.to_string())?;
        for rule in &bundle.rules {
            check_lists(&rule.condition, &bundle.lists)
                .map_err(|list| format!("rule {} references undefined list '{}'", rule.id, list))?;
        }
        bundle.build_index();
        Ok(bundle)
    }

    /// Rebuilds the case-insensitive lookup sets from `lists`.
    fn build_index(&mut self) {
        self.list_index = self
            .lists
            .iter()
            .map(|(name, items)| {
                (
                    name.clone(),
                    items.iter().map(|v| v.to_ascii_uppercase()).collect(),
                )
            })
            .collect();
    }

    /// True when `value` (already uppercased) is in the named list.
    fn list_contains(&self, list: &str, value_upper: &str) -> bool {
        self.list_index
            .get(list)
            .map(|set| set.contains(value_upper))
            .unwrap_or(false)
    }
}

/// Walks a condition tree and returns the first `in_list` list name not defined in `lists`.
fn check_lists(cond: &Condition, lists: &BTreeMap<String, Vec<String>>) -> Result<(), String> {
    match cond {
        Condition::All { all } => all.iter().try_for_each(|c| check_lists(c, lists)),
        Condition::Any { any } => any.iter().try_for_each(|c| check_lists(c, lists)),
        Condition::Not { not } => check_lists(not, lists),
        Condition::InList { list, .. } if !lists.contains_key(list) => Err(list.clone()),
        _ => Ok(()),
    }
}

/// The evaluation result relayed back to the PSP API node.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Decision {
    pub action: Action,
    pub score: i32,
    pub triggered_rule_ids: Vec<u64>,
    /// Parallel to triggered_rule_ids. None when that rule had no version id in the bundle.
    #[serde(default)]
    pub triggered_rule_version_ids: Vec<Option<u64>>,
    pub reasons: Vec<String>,
}

/// Feature context for one transaction (field name -> value).
pub type Features = HashMap<String, Value>;

/// Parses a JSON object of features. Scalar members (number, boolean, string) are kept; `null`,
/// arrays and objects are dropped, so they behave exactly like an absent feature — the same rule
/// the Java interpreter applies. A body that is not a JSON object is an error.
pub fn features_from_json(bytes: &[u8]) -> Result<Features, String> {
    let raw: serde_json::Map<String, serde_json::Value> =
        serde_json::from_slice(bytes).map_err(|e| e.to_string())?;
    let mut features = Features::with_capacity(raw.len());
    for (key, value) in raw {
        let scalar = match value {
            serde_json::Value::Number(n) => n.as_f64().map(Value::Num),
            serde_json::Value::Bool(b) => Some(Value::Bool(b)),
            serde_json::Value::String(s) => Some(Value::Str(s)),
            _ => None,
        };
        if let Some(v) = scalar {
            features.insert(key, v);
        }
    }
    Ok(features)
}

/// Evaluate a transaction's features against a bundle. Pure function, no I/O.
pub fn evaluate(bundle: &RuleBundle, features: &Features) -> Decision {
    let mut action = Action::Allow;
    let mut score: i32 = 0;
    let mut triggered = Vec::new();
    let mut triggered_versions = Vec::new();
    let mut reasons = Vec::new();

    for rule in &bundle.rules {
        if eval_condition(bundle, &rule.condition, features) {
            action = action.max(rule.action);
            score = score.saturating_add(rule.score);
            triggered.push(rule.id);
            triggered_versions.push(rule.rule_version_id);
            reasons.push(match &rule.description {
                Some(d) if !d.is_empty() => d.clone(),
                _ => rule.name.clone(),
            });
        }
    }

    Decision {
        action,
        score,
        triggered_rule_ids: triggered,
        triggered_rule_version_ids: triggered_versions,
        reasons,
    }
}

/// Recursively evaluates one condition node.
fn eval_condition(bundle: &RuleBundle, cond: &Condition, f: &Features) -> bool {
    match cond {
        Condition::All { all } => all.iter().all(|c| eval_condition(bundle, c, f)),
        Condition::Any { any } => any.iter().any(|c| eval_condition(bundle, c, f)),
        Condition::Not { not } => !eval_condition(bundle, not, f),
        Condition::Cmp { field, op, value } => eval_cmp(f.get(field), *op, value),
        Condition::Exists { field } => f.contains_key(field),
        Condition::InList { field, list } => match f.get(field) {
            Some(v) => bundle.list_contains(list, &value_to_string(v).to_ascii_uppercase()),
            None => false, // a missing feature is never on a list
        },
        Condition::Const { value } => *value,
    }
}

/// Evaluates a `cmp` leaf; a missing feature never satisfies a comparison.
fn eval_cmp(actual: Option<&Value>, op: Op, expected: &Value) -> bool {
    let actual = match actual {
        Some(v) => v,
        None => return false,
    };
    match op {
        Op::Eq => values_equal(actual, expected),
        Op::Ne => !values_equal(actual, expected),
        Op::Gt | Op::Gte | Op::Lt | Op::Lte => match (actual.as_num(), expected.as_num()) {
            (Some(a), Some(b)) => match op {
                Op::Gt => a > b,
                Op::Gte => a >= b,
                Op::Lt => a < b,
                Op::Lte => a <= b,
                _ => unreachable!(),
            },
            _ => false,
        },
        Op::In => match expected {
            // `value` is a comma-separated set, or a single value.
            Value::Str(s) => {
                let a = value_to_string(actual);
                s.split(',').any(|item| item.trim() == a.as_str())
            }
            other => values_equal(actual, other),
        },
        Op::Contains => value_to_string(actual).contains(value_to_string(expected).as_str()),
    }
}

/// Numeric equality within `EQ_TOLERANCE` when both sides are numeric, else string equality.
fn values_equal(a: &Value, b: &Value) -> bool {
    match (a.as_num(), b.as_num()) {
        (Some(x), Some(y)) => (x - y).abs() < EQ_TOLERANCE,
        _ => value_to_string(a) == value_to_string(b),
    }
}

/// String form of a value: integral numbers as a (saturating) 64-bit integer, other numbers in
/// shortest plain decimal form, booleans as true/false. The Java interpreter produces the same
/// text (`(long) d` and `BigDecimal.toPlainString`).
fn value_to_string(v: &Value) -> String {
    match v {
        Value::Str(s) => s.clone(),
        Value::Bool(b) => b.to_string(),
        Value::Num(n) => {
            if n.fract() == 0.0 {
                format!("{}", *n as i64)
            } else {
                format!("{}", n)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Builds a feature map from pairs.
    fn features(pairs: &[(&str, Value)]) -> Features {
        pairs
            .iter()
            .map(|(k, v)| (k.to_string(), v.clone()))
            .collect()
    }

    /// A bundle holding the given rules and no lists.
    fn bundle(rules: Vec<Rule>) -> RuleBundle {
        RuleBundle::new(1, 42, rules)
    }

    /// A rule with the given id, condition and action.
    fn rule(id: u64, condition: Condition, action: Action, score: i32) -> Rule {
        Rule {
            id,
            name: format!("rule {id}"),
            description: None,
            condition,
            action,
            score,
            priority: 1,
            rule_version_id: None,
        }
    }

    // A single comparison fires its action and score.
    #[test]
    fn high_value_blocks() {
        let b = bundle(vec![rule(
            1,
            Condition::Cmp {
                field: "amount".into(),
                op: Op::Gt,
                value: Value::Num(10000.0),
            },
            Action::Block,
            50,
        )]);
        let d = evaluate(&b, &features(&[("amount", Value::Num(25000.0))]));
        assert_eq!(d.action, Action::Block);
        assert_eq!(d.score, 50);
        assert_eq!(d.triggered_rule_ids, vec![1]);
    }

    // The strongest action wins and scores add up.
    #[test]
    fn strongest_action_and_summed_score_win() {
        let b = bundle(vec![
            rule(
                1,
                Condition::Cmp {
                    field: "ip_vpn_or_proxy".into(),
                    op: Op::Eq,
                    value: Value::Bool(true),
                },
                Action::Alert,
                20,
            ),
            rule(
                2,
                Condition::Cmp {
                    field: "amount".into(),
                    op: Op::Gte,
                    value: Value::Num(5000.0),
                },
                Action::Hold,
                30,
            ),
        ]);
        let d = evaluate(
            &b,
            &features(&[
                ("ip_vpn_or_proxy", Value::Bool(true)),
                ("amount", Value::Num(9000.0)),
            ]),
        );
        assert_eq!(d.action, Action::Hold);
        assert_eq!(d.score, 50);
        assert_eq!(d.triggered_rule_ids, vec![1, 2]);
    }

    // Nested all/any/not groups combine as boolean algebra.
    #[test]
    fn and_or_not_grouping() {
        let cond = Condition::All {
            all: vec![
                Condition::Cmp {
                    field: "amount".into(),
                    op: Op::Gt,
                    value: Value::Num(1000.0),
                },
                Condition::Any {
                    any: vec![
                        Condition::Cmp {
                            field: "mcc".into(),
                            op: Op::In,
                            value: Value::Str("5411,5812,7995".into()),
                        },
                        Condition::Cmp {
                            field: "ip_vpn_or_proxy".into(),
                            op: Op::Eq,
                            value: Value::Bool(true),
                        },
                    ],
                },
                Condition::Not {
                    not: Box::new(Condition::Cmp {
                        field: "allowlisted".into(),
                        op: Op::Eq,
                        value: Value::Bool(true),
                    }),
                },
            ],
        };
        let mut r = rule(7, cond, Action::Hold, 40);
        r.description = Some("combo hit".into());
        let b = bundle(vec![r]);

        let hit = evaluate(
            &b,
            &features(&[
                ("amount", Value::Num(2000.0)),
                ("mcc", Value::Str("5812".into())),
                ("allowlisted", Value::Bool(false)),
            ]),
        );
        assert_eq!(hit.action, Action::Hold);
        assert_eq!(hit.reasons, vec!["combo hit".to_string()]);

        let miss = evaluate(
            &b,
            &features(&[
                ("amount", Value::Num(2000.0)),
                ("mcc", Value::Str("1234".into())),
                ("ip_vpn_or_proxy", Value::Bool(false)),
                ("allowlisted", Value::Bool(false)),
            ]),
        );
        assert_eq!(miss.action, Action::Allow);
        assert!(miss.triggered_rule_ids.is_empty());
    }

    // A comparison on an absent feature is false.
    #[test]
    fn missing_feature_does_not_trigger() {
        let b = bundle(vec![rule(
            1,
            Condition::Cmp {
                field: "absent".into(),
                op: Op::Gt,
                value: Value::Num(1.0),
            },
            Action::Block,
            99,
        )]);
        let d = evaluate(&b, &features(&[("amount", Value::Num(5.0))]));
        assert_eq!(d.action, Action::Allow);
    }

    // The exact JSON shape the control plane emits parses and evaluates.
    #[test]
    fn deserializes_control_plane_json_bundle() {
        let json = br#"{
          "version": 37,
          "psp_id": 42,
          "rules": [
            {"id": 100, "name": "VPN hold", "condition":
               {"type":"cmp","field":"ip_vpn_or_proxy","op":"EQ","value": true},
             "action": "HOLD", "score": 25, "priority": 5}
          ]
        }"#;
        let b = RuleBundle::from_json(json).expect("valid bundle json");
        assert_eq!(b.version, 37);
        let d = evaluate(&b, &features(&[("ip_vpn_or_proxy", Value::Bool(true))]));
        assert_eq!(d.action, Action::Hold);
        assert_eq!(d.score, 25);
        assert_eq!(d.triggered_rule_ids, vec![100]);
        assert_eq!(d.triggered_rule_version_ids, vec![None]);
    }

    // Rule version ids ride along with hits; FLAG and SUSPEND keep their own severities.
    #[test]
    fn hit_carries_rule_version_id_and_flag_suspend_stay_distinct() {
        let json = br#"{
          "version": 3,
          "psp_id": 42,
          "manifest_hash": "sha256:abc",
          "rules": [
            {"id": 1, "name": "flag", "rule_version_id": 11,
             "condition": {"type":"cmp","field":"amount","op":"GT","value": 1},
             "action": "FLAG", "score": 5, "priority": 1},
            {"id": 2, "name": "suspend", "rule_version_id": 22,
             "condition": {"type":"cmp","field":"amount","op":"GT","value": 10},
             "action": "SUSPEND", "score": 9, "priority": 1}
          ]
        }"#;
        let b = RuleBundle::from_json(json).expect("bundle");
        let flag_only = evaluate(&b, &features(&[("amount", Value::Num(5.0))]));
        assert_eq!(flag_only.action, Action::Flag);
        assert_eq!(flag_only.triggered_rule_version_ids, vec![Some(11)]);
        let both = evaluate(&b, &features(&[("amount", Value::Num(20.0))]));
        assert_eq!(both.action, Action::Suspend);
        assert_eq!(both.triggered_rule_ids, vec![1, 2]);
        assert_eq!(both.triggered_rule_version_ids, vec![Some(11), Some(22)]);
    }

    // IR v2: in_list matches case-insensitively and exists/const behave as specified.
    #[test]
    fn ir_v2_leaves_evaluate() {
        let json = br#"{
          "version": 4, "psp_id": 42, "ir_version": 2,
          "lists": {"high_risk_countries": ["IR", "kp"]},
          "rules": [
            {"id": 1, "name": "hrc", "condition": {"type":"in_list","field":"country_code","list":"high_risk_countries"},
             "action": "ALERT", "score": 50},
            {"id": 2, "name": "has ratio", "condition": {"type":"exists","field":"ratio"},
             "action": "ALERT", "score": 1},
            {"id": 3, "name": "inert", "condition": {"type":"const","value":false},
             "action": "BLOCK", "score": 99}
          ]
        }"#;
        let b = RuleBundle::from_json(json).expect("bundle");
        let d = evaluate(&b, &features(&[("country_code", Value::Str("Kp".into()))]));
        assert_eq!(d.triggered_rule_ids, vec![1]);
        let d = evaluate(
            &b,
            &features(&[
                ("country_code", Value::Str("KE".into())),
                ("ratio", Value::Num(0.0)),
            ]),
        );
        assert_eq!(d.triggered_rule_ids, vec![2]);
    }

    // A bundle whose in_list names a list it does not define is rejected at load.
    #[test]
    fn undefined_list_is_rejected() {
        let json = br#"{"version":1,"psp_id":1,"rules":[
          {"id":1,"name":"x","condition":{"type":"not","not":{"type":"in_list","field":"a","list":"nope"}},
           "action":"ALERT"}]}"#;
        let err = RuleBundle::from_json(json).unwrap_err();
        assert!(err.contains("nope"), "{err}");
    }

    // Unknown condition types still fail the whole bundle (fail-closed load).
    #[test]
    fn unknown_condition_type_is_rejected() {
        let json = br#"{"version":1,"psp_id":1,"rules":[
          {"id":1,"name":"x","condition":{"type":"regex","field":"a"},"action":"ALERT"}]}"#;
        assert!(RuleBundle::from_json(json).is_err());
    }

    // Null, array and object features count as absent.
    #[test]
    fn non_scalar_features_are_absent() {
        let f = features_from_json(br#"{"a":null,"b":[1],"c":{"x":1},"d":2,"e":"s","g":true}"#)
            .expect("object");
        assert_eq!(f.len(), 3);
        assert!(features_from_json(b"[1,2]").is_err());
    }

    // Only plain decimal literals are numbers.
    #[test]
    fn strict_numeric_strings() {
        assert_eq!(parse_num("12.5"), Some(12.5));
        assert_eq!(parse_num("-3e2"), Some(-300.0));
        assert_eq!(parse_num(".5"), Some(0.5));
        assert_eq!(parse_num("5."), Some(5.0));
        for bad in ["", " 5", "5 ", "inf", "NaN", "0x10", "1e", "+", ".", "5d"] {
            assert_eq!(parse_num(bad), None, "{bad:?}");
        }
    }

    /// One vector of the shared Rust/Java parity file.
    #[derive(Deserialize)]
    struct ParityCase {
        name: String,
        features: serde_json::Value,
        expect: ParityExpect,
    }

    /// Expected decision of a parity vector.
    #[derive(Deserialize)]
    struct ParityExpect {
        action: Action,
        score: i32,
        triggered_rule_ids: Vec<u64>,
    }

    /// The parity file: one bundle and its cases.
    #[derive(Deserialize)]
    struct ParityFile {
        bundle: serde_json::Value,
        cases: Vec<ParityCase>,
    }

    // Every shared vector evaluates to the decision the file records (the Java side runs the
    // same file through EdgeRuleInterpreter and, under -Pnative-core, through JNI).
    #[test]
    fn shared_parity_vectors() {
        let raw = include_str!("../../../test-vectors/rule_ir_parity.json");
        let file: ParityFile = serde_json::from_str(raw).expect("parity file");
        let bundle_json = serde_json::to_vec(&file.bundle).unwrap();
        let bundle = RuleBundle::from_json(&bundle_json).expect("parity bundle loads");
        assert!(
            file.cases.len() >= 20,
            "parity file must exercise every operator"
        );
        for case in &file.cases {
            let f = features_from_json(&serde_json::to_vec(&case.features).unwrap())
                .expect("features object");
            let d = evaluate(&bundle, &f);
            assert_eq!(d.action, case.expect.action, "case {}", case.name);
            assert_eq!(d.score, case.expect.score, "case {}", case.name);
            assert_eq!(
                d.triggered_rule_ids, case.expect.triggered_rule_ids,
                "case {}",
                case.name
            );
        }
    }
}
