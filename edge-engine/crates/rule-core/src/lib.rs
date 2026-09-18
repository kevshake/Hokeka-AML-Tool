//! Hokeka edge rule-evaluation core.
//!
//! Executes a signed **Rule IR** (compiled centrally from the `RuleDefinition` catalog and pulled
//! to the edge) against a transaction's features, producing a decision. This is the CPU-bound
//! kernel invoked once per transaction from the Java host over JNI. It has **no I/O and no
//! network** — feature values are supplied by the caller — so it is deterministic and fast.
//!
//! The IR mirrors the control-plane rule model: `all` (AND) / `any` (OR) / `not` groups over
//! field comparisons, each rule carrying an action + score, exactly as the Java
//! `DynamicRuleConverter` emits.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// A feature/field value available to rule conditions.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(untagged)]
pub enum Value {
    Num(f64),
    Bool(bool),
    Str(String),
}

impl Value {
    fn as_num(&self) -> Option<f64> {
        match self {
            Value::Num(n) => Some(*n),
            Value::Bool(b) => Some(if *b { 1.0 } else { 0.0 }),
            Value::Str(s) => s.parse::<f64>().ok(),
        }
    }
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

/// A boolean condition tree. `all` = AND, `any` = OR, `not` = negation, `cmp` = field comparison.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum Condition {
    All { all: Vec<Condition> },
    Any { any: Vec<Condition> },
    Not { not: Box<Condition> },
    Cmp { field: String, op: Op, value: Value },
}

/// Terminal action a rule can assert; ordered by severity (Block strongest).
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "UPPERCASE")]
pub enum Action {
    Allow,
    Alert,
    Hold,
    Block,
}

impl Action {
    fn severity(self) -> u8 {
        match self {
            Action::Allow => 0,
            Action::Alert => 1,
            Action::Hold => 2,
            Action::Block => 3,
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
}

/// A versioned, per-PSP bundle of rules — the plaintext inside an HSE-1 envelope.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuleBundle {
    pub version: u64,
    pub psp_id: i64,
    pub rules: Vec<Rule>,
}

impl RuleBundle {
    pub fn from_json(bytes: &[u8]) -> Result<Self, serde_json::Error> {
        serde_json::from_slice(bytes)
    }
}

/// The evaluation result relayed back to the PSP API node.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Decision {
    pub action: Action,
    pub score: i32,
    pub triggered_rule_ids: Vec<u64>,
    pub reasons: Vec<String>,
}

/// Feature context for one transaction (field name -> value).
pub type Features = HashMap<String, Value>;

/// Evaluate a transaction's features against a bundle. Pure function, no I/O.
pub fn evaluate(bundle: &RuleBundle, features: &Features) -> Decision {
    let mut action = Action::Allow;
    let mut score: i32 = 0;
    let mut triggered = Vec::new();
    let mut reasons = Vec::new();

    for rule in &bundle.rules {
        if eval_condition(&rule.condition, features) {
            action = action.max(rule.action);
            score = score.saturating_add(rule.score);
            triggered.push(rule.id);
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
        reasons,
    }
}

fn eval_condition(cond: &Condition, f: &Features) -> bool {
    match cond {
        Condition::All { all } => all.iter().all(|c| eval_condition(c, f)),
        Condition::Any { any } => any.iter().any(|c| eval_condition(c, f)),
        Condition::Not { not } => !eval_condition(not, f),
        Condition::Cmp { field, op, value } => eval_cmp(f.get(field), *op, value),
    }
}

fn eval_cmp(actual: Option<&Value>, op: Op, expected: &Value) -> bool {
    let actual = match actual {
        Some(v) => v,
        None => return false, // a missing feature never satisfies a comparison
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
        Op::Contains => match (actual, expected) {
            (Value::Str(a), Value::Str(b)) => a.contains(b.as_str()),
            _ => false,
        },
    }
}

fn values_equal(a: &Value, b: &Value) -> bool {
    match (a.as_num(), b.as_num()) {
        (Some(x), Some(y)) => (x - y).abs() < f64::EPSILON,
        _ => value_to_string(a) == value_to_string(b),
    }
}

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

    fn features(pairs: &[(&str, Value)]) -> Features {
        pairs
            .iter()
            .map(|(k, v)| (k.to_string(), v.clone()))
            .collect()
    }

    fn bundle(rules: Vec<Rule>) -> RuleBundle {
        RuleBundle {
            version: 1,
            psp_id: 42,
            rules,
        }
    }

    #[test]
    fn high_value_blocks() {
        let b = bundle(vec![Rule {
            id: 1,
            name: "High value".into(),
            description: None,
            condition: Condition::Cmp {
                field: "amount".into(),
                op: Op::Gt,
                value: Value::Num(10000.0),
            },
            action: Action::Block,
            score: 50,
            priority: 10,
        }]);
        let d = evaluate(&b, &features(&[("amount", Value::Num(25000.0))]));
        assert_eq!(d.action, Action::Block);
        assert_eq!(d.score, 50);
        assert_eq!(d.triggered_rule_ids, vec![1]);
    }

    #[test]
    fn strongest_action_and_summed_score_win() {
        let b = bundle(vec![
            Rule {
                id: 1,
                name: "vpn".into(),
                description: None,
                condition: Condition::Cmp {
                    field: "ip_vpn_or_proxy".into(),
                    op: Op::Eq,
                    value: Value::Bool(true),
                },
                action: Action::Alert,
                score: 20,
                priority: 1,
            },
            Rule {
                id: 2,
                name: "amt".into(),
                description: None,
                condition: Condition::Cmp {
                    field: "amount".into(),
                    op: Op::Gte,
                    value: Value::Num(5000.0),
                },
                action: Action::Hold,
                score: 30,
                priority: 1,
            },
        ]);
        let d = evaluate(
            &b,
            &features(&[
                ("ip_vpn_or_proxy", Value::Bool(true)),
                ("amount", Value::Num(9000.0)),
            ]),
        );
        assert_eq!(d.action, Action::Hold); // stronger than Alert
        assert_eq!(d.score, 50);
        assert_eq!(d.triggered_rule_ids, vec![1, 2]);
    }

    #[test]
    fn and_or_not_grouping() {
        // (amount > 1000 AND (mcc IN {5411,5812} OR ip_vpn_or_proxy == true)) AND NOT allowlisted
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
        let b = bundle(vec![Rule {
            id: 7,
            name: "combo".into(),
            description: Some("combo hit".into()),
            condition: cond,
            action: Action::Hold,
            score: 40,
            priority: 5,
        }]);

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

    #[test]
    fn missing_feature_does_not_trigger() {
        let b = bundle(vec![Rule {
            id: 1,
            name: "x".into(),
            description: None,
            condition: Condition::Cmp {
                field: "absent".into(),
                op: Op::Gt,
                value: Value::Num(1.0),
            },
            action: Action::Block,
            score: 99,
            priority: 1,
        }]);
        let d = evaluate(&b, &features(&[("amount", Value::Num(5.0))]));
        assert_eq!(d.action, Action::Allow);
    }

    #[test]
    fn deserializes_control_plane_json_bundle() {
        // Exactly the shape the Java control plane emits as the HSE-1 payload.
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
    }
}
