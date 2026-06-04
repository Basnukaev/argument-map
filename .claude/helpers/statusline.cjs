#!/usr/bin/env node
/**
 * Claude Code statusline generator.
 *
 * Reads session JSON from stdin (model / context_window / cost) and
 * renders a compact one-line summary: duration | ctx% | cost.
 */

/* eslint-disable @typescript-eslint/no-var-requires */
const fs = require('fs');

// ANSI colors
const c = {
  reset: '\x1b[0m',
  dim: '\x1b[2m',
  cyan: '\x1b[0;36m',
  brightRed: '\x1b[1;31m',
  brightYellow: '\x1b[1;33m',
  brightGreen: '\x1b[1;32m',
};

// Read Claude Code session JSON piped to fd 0. TTY = manual run = no input.
let _stdinData = null;
let _stdinRead = false;
function getStdinData() {
  if (_stdinRead) return _stdinData;
  _stdinRead = true;
  try {
    if (process.stdin.isTTY) return null;
    const chunks = [];
    const buf = Buffer.alloc(4096);
    let bytesRead;
    try {
      while ((bytesRead = fs.readSync(0, buf, 0, buf.length, null)) > 0) {
        chunks.push(buf.slice(0, bytesRead));
      }
    } catch { /* EOF */ }
    const raw = Buffer.concat(chunks).toString('utf-8').trim();
    if (raw && raw.startsWith('{')) _stdinData = JSON.parse(raw);
  } catch { /* ignore */ }
  return _stdinData;
}

function getContextFromStdin() {
  const d = getStdinData();
  if (d && d.context_window) {
    return { usedPct: Math.floor(d.context_window.used_percentage || 0) };
  }
  return null;
}

function getCostFromStdin() {
  const d = getStdinData();
  if (d && d.cost) {
    const ms = d.cost.total_duration_ms || 0;
    const mins = Math.floor(ms / 60000);
    const secs = Math.floor((ms % 60000) / 1000);
    return {
      costUsd: d.cost.total_cost_usd || 0,
      duration: mins > 0 ? mins + 'm' + secs + 's' : secs + 's',
    };
  }
  return null;
}

function generateStatusline() {
  const ctx = getContextFromStdin();
  const cost = getCostFromStdin();
  const parts = [];

  if (cost && cost.duration) {
    parts.push(c.cyan + '⏱ ' + cost.duration + c.reset);
  }

  if (ctx && ctx.usedPct > 0) {
    const color = ctx.usedPct >= 90 ? c.brightRed
      : ctx.usedPct >= 70 ? c.brightYellow
      : c.brightGreen;
    parts.push(color + '● ' + ctx.usedPct + '% ctx' + c.reset);
  }

  if (cost && cost.costUsd > 0) {
    parts.push(c.brightYellow + '$' + cost.costUsd.toFixed(2) + c.reset);
  }

  return parts.join('  ' + c.dim + '│' + c.reset + '  ');
}

console.log(generateStatusline());
