#!/usr/bin/env node
// Signal-aware orchestration around the canonical shared Reader resource lease.
import { spawn } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const args = process.argv.slice(2);
if (args.shift() !== '--module') throw new Error('Expected --module <canonical build-resource-lock.mjs> -- <executable> [arguments]');
const modulePath = args.shift();
if (!modulePath || args.shift() !== '--' || args.length === 0) throw new Error('Missing canonical module or build command');
const { acquireBuildLease } = await import(pathToFileURL(resolve(modulePath)).href);
const lease = acquireBuildLease();
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
  child = spawn(args.shift(), args, { env: { ...lease.env, READER_NATIVE_BUILD_LOCKED: '1' }, stdio: 'inherit', shell: false });
  const outcome = await new Promise((accept, reject) => {
    child.once('error', reject);
    child.once('close', (code, value) => accept({ code, signal: value }));
  });
  process.exitCode = signal === 'SIGINT' ? 130 : signal || outcome.signal ? 143 : (outcome.code ?? 1);
} finally {
  process.off('SIGTERM', onTerm);
  process.off('SIGINT', onInt);
  lease.release();
}
