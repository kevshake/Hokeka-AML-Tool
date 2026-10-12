import { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  IconButton,
  Select,
  MenuItem,
  TextField,
  Divider
} from '@mui/material';
import { Add, Delete, DragIndicator } from '@mui/icons-material';

interface Condition {
  id: string;
  field: string;
  operator: string;
  value: string;
}

interface RuleGroup {
  id: string;
  logic: 'AND' | 'OR';
  conditions: Condition[];
}

interface VisualRuleBuilderProps {
  onChange: (expression: string, json: any) => void;
  onSave?: (rule: any) => void;
}

export default function VisualRuleBuilder({ onChange, onSave }: VisualRuleBuilderProps) {
  const [groups, setGroups] = useState<RuleGroup[]>([
    {
      id: 'group-1',
      logic: 'AND',
      conditions: [{ id: 'cond-1', field: 'amount', operator: '>=', value: '10000' }]
    }
  ]);

  const fields = [
    'amount', 'currency', 'country', 'merchantId', 'deviceFingerprint',
    'ipAddress', 'velocityScore', 'roundAmount', 'levenshteinDistance'
  ];

  const operators = ['>=', '<=', '>', '<', '==', '!=', 'contains', 'in'];

  const canonicalOperator: Record<string, string> = {
    '>=': 'GREATER_THAN_OR_EQUAL',
    '<=': 'LESS_THAN_OR_EQUAL',
    '>': 'GREATER_THAN',
    '<': 'LESS_THAN',
    '==': 'EQUALS',
    '!=': 'NOT_EQUALS',
    contains: 'CONTAINS',
    in: 'IN',
  };

  const edgeField = (field: string) => (field === 'country' ? 'country_code' : field);

  const coerceValue = (raw: string) => {
    if (raw === 'true') return true;
    if (raw === 'false') return false;
    if (raw !== '' && !Number.isNaN(Number(raw))) return Number(raw);
    return raw;
  };

  /** Shape both the Control Plane DRL converter and the Edge rule compiler accept. */
  const canonicalJson = (ruleGroups: RuleGroup[]) => {
    const parts = ruleGroups.map((group) => {
      const leaves = group.conditions.map((cond) => ({
        field: edgeField(cond.field),
        operator: canonicalOperator[cond.operator] ?? cond.operator,
        value: coerceValue(cond.value),
      }));
      return leaves.length === 1 ? leaves[0] : { all: leaves };
    });
    let condition: unknown = parts[0];
    for (let i = 1; i < parts.length; i += 1) {
      const key = ruleGroups[i].logic === 'OR' ? 'any' : 'all';
      condition = { [key]: [condition, parts[i]] };
    }
    return { conditions: condition };
  };

  const updateExpression = (newGroups: RuleGroup[]) => {
    let expression = '';
    const json = canonicalJson(newGroups);

    newGroups.forEach((group, gIndex) => {
      if (gIndex > 0) expression += ` ${group.logic} `;
      expression += '(';
      group.conditions.forEach((cond, cIndex) => {
        if (cIndex > 0) expression += ' AND ';
        expression += `#tx.${cond.field} ${cond.operator} ${cond.value}`;
      });
      expression += ')';
    });

    onChange(expression, json);
  };

  const addCondition = (groupId: string) => {
    const newGroups = groups.map(group => {
      if (group.id === groupId) {
        return {
          ...group,
          conditions: [
            ...group.conditions,
            {
              id: `cond-${Date.now()}`,
              field: 'amount',
              operator: '>=',
              value: '0'
            }
          ]
        };
      }
      return group;
    });
    setGroups(newGroups);
    updateExpression(newGroups);
  };

  const removeCondition = (groupId: string, conditionId: string) => {
    const newGroups = groups.map(group => {
      if (group.id === groupId) {
        return {
          ...group,
          conditions: group.conditions.filter(c => c.id !== conditionId)
        };
      }
      return group;
    });
    setGroups(newGroups);
    updateExpression(newGroups);
  };

  const updateCondition = (groupId: string, conditionId: string, field: string, value: string) => {
    const newGroups = groups.map(group => {
      if (group.id === groupId) {
        return {
          ...group,
          conditions: group.conditions.map(cond =>
            cond.id === conditionId ? { ...cond, [field]: value } : cond
          )
        };
      }
      return group;
    });
    setGroups(newGroups);
    updateExpression(newGroups);
  };

  const addGroup = () => {
    const newGroup: RuleGroup = {
      id: `group-${Date.now()}`,
      logic: 'AND',
      conditions: [{ id: `cond-${Date.now()}`, field: 'amount', operator: '>=', value: '10000' }]
    };
    const newGroups = [...groups, newGroup];
    setGroups(newGroups);
    updateExpression(newGroups);
  };

  return (
    <Paper sx={{ p: 3, backgroundColor: 'var(--surface-0)', color: 'white' }}>
      <Typography variant="h6" gutterBottom>
        Visual Rule Builder
      </Typography>

      {groups.map((group, index) => (
        <Box key={group.id} sx={{ mb: 3, p: 2, border: '1px solid #444', borderRadius: 1 }}>
          <Box display="flex" alignItems="center" gap={2} mb={2}>
            <DragIndicator />
            <Typography>Group {index + 1}</Typography>
            <Select
              value={group.logic}
              onChange={(e) => {
                const newGroups = groups.map(g =>
                  g.id === group.id ? { ...g, logic: e.target.value as 'AND' | 'OR' } : g
                );
                setGroups(newGroups);
                updateExpression(newGroups);
              }}
              size="small"
              sx={{ color: 'white', minWidth: 80 }}
            >
              <MenuItem value="AND">AND</MenuItem>
              <MenuItem value="OR">OR</MenuItem>
            </Select>
            <Button startIcon={<Add />} onClick={() => addCondition(group.id)} size="small">
              Add Condition
            </Button>
          </Box>

          {group.conditions.map((cond) => (
            <Box key={cond.id} display="flex" gap={1} alignItems="center" mb={1}>
              <Select
                value={cond.field}
                onChange={(e) => updateCondition(group.id, cond.id, 'field', e.target.value)}
                size="small"
              >
                {fields.map(f => <MenuItem key={f} value={f}>{f}</MenuItem>)}
              </Select>

              <Select
                value={cond.operator}
                onChange={(e) => updateCondition(group.id, cond.id, 'operator', e.target.value)}
                size="small"
              >
                {operators.map(op => <MenuItem key={op} value={op}>{op}</MenuItem>)}
              </Select>

              <TextField
                value={cond.value}
                onChange={(e) => updateCondition(group.id, cond.id, 'value', e.target.value)}
                size="small"
                sx={{ width: 120 }}
              />

              <IconButton onClick={() => removeCondition(group.id, cond.id)} color="error">
                <Delete />
              </IconButton>
            </Box>
          ))}
        </Box>
      ))}

      <Button variant="outlined" onClick={addGroup} startIcon={<Add />}>
        Add Logic Group
      </Button>
      {onSave && (
        <Button
          variant="contained"
          onClick={() =>
            onSave({
              ruleName: "Visual Rule",
              ruleExpression: groups
                .map((g) => `(${g.conditions.map((c) => `#tx.${c.field} ${c.operator} ${c.value}`).join(" AND ")})`)
                .join(` ${groups[0]?.logic ?? "OR"} `),
              ruleType: "SPEL",
              ruleJson: JSON.stringify(canonicalJson(groups)),
            })
          }
          sx={{ ml: 2 }}
        >
        Save Rule
        </Button>
      )}

      <Divider sx={{ my: 2 }} />

      <Typography variant="subtitle2" color="text.secondary">
        Generated Expression:
      </Typography>
      <Box sx={{ fontFamily: 'monospace', bgcolor: '#111', p: 2, borderRadius: 1, mt: 1 }}>
        {groups.map((g, i) => (
          <div key={i}>
            {i > 0 && ` ${g.logic} `}
            ({g.conditions.map(c => `#tx.${c.field} ${c.operator} ${c.value}`).join(' AND ')})
          </div>
        ))}
      </Box>
    </Paper>
  );
}
