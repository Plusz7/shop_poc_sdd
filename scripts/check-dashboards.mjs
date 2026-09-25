#!/usr/bin/env node
// Checks that Grafana dashboards and Prometheus rules refer only to metrics of the contract
// (research R-31, R-34; specs/001-shop-browse-cart-checkout/contracts/metrics.md §2). No dependencies.
//
//   node scripts/check-dashboards.mjs
//
// Dashboards: valid JSON, data source uid "prometheus" on every panel and target, every shop_* metric listed
// in the contract, counters (_total, _count) only inside rate()/increase(). Rules: the same metric checks.
// Exit code 1 with the list of errors.

import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const CONTRACT = join(ROOT, 'specs', '001-shop-browse-cart-checkout', 'contracts', 'metrics.md');
const DASHBOARDS = join(ROOT, 'observability', 'grafana', 'dashboards');
const RULES = join(ROOT, 'observability', 'prometheus', 'rules');
const DATASOURCE_UID = 'prometheus';
const COUNTER_FUNCTIONS = new Set(['rate', 'increase', 'irate']);
const HISTOGRAM_SUFFIXES = ['_bucket', '_count', '_sum', '_max'];

const errors = [];
const { plain, histograms } = contractMetrics();

for (const file of listFiles(DASHBOARDS, '.json')) {
  const name = relative(ROOT, file);
  let dashboard;
  try {
    dashboard = JSON.parse(readFileSync(file, 'utf8'));
  } catch (invalid) {
    errors.push(`${name}: invalid JSON (${invalid.message})`);
    continue;
  }
  for (const panel of panels(dashboard.panels ?? [])) {
    const where = `${name} panel ${panel.id} "${panel.title}"`;
    if (panel.type !== 'row' && panel.datasource?.uid !== DATASOURCE_UID) {
      errors.push(`${where}: datasource.uid must be "${DATASOURCE_UID}"`);
    }
    for (const target of panel.targets ?? []) {
      if (target.datasource?.uid !== DATASOURCE_UID) {
        errors.push(`${where} target ${target.refId}: datasource.uid must be "${DATASOURCE_UID}"`);
      }
      checkExpression(target.expr ?? '', `${where} target ${target.refId}`);
    }
  }
}

for (const file of listFiles(RULES, '.yml')) {
  const name = relative(ROOT, file);
  readFileSync(file, 'utf8').split(/\r?\n/).forEach((line, index) => {
    const expr = line.match(/^\s*expr:\s*(.+)$/);
    if (expr) {
      checkExpression(expr[1], `${name}:${index + 1}`);
    }
  });
}

if (errors.length > 0) {
  console.error(`Dashboard/rule check failed (${errors.length}):\n` + errors.map((e) => `  - ${e}`).join('\n'));
  process.exit(1);
}
console.log(`Dashboards and rules refer only to the ${plain.size + histograms.size} contract metrics.`);

/** Rows "| `shop.…` | `shop_…` |" of §2; a histogram is written as `<base>_{bucket,count,sum}`. */
function contractMetrics() {
  const plainNames = new Set();
  const histogramNames = new Set();
  for (const line of readFileSync(CONTRACT, 'utf8').split(/\r?\n/)) {
    const row = line.match(/^\| `shop\.[^`]+`[^|]*\| `(shop_[a-z_]+)(\{[a-z,]+\})?`/);
    if (!row) continue;
    if (row[2]) {
      histogramNames.add(row[1].replace(/_$/, ''));
    } else {
      plainNames.add(row[1]);
    }
  }
  if (plainNames.size + histogramNames.size === 0) {
    console.error(`No shop_* metrics found in ${relative(ROOT, CONTRACT)}`);
    process.exit(1);
  }
  return { plain: plainNames, histograms: histogramNames };
}

function checkExpression(expr, where) {
  for (const match of expr.matchAll(/\b(shop_[a-z_]+)/g)) {
    const metric = match[1];
    if (!isContractMetric(metric)) {
      errors.push(`${where}: ${metric} is not in contracts/metrics.md §2`);
    }
  }
  for (const match of expr.matchAll(/\b([a-zA-Z_:][a-zA-Z0-9_:]*(?:_total|_count))\b/g)) {
    const fn = enclosingFunction(expr, match.index);
    if (!COUNTER_FUNCTIONS.has(fn)) {
      errors.push(`${where}: counter ${match[1]} must be used inside rate() or increase()`);
    }
  }
}

function isContractMetric(metric) {
  if (plain.has(metric) || histograms.has(metric)) return true;
  const suffix = HISTOGRAM_SUFFIXES.find((s) => metric.endsWith(s));
  return suffix !== undefined && histograms.has(metric.slice(0, -suffix.length));
}

/** Name of the function whose unclosed "(" is the nearest before the position. */
function enclosingFunction(expr, position) {
  let depth = 0;
  for (let i = position - 1; i >= 0; i--) {
    if (expr[i] === ')') depth++;
    else if (expr[i] === '(') {
      if (depth === 0) {
        return (expr.slice(0, i).match(/([a-zA-Z_]+)\s*$/) ?? [])[1];
      }
      depth--;
    }
  }
  return undefined;
}

function panels(list) {
  return list.flatMap((panel) => [panel, ...panels(panel.panels ?? [])]);
}

function listFiles(dir, extension) {
  return readdirSync(dir).filter((f) => f.endsWith(extension)).sort().map((f) => join(dir, f));
}
