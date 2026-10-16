#!/usr/bin/env node
/**
 * Fails the process when Docker is reachable but required Testcontainers integration
 * tests were skipped or never executed (silent skip regression on Docker 29 / Windows).
 */
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

/** Test class simple names that must run (not skip) whenever Docker is available. */
const REQUIRED_CLASSES = [
  'FlywayFreshDatabaseIntegrationTest',
  'KafkaNeo4jContainerSmokeTest',
  'ConsoleListKeysetBenchmarkIT',
];

/** Returns true when the Docker daemon responds to `docker info`. */
function isDockerAvailable() {
  try {
    execSync('docker info', { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

/** Parses a Surefire TEST-*.xml file and returns { classname, tests, skipped, failures, errors }. */
function parseSurefireXml(filePath) {
  const xml = fs.readFileSync(filePath, 'utf8');
  const classMatch = xml.match(/classname="([^"]+)"/);
  const tests = Number(xml.match(/tests="(\d+)"/)?.[1] ?? '0');
  const skipped = Number(xml.match(/skipped="(\d+)"/)?.[1] ?? '0');
  const failures = Number(xml.match(/failures="(\d+)"/)?.[1] ?? '0');
  const errors = Number(xml.match(/errors="(\d+)"/)?.[1] ?? '0');
  const simple = classMatch?.[1]?.split('.').pop() ?? path.basename(filePath);
  return { classname: simple, tests, skipped, failures, errors };
}

/** Collects Surefire summaries for each required class under reportsDir. */
function collectRequiredSummaries(reportsDir) {
  const summaries = new Map(REQUIRED_CLASSES.map((name) => [name, null]));
  if (!fs.existsSync(reportsDir)) {
    return summaries;
  }
  for (const entry of fs.readdirSync(reportsDir)) {
    if (!entry.startsWith('TEST-') || !entry.endsWith('.xml')) {
      continue;
    }
    const parsed = parseSurefireXml(path.join(reportsDir, entry));
    if (summaries.has(parsed.classname)) {
      summaries.set(parsed.classname, parsed);
    }
  }
  return summaries;
}

/** Prints BENCHMARK=* lines from Surefire system-out for CI visibility. */
function printBenchmarkLines(reportsDir) {
  if (!fs.existsSync(reportsDir)) {
    return;
  }
  const pattern = /BENCHMARK[^\n]*/g;
  for (const entry of fs.readdirSync(reportsDir)) {
    if (!entry.includes('ConsoleListKeysetBenchmarkIT') || !entry.endsWith('.xml')) {
      continue;
    }
    const xml = fs.readFileSync(path.join(reportsDir, entry), 'utf8');
    const chunks = xml.match(/<system-out><!\[CDATA\[([\s\S]*?)\]\]><\/system-out>/g) ?? [];
    for (const chunk of chunks) {
      const inner = chunk.replace(/<\/?system-out><!\[CDATA\[|\]\]><\/system-out>/g, '');
      const matches = inner.match(pattern);
      if (matches) {
        for (const line of matches) {
          console.log(line);
        }
      }
    }
  }
}

/** Entry point: validate reports when Docker is present. */
function main() {
  const reportsDir = process.argv[2] ?? 'BACKEND/target/surefire-reports';
  if (!isDockerAvailable()) {
    console.log('Docker unavailable — not enforcing Testcontainers IT execution.');
    process.exit(0);
  }

  const summaries = collectRequiredSummaries(reportsDir);
  let failed = false;
  for (const className of REQUIRED_CLASSES) {
    const summary = summaries.get(className);
    if (!summary) {
      console.error(`::error::Missing Surefire report for ${className} (class did not run).`);
      failed = true;
      continue;
    }
    if (summary.tests === 0 || summary.skipped > 0) {
      console.error(
        `::error::${className} must run under Docker (tests=${summary.tests}, skipped=${summary.skipped}).`,
      );
      failed = true;
    }
    if (summary.failures > 0 || summary.errors > 0) {
      console.error(
        `::error::${className} failed (failures=${summary.failures}, errors=${summary.errors}).`,
      );
      failed = true;
    }
  }

  printBenchmarkLines(reportsDir);
  process.exit(failed ? 1 : 0);
}

main();
