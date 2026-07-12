#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const args = new Map();
for (let index = 2; index < process.argv.length; index += 2) {
  args.set(process.argv[index], process.argv[index + 1] ?? "");
}

const tag = args.get("--tag") ?? "";
const version = args.get("--version") ?? "";
const hash = args.get("--hash") ?? "";
const lockPath = path.resolve(process.cwd(), "READER_UI_CONSUMER.json");
const lock = JSON.parse(fs.readFileSync(lockPath, "utf8"));
const failures = [];

if (!/^v[^\s]+$/.test(tag)) failures.push(`dispatch tag must start with v, got ${tag || "<empty>"}`);
if (tag !== `v${version}`) failures.push(`dispatch tag ${tag} does not match payload version ${version}`);
if (version !== lock.readerUiVersion) {
  failures.push(`dispatch version ${version} does not match lock ${lock.readerUiVersion}`);
}
if (hash !== lock.runtimeActionsSha256) {
  failures.push(`dispatch runtime hash ${hash} does not match lock ${lock.runtimeActionsSha256}`);
}

if (failures.length > 0) {
  console.error(`[reader-ui-dispatch] FAIL\n${failures.join("\n")}`);
  process.exit(1);
}
console.log(`[reader-ui-dispatch] PASS tag=${tag} version=${version} hash=${hash}`);
