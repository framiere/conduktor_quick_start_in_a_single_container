#!/usr/bin/env node

import { readFileSync, writeFileSync, readdirSync, existsSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const projectRoot = join(__dirname, '../../..');
const surefireDir = join(projectRoot, 'target/surefire-reports');
const failsafeDir = join(projectRoot, 'target/failsafe-reports');
const outputDir = join(__dirname, '../src/data');

function parseXmlAttribute(xml, attrName) {
  const regex = new RegExp(`${attrName}="([^"]*)"`, 'i');
  const match = xml.match(regex);
  return match ? match[1] : null;
}

function parseTestCases(xml) {
  const testCases = [];
  const testCaseRegex = /<testcase\s+([^>]*?)(?:\/>|>[\s\S]*?<\/testcase>)/gi;
  let match;

  while ((match = testCaseRegex.exec(xml)) !== null) {
    const attrs = match[1];
    const fullMatch = match[0];

    const name = parseXmlAttribute(attrs, 'name');
    const classname = parseXmlAttribute(attrs, 'classname');
    const time = parseFloat(parseXmlAttribute(attrs, 'time') || '0');

    const hasFailure = fullMatch.includes('<failure');
    const hasError = fullMatch.includes('<error');
    const hasSkipped = fullMatch.includes('<skipped');

    let status = 'passed';
    let failureMessage = null;

    if (hasFailure || hasError) {
      status = 'failed';
      const failureMatch = fullMatch.match(/<failure[^>]*message="([^"]*)"/);
      failureMessage = failureMatch ? failureMatch[1] : 'Test failed';
    } else if (hasSkipped) {
      status = 'skipped';
    }

    testCases.push({ name, classname, time, status, failureMessage });
  }

  return testCases;
}

function parseTestSuite(filePath) {
  const xml = readFileSync(filePath, 'utf-8');

  const name = parseXmlAttribute(xml, 'name');
  const time = parseFloat(parseXmlAttribute(xml, 'time') || '0');
  const tests = parseInt(parseXmlAttribute(xml, 'tests') || '0', 10);
  const errors = parseInt(parseXmlAttribute(xml, 'errors') || '0', 10);
  const skipped = parseInt(parseXmlAttribute(xml, 'skipped') || '0', 10);
  const failures = parseInt(parseXmlAttribute(xml, 'failures') || '0', 10);

  const testCases = parseTestCases(xml);

  return {
    name,
    time,
    tests,
    errors,
    skipped,
    failures,
    passed: tests - errors - failures - skipped,
    testCases
  };
}

function categorizeTestClass(className) {
  if (className.includes('.crd.')) return 'CRD';
  if (className.includes('.store.')) return 'Store';
  if (className.includes('.validation.')) return 'Validation';
  if (className.includes('.webhook.')) return 'Webhook';
  if (className.includes('.events.')) return 'Events';
  if (className.includes('.e2e.')) return 'E2E';
  if (className.includes('.it.scenario.')) return 'Scenario';
  if (className.includes('.it.component.')) return 'Component';
  return 'Other';
}

function processReports(dir, type) {
  if (!existsSync(dir)) {
    console.log(`Directory not found: ${dir}`);
    return [];
  }

  const files = readdirSync(dir).filter(f => f.startsWith('TEST-') && f.endsWith('.xml'));
  const suites = [];

  for (const file of files) {
    try {
      const suite = parseTestSuite(join(dir, file));
      if (suite.tests > 0) {
        suite.type = type;
        suite.category = categorizeTestClass(suite.name);
        suites.push(suite);
      }
    } catch (error) {
      console.error(`Error parsing ${file}:`, error.message);
    }
  }

  return suites;
}

function generateReport() {
  console.log('Parsing test reports...');

  const unitTests = processReports(surefireDir, 'unit');
  const integrationTests = processReports(failsafeDir, 'integration');

  const allSuites = [...unitTests, ...integrationTests];

  const summary = {
    timestamp: new Date().toISOString(),
    unit: {
      suites: unitTests.length,
      tests: unitTests.reduce((sum, s) => sum + s.tests, 0),
      passed: unitTests.reduce((sum, s) => sum + s.passed, 0),
      failed: unitTests.reduce((sum, s) => sum + s.failures, 0),
      errors: unitTests.reduce((sum, s) => sum + s.errors, 0),
      skipped: unitTests.reduce((sum, s) => sum + s.skipped, 0),
      time: unitTests.reduce((sum, s) => sum + s.time, 0)
    },
    integration: {
      suites: integrationTests.length,
      tests: integrationTests.reduce((sum, s) => sum + s.tests, 0),
      passed: integrationTests.reduce((sum, s) => sum + s.passed, 0),
      failed: integrationTests.reduce((sum, s) => sum + s.failures, 0),
      errors: integrationTests.reduce((sum, s) => sum + s.errors, 0),
      skipped: integrationTests.reduce((sum, s) => sum + s.skipped, 0),
      time: integrationTests.reduce((sum, s) => sum + s.time, 0)
    }
  };

  const byCategory = {};
  for (const suite of allSuites) {
    if (!byCategory[suite.category]) {
      byCategory[suite.category] = { suites: [], tests: 0, passed: 0, failed: 0, time: 0 };
    }
    byCategory[suite.category].suites.push(suite.name);
    byCategory[suite.category].tests += suite.tests;
    byCategory[suite.category].passed += suite.passed;
    byCategory[suite.category].failed += suite.failures + suite.errors;
    byCategory[suite.category].time += suite.time;
  }

  const report = {
    summary,
    byCategory,
    suites: allSuites.sort((a, b) => a.name.localeCompare(b.name))
  };

  if (!existsSync(outputDir)) {
    mkdirSync(outputDir, { recursive: true });
  }

  const outputPath = join(outputDir, 'testResults.json');
  writeFileSync(outputPath, JSON.stringify(report, null, 2));

  console.log(`\nTest Report Summary:`);
  console.log(`  Unit Tests: ${summary.unit.tests} (${summary.unit.passed} passed, ${summary.unit.failed} failed)`);
  console.log(`  Integration Tests: ${summary.integration.tests} (${summary.integration.passed} passed, ${summary.integration.failed} failed)`);
  console.log(`\nReport written to: ${outputPath}`);
}

generateReport();
