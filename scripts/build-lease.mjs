#!/usr/bin/env node
// Signal-aware orchestration around the canonical shared Reader resource lease.
import { spawn } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { readFileSync } from 'node:fs';
import { setTimeout } from 'node:timers/promises';

const args = process.argv.slice(2);
if (args.shift() !== '--module') throw new Error('Expected --module <canonical build-resource-lock.mjs> -- <executable> [arguments]');
const modulePath = args.shift();
if (!modulePath || args.shift() !== '--' || args.length === 0) throw new Error('Missing canonical module or build command');
const { acquireBuildLease, buildLockPath } = await import(pathToFileURL(resolve(modulePath)).href);
let lease;
let child;
let signal;
const forward = value => {
  signal = value;
  if (child && child.exitCode === null) child.kill(value);
};
const onTerm = () => forward('SIGTERM');
const onInt = () => forward('SIGINT');
process.on('SIGTERM', onTerm);
process.on('SIGINT', onInt);
try {
  const deadline = performance.now() + 60_000;
  let lastOwner;
  while (!lease && !signal) {
    try { lease = acquireBuildLease(); }
    catch (error) {
      if (!error.message.startsWith('Reader build resource is occupied or has an unreconciled stale owner:')) throw error;
      const owner = JSON.parse(readFileSync(resolve(buildLockPath(), 'owner.json'), 'utf8'));
      if (!Number.isInteger(owner.pid) || owner.pid <= 0 || !owner.token || owner.protocol !== 'reader-build-resource-v1') throw error;
      try { process.kill(owner.pid, 0); }
      catch (probeError) { if (probeError.code !== 'EPERM') throw error; }
      if (performance.now() >= deadline) throw error;
      if (owner.token !== lastOwner) {
        console.error(`Reader build resource: waiting at most 60s for live owner ${owner.pid}; build child not dispatched`);
        lastOwner = owner.token;
      }
      // Only reservation is retried. Never remove an owner's lock or start a
      // second build child; the canonical module still acquires the same lock.
      await setTimeout(Math.min(1_000, deadline - performance.now()));
    }
  }
  if (lease && !signal) {
    child = spawn(args.shift(), args, { env: { ...lease.env, READER_NATIVE_BUILD_LOCKED: '1' }, stdio: 'inherit', shell: false });
    const outcome = await new Promise((accept, reject) => {
      child.once('error', reject);
      child.once('close', (code, value) => accept({ code, signal: value }));
    });
    process.exitCode = signal === 'SIGINT' ? 130 : signal || outcome.signal ? 143 : (outcome.code ?? 1);
  } else process.exitCode = signal === 'SIGINT' ? 130 : 143;
} finally {
  process.off('SIGTERM', onTerm);
  process.off('SIGINT', onInt);
  lease?.release();
}
