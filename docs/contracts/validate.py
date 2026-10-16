#!/usr/bin/env python3
"""Validate the Edge data residency contract fixtures against their JSON Schemas.

Usage (from anywhere):  py docs/contracts/validate.py      (or: python3 docs/contracts/validate.py)

Standard library only. It implements the subset of JSON Schema draft 2020-12 that the schemas in
docs/contracts use, and refuses any schema keyword outside that subset, so a schema can never rely
on a keyword this script silently ignores.

What it checks
  1. Every *.schema.json under docs/contracts declares draft 2020-12 and an $id equal to
     https://contracts.hokeka.com/edge/<path relative to docs/contracts>, and uses only supported
     keywords.
  2. Every fixture under docs/contracts/tunnel/examples is mapped to a schema by its file name:
     '<schema stem>--<label>.json' validates against '<schema stem>.schema.json' (the stem must
     name exactly one schema). Valid fixtures must produce no errors.
  3. Fixtures named '*.invalid.json' must fail, and at least one error must contain the substring
     recorded for that file in examples/expected-failures.json (so they fail for the intended
     reason, not by accident). Every manifest entry must name an existing invalid fixture.
  4. Every schema is exercised by at least one valid fixture (directly or through $ref).

Semantics notes
  * 'format' is asserted (date-time, date, uuid), not just annotated.
  * 'x-hokeka-luhnReject' (Hokeka extension): a string that, after removing spaces and hyphens,
    is 13-19 digits and passes the Luhn check is rejected as a probable PAN.
  * 'pattern' uses Python 're' search semantics; the schemas only use the ECMA-262/Python common
    subset (character classes, anchors, quantifiers, groups, \\x escapes).

Exit status 0 when everything passes, 1 otherwise.
"""
from __future__ import annotations

import datetime as _dt
import json
import re
import sys
from pathlib import Path
from urllib.parse import urldefrag, urljoin

ROOT = Path(__file__).resolve().parent
EXAMPLES = ROOT / "tunnel" / "examples"
MANIFEST = EXAMPLES / "expected-failures.json"
BASE_URI = "https://contracts.hokeka.com/edge/"
DRAFT = "https://json-schema.org/draft/2020-12/schema"

ANNOTATIONS = {
    "$schema", "$id", "$comment", "$defs", "title", "description", "default", "examples",
    "deprecated", "readOnly", "writeOnly",
}
ASSERTIONS = {
    "$ref", "type", "enum", "const", "properties", "required", "additionalProperties",
    "patternProperties", "propertyNames", "minProperties", "maxProperties", "items",
    "prefixItems", "minItems", "maxItems", "uniqueItems", "minLength", "maxLength", "pattern",
    "format", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "allOf", "anyOf",
    "oneOf", "not", "if", "then", "else", "x-hokeka-luhnReject",
}
SUPPORTED = ANNOTATIONS | ASSERTIONS
# Keywords whose value is a map of name -> subschema / a list of subschemas / one subschema.
MAP_OF_SCHEMAS = {"properties", "patternProperties", "$defs"}
LIST_OF_SCHEMAS = {"allOf", "anyOf", "oneOf", "prefixItems"}
ONE_SCHEMA = {"additionalProperties", "propertyNames", "items", "not", "if", "then", "else"}

UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.I)


class SchemaError(Exception):
    """A schema is malformed or uses an unsupported construct."""


# ── helpers ──────────────────────────────────────────────────────────────────────────────────────

def luhn_pan_like(value: str) -> bool:
    digits = re.sub(r"[ -]", "", value)
    if not digits.isdigit() or not 13 <= len(digits) <= 19:
        return False
    total = 0
    for i, ch in enumerate(reversed(digits)):
        d = ord(ch) - 48
        if i % 2 == 1:
            d *= 2
            if d > 9:
                d -= 9
        total += d
    return total % 10 == 0


def type_matches(value, name: str) -> bool:
    if name == "null":
        return value is None
    if name == "boolean":
        return isinstance(value, bool)
    if name == "integer":
        return (isinstance(value, int) and not isinstance(value, bool)) or (
            isinstance(value, float) and value.is_integer())
    if name == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if name == "string":
        return isinstance(value, str)
    if name == "array":
        return isinstance(value, list)
    if name == "object":
        return isinstance(value, dict)
    raise SchemaError(f"unknown type name {name!r}")


def json_equal(a, b) -> bool:
    """Equality per JSON Schema: booleans never equal numbers, 1 == 1.0."""
    if isinstance(a, bool) or isinstance(b, bool):
        return isinstance(a, bool) and isinstance(b, bool) and a == b
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return a == b
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(json_equal(x, y) for x, y in zip(a, b))
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(json_equal(a[k], b[k]) for k in a)
    return type(a) is type(b) and a == b


def check_format(value: str, fmt: str) -> bool:
    if fmt == "uuid":
        return bool(UUID_RE.match(value))
    if fmt == "date":
        try:
            _dt.date.fromisoformat(value)
            return bool(re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value))
        except ValueError:
            return False
    if fmt == "date-time":
        m = re.fullmatch(
            r"([0-9]{4}-[0-9]{2}-[0-9]{2})[Tt]([0-9]{2}):([0-9]{2}):([0-9]{2})(\.[0-9]+)?"
            r"([Zz]|[+-][0-9]{2}:[0-9]{2})", value)
        if not m:
            return False
        try:
            _dt.date.fromisoformat(m.group(1))
        except ValueError:
            return False
        hh, mm, ss = int(m.group(2)), int(m.group(3)), int(m.group(4))
        return hh < 24 and mm < 60 and ss < 61
    raise SchemaError(f"unsupported format {fmt!r}")


def fmt_path(path: str) -> str:
    return path or "$"


# ── registry & validator ─────────────────────────────────────────────────────────────────────────

class Registry:
    def __init__(self) -> None:
        self.docs: dict[str, dict] = {}
        self.files: dict[str, Path] = {}
        self.touched: set[str] = set()

    def add(self, uri: str, doc: dict, path: Path) -> None:
        if uri in self.docs:
            raise SchemaError(f"duplicate $id {uri} ({self.files[uri]} and {path})")
        self.docs[uri] = doc
        self.files[uri] = path

    def resolve(self, base: str, ref: str):
        target = urljoin(base, ref)
        doc_uri, fragment = urldefrag(target)
        if doc_uri not in self.docs:
            raise SchemaError(f"$ref {ref!r} from {base} resolves to unknown document {doc_uri}")
        self.touched.add(doc_uri)
        node = self.docs[doc_uri]
        if fragment:
            if not fragment.startswith("/"):
                raise SchemaError(f"only JSON-pointer fragments are supported: {ref!r}")
            for raw in fragment[1:].split("/"):
                key = raw.replace("~1", "/").replace("~0", "~")
                if isinstance(node, list):
                    node = node[int(key)]
                elif isinstance(node, dict) and key in node:
                    node = node[key]
                else:
                    raise SchemaError(f"$ref {ref!r} from {base}: pointer segment {key!r} not found")
        return doc_uri, node


class Validator:
    def __init__(self, registry: Registry) -> None:
        self.reg = registry

    def validate(self, inst, schema, base: str, path: str = "") -> list[str]:
        if schema is True:
            return []
        if schema is False:
            return [f"{fmt_path(path)}: no value is allowed here"]
        if not isinstance(schema, dict):
            raise SchemaError(f"schema at {base} is not an object: {schema!r}")
        errs: list[str] = []
        p = fmt_path(path)

        if "$ref" in schema:
            doc_uri, target = self.reg.resolve(base, schema["$ref"])
            errs += self.validate(inst, target, doc_uri, path)

        if "type" in schema:
            names = schema["type"] if isinstance(schema["type"], list) else [schema["type"]]
            if not any(type_matches(inst, n) for n in names):
                errs.append(f"{p}: expected type {'/'.join(names)}, got {type(inst).__name__}")
                return errs  # further keywords would only add noise
        if "enum" in schema and not any(json_equal(inst, e) for e in schema["enum"]):
            errs.append(f"{p}: {inst!r} is not one of {schema['enum']}")
        if "const" in schema and not json_equal(inst, schema["const"]):
            errs.append(f"{p}: must equal const {schema['const']!r}")

        if isinstance(inst, str):
            if "minLength" in schema and len(inst) < schema["minLength"]:
                errs.append(f"{p}: shorter than minLength {schema['minLength']}")
            if "maxLength" in schema and len(inst) > schema["maxLength"]:
                errs.append(f"{p}: longer than maxLength {schema['maxLength']}")
            if "pattern" in schema and not re.search(schema["pattern"], inst):
                errs.append(f"{p}: does not match pattern {schema['pattern']}")
            if "format" in schema and not check_format(inst, schema["format"]):
                errs.append(f"{p}: not a valid {schema['format']}")
            if schema.get("x-hokeka-luhnReject") and luhn_pan_like(inst):
                errs.append(f"{p}: value looks like a PAN (13-19 digits passing Luhn) - x-hokeka-luhnReject")

        if isinstance(inst, (int, float)) and not isinstance(inst, bool):
            if "minimum" in schema and inst < schema["minimum"]:
                errs.append(f"{p}: {inst} is below minimum {schema['minimum']}")
            if "maximum" in schema and inst > schema["maximum"]:
                errs.append(f"{p}: {inst} is above maximum {schema['maximum']}")
            if "exclusiveMinimum" in schema and inst <= schema["exclusiveMinimum"]:
                errs.append(f"{p}: {inst} is not above exclusiveMinimum {schema['exclusiveMinimum']}")
            if "exclusiveMaximum" in schema and inst >= schema["exclusiveMaximum"]:
                errs.append(f"{p}: {inst} is not below exclusiveMaximum {schema['exclusiveMaximum']}")

        if isinstance(inst, list):
            if "minItems" in schema and len(inst) < schema["minItems"]:
                errs.append(f"{p}: fewer than minItems {schema['minItems']}")
            if "maxItems" in schema and len(inst) > schema["maxItems"]:
                errs.append(f"{p}: more than maxItems {schema['maxItems']} ({len(inst)})")
            if schema.get("uniqueItems"):
                for i in range(len(inst)):
                    if any(json_equal(inst[i], inst[j]) for j in range(i)):
                        errs.append(f"{p}: items are not unique (index {i})")
                        break
            prefix = schema.get("prefixItems", [])
            for i, sub in enumerate(prefix[: len(inst)]):
                errs += self.validate(inst[i], sub, base, f"{path}[{i}]")
            if "items" in schema:
                for i in range(len(prefix), len(inst)):
                    errs += self.validate(inst[i], schema["items"], base, f"{path}[{i}]")

        if isinstance(inst, dict):
            if "minProperties" in schema and len(inst) < schema["minProperties"]:
                errs.append(f"{p}: fewer than minProperties {schema['minProperties']}")
            if "maxProperties" in schema and len(inst) > schema["maxProperties"]:
                errs.append(f"{p}: more than maxProperties {schema['maxProperties']}")
            for name in schema.get("required", []):
                if name not in inst:
                    errs.append(f"{p}: missing required property '{name}'")
            props = schema.get("properties", {})
            pprops = schema.get("patternProperties", {})
            for key, val in inst.items():
                kp = f"{path}.{key}"
                matched = False
                if key in props:
                    matched = True
                    errs += self.validate(val, props[key], base, kp)
                for pat, sub in pprops.items():
                    if re.search(pat, key):
                        matched = True
                        errs += self.validate(val, sub, base, kp)
                if "propertyNames" in schema:
                    for e in self.validate(key, schema["propertyNames"], base, kp):
                        errs.append(f"{kp}: property name rejected ({e})")
                if not matched and "additionalProperties" in schema:
                    ap = schema["additionalProperties"]
                    if ap is False:
                        errs.append(f"{kp}: additional property '{key}' is not allowed")
                    else:
                        errs += self.validate(val, ap, base, kp)

        for sub in schema.get("allOf", []):
            errs += self.validate(inst, sub, base, path)
        if "anyOf" in schema:
            branch_errs = [self.validate(inst, sub, base, path) for sub in schema["anyOf"]]
            if all(branch_errs):
                errs.append(f"{p}: matches none of anyOf: " + " | ".join("; ".join(b) for b in branch_errs))
        if "oneOf" in schema:
            branch_errs = [self.validate(inst, sub, base, path) for sub in schema["oneOf"]]
            ok = sum(1 for b in branch_errs if not b)
            if ok == 0:
                errs.append(f"{p}: matches none of oneOf: " + " | ".join("; ".join(b) for b in branch_errs))
            elif ok > 1:
                errs.append(f"{p}: matches {ok} oneOf branches, exactly one required")
        if "not" in schema and not self.validate(inst, schema["not"], base, path):
            errs.append(f"{p}: must not match the 'not' schema")
        if "if" in schema:
            if not self.validate(inst, schema["if"], base, path):
                if "then" in schema:
                    errs += self.validate(inst, schema["then"], base, path)
            elif "else" in schema:
                errs += self.validate(inst, schema["else"], base, path)
        return errs


# ── schema hygiene ───────────────────────────────────────────────────────────────────────────────

def check_keywords(schema, where: str) -> list[str]:
    problems: list[str] = []
    if isinstance(schema, bool):
        return problems
    if not isinstance(schema, dict):
        return [f"{where}: subschema is not an object or boolean"]
    for key, val in schema.items():
        if key not in SUPPORTED:
            problems.append(f"{where}: unsupported keyword '{key}'")
            continue
        if key in MAP_OF_SCHEMAS:
            for name, sub in val.items():
                problems += check_keywords(sub, f"{where}/{key}/{name}")
        elif key in LIST_OF_SCHEMAS:
            for i, sub in enumerate(val):
                problems += check_keywords(sub, f"{where}/{key}/{i}")
        elif key in ONE_SCHEMA:
            problems += check_keywords(val, f"{where}/{key}")
        elif key == "pattern":
            try:
                re.compile(val)
            except re.error as exc:
                problems.append(f"{where}: invalid pattern {val!r}: {exc}")
    return problems


def load_json(path: Path):
    with path.open(encoding="utf-8") as fh:
        return json.load(fh)


def main() -> int:
    failures: list[str] = []
    reg = Registry()
    stems: dict[str, list[str]] = {}

    schema_files = sorted(ROOT.rglob("*.schema.json"))
    for path in schema_files:
        rel = path.relative_to(ROOT).as_posix()
        try:
            doc = load_json(path)
        except json.JSONDecodeError as exc:
            failures.append(f"schema {rel}: invalid JSON: {exc}")
            continue
        if doc.get("$schema") != DRAFT:
            failures.append(f"schema {rel}: $schema must be {DRAFT}")
        expected_id = BASE_URI + rel
        if doc.get("$id") != expected_id:
            failures.append(f"schema {rel}: $id must be {expected_id}, is {doc.get('$id')}")
        failures += [f"schema {rel}: {p}" for p in check_keywords(doc, "#")]
        reg.add(doc.get("$id", expected_id), doc, path)
        stems.setdefault(path.name[: -len(".schema.json")], []).append(doc.get("$id", expected_id))

    # Every $ref in every schema must resolve (catches typos even in branches no fixture reaches).
    def walk_refs(node, base):
        if isinstance(node, dict):
            if "$ref" in node:
                try:
                    reg.resolve(base, node["$ref"])
                except (SchemaError, KeyError, IndexError, ValueError) as exc:
                    failures.append(f"unresolvable $ref: {exc}")
            for v in node.values():
                walk_refs(v, base)
        elif isinstance(node, list):
            for v in node:
                walk_refs(v, base)

    for uri, doc in reg.docs.items():
        walk_refs(doc, uri)
    reg.touched.clear()

    manifest = load_json(MANIFEST) if MANIFEST.exists() else {}
    validator = Validator(reg)
    exercised: set[str] = set()
    n_valid = n_invalid = 0

    example_files = sorted(p for p in EXAMPLES.glob("*.json") if p.name != MANIFEST.name)
    for path in example_files:
        name = path.name
        invalid = name.endswith(".invalid.json")
        base_name = name[: -len(".invalid.json")] if invalid else name[: -len(".json")]
        stem = base_name.split("--", 1)[0]
        targets = stems.get(stem, [])
        if len(targets) != 1:
            failures.append(f"example {name}: stem '{stem}' maps to {len(targets)} schemas (need exactly 1)")
            continue
        uri = targets[0]
        try:
            instance = load_json(path)
        except json.JSONDecodeError as exc:
            failures.append(f"example {name}: invalid JSON: {exc}")
            continue
        reg.touched.clear()
        reg.touched.add(uri)
        try:
            errors = validator.validate(instance, reg.docs[uri], uri)
        except SchemaError as exc:
            failures.append(f"example {name}: schema error: {exc}")
            continue
        schema_rel = reg.files[uri].relative_to(ROOT).as_posix()
        if invalid:
            n_invalid += 1
            expected = manifest.get(name)
            if expected is None:
                failures.append(f"example {name}: invalid fixture has no entry in {MANIFEST.name}")
            elif not errors:
                failures.append(f"example {name}: expected to FAIL against {schema_rel} but passed")
            elif not any(expected in e for e in errors):
                failures.append(f"example {name}: failed, but no error contains {expected!r}; got: {errors}")
            else:
                hit = next(e for e in errors if expected in e)
                print(f"  ok   (rejected) {name:<58} -> {hit}")
        else:
            n_valid += 1
            if errors:
                failures.append(f"example {name}: expected to pass {schema_rel}, got: " + "; ".join(errors))
            else:
                exercised |= reg.touched
                print(f"  ok   (valid)    {name:<58} -> {schema_rel}")

    for entry in manifest:
        if not (EXAMPLES / entry).exists():
            failures.append(f"{MANIFEST.name}: entry '{entry}' names a fixture that does not exist")
        elif not entry.endswith(".invalid.json"):
            failures.append(f"{MANIFEST.name}: entry '{entry}' is not an *.invalid.json fixture")

    for uri, path in reg.files.items():
        if uri not in exercised:
            failures.append(f"schema {path.relative_to(ROOT).as_posix()} is not exercised by any valid example")

    print()
    print(f"schemas: {len(schema_files)}   valid fixtures: {n_valid}   invalid fixtures: {n_invalid}")
    if failures:
        print(f"FAILED ({len(failures)} problem(s)):")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("PASS: every valid fixture validates, every invalid fixture fails for its recorded reason, "
          "every schema is exercised.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
