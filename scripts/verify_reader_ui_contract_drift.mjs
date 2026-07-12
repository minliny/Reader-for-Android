#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const readerUiRoot = process.env.READER_UI_ROOT || [
  path.resolve(repoRoot, "../Reader-UI"),
  path.resolve(repoRoot, "../Reader UI")
].find((candidate) => fs.existsSync(candidate));
if (!readerUiRoot) {
  throw new Error("Reader UI source repo not found beside Android repo");
}
const generatedRoutePath = path.join(readerUiRoot, "generated/kotlin/Route.kt");
const demoRouteContractPath = [
  "frontend-demo-next/route-contract.js",
  "frontend-demo-optimized/route-contract.js",
  "frontend-demo/route-contract.js"
].map((relativePath) => path.join(readerUiRoot, relativePath))
  .find((candidate) => fs.existsSync(candidate));
if (!demoRouteContractPath) {
  throw new Error(`Reader UI route-contract.js not found under ${readerUiRoot}`);
}
const readerUiStatePath = path.join(
  repoRoot,
  "app/src/main/kotlin/com/reader/ui/shell/ReaderUiState.kt"
);
const demoRouteRegistryPath = path.join(
  repoRoot,
  "app/src/main/kotlin/com/reader/ui/demo/DemoRouteRegistry.kt"
);

function readFile(requiredPath) {
  if (!fs.existsSync(requiredPath)) {
    throw new Error(`Missing required file: ${requiredPath}`);
  }
  return fs.readFileSync(requiredPath, "utf8");
}

function unique(values) {
  return [...new Set(values)];
}

function difference(left, right) {
  return left.filter((item) => !right.has(item));
}

function extractKotlinBlock(source, marker) {
  const start = source.indexOf(marker);
  if (start === -1) {
    throw new Error(`Missing Kotlin block marker: ${marker}`);
  }
  const braceStart = source.indexOf("{", start);
  if (braceStart === -1) {
    throw new Error(`Missing Kotlin block open brace: ${marker}`);
  }
  let depth = 0;
  for (let index = braceStart; index < source.length; index += 1) {
    const char = source[index];
    if (char === "{") depth += 1;
    if (char === "}") depth -= 1;
    if (depth === 0) {
      return source.slice(braceStart + 1, index);
    }
  }
  throw new Error(`Missing Kotlin block close brace: ${marker}`);
}

function extractGeneratedRouteIds(source) {
  const block = extractKotlinBlock(source, "enum class RouteId");
  return unique([...block.matchAll(/@SerialName\("([^"]+)"\)/g)].map((match) => match[1]));
}

function extractSourceRange(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start + startMarker.length);
  if (start === -1 || end === -1) {
    throw new Error(`Missing source range: ${startMarker} -> ${endMarker}`);
  }
  return source.slice(start, end);
}

function loadDemoRouteIds() {
  const source = readFile(demoRouteContractPath);
  const sandbox = { window: {} };
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: demoRouteContractPath });
  return Object.keys(sandbox.window.ReaderFrontendDemoDraftRouteContract.routes);
}

function extractRouteIdsConstants(source) {
  const block = extractKotlinBlock(source, "object RouteIds");
  return [...block.matchAll(/const val\s+[A-Z0-9_]+(?:\s*:\s*String)?\s*=\s*"([^"]+)"/g)].map(
    (match) => match[1]
  );
}

function extractMainTabRouteIds(source) {
  const block = extractKotlinBlock(source, "enum class MainTab");
  return [...block.matchAll(/\b[A-Z_]+\("([^"]+)"/g)].map((match) => match[1]);
}

function extractRegistryRouteIds(source) {
  const authoredBlock = extractSourceRange(
    source,
    "private val authoredPages",
    "private val contract25Additions"
  );
  const additionsBlock = extractSourceRange(
    source,
    "private val contract25Additions",
    "private val contract25RendererByRoute"
  );
  const pageIds = [...authoredBlock.matchAll(/DemoRoutePage\(id\s*=\s*"([^"]+)"/g)].map(
    (match) => match[1]
  );
  const additionIds = [...additionsBlock.matchAll(/\bid\s*=\s*"([^"]+)"/g)].map(
    (match) => match[1]
  );
  return [...pageIds, ...additionIds];
}

const generatedRouteIds = extractGeneratedRouteIds(readFile(generatedRoutePath));
const generatedRouteIdSet = new Set(generatedRouteIds);
const demoRouteIds = loadDemoRouteIds();
const demoRouteIdSet = new Set(demoRouteIds);
const readerUiStateSource = readFile(readerUiStatePath);
const registrySource = readFile(demoRouteRegistryPath);
const registryRouteIds = extractRegistryRouteIds(registrySource);
const registryRouteIdSet = new Set(registryRouteIds);
const androidRouteIds = unique([
  ...extractMainTabRouteIds(readerUiStateSource),
  ...extractRouteIdsConstants(readerUiStateSource),
  ...registryRouteIds
]);

const demoMissingFromGenerated = difference(demoRouteIds, generatedRouteIdSet);
const generatedMissingFromDemo = difference(generatedRouteIds, demoRouteIdSet);
const androidMissingFromGenerated = difference(androidRouteIds, generatedRouteIdSet);
const generatedMissingFromRegistry = difference(generatedRouteIds, registryRouteIdSet);
const registryMissingFromGenerated = difference(registryRouteIds, generatedRouteIdSet);

console.log(`[contract-drift] Reader UI generated RouteId: ${generatedRouteIds.length}`);
console.log(`[contract-drift] frontend-demo routes: ${demoRouteIds.length}`);
console.log(`[contract-drift] generated-only state routes: ${generatedMissingFromDemo.length}`);
console.log(`[contract-drift] Android registry routes: ${registryRouteIds.length}`);
console.log(`[contract-drift] Android route references: ${androidRouteIds.length}`);

if (
  demoMissingFromGenerated.length > 0 ||
  androidMissingFromGenerated.length > 0 ||
  generatedMissingFromRegistry.length > 0 ||
  registryMissingFromGenerated.length > 0
) {
  if (demoMissingFromGenerated.length > 0) {
    console.error(
      `[contract-drift] frontend-demo routes missing from generated Kotlin: ${demoMissingFromGenerated.join(", ")}`
    );
  }
  if (androidMissingFromGenerated.length > 0) {
    console.error(
      `[contract-drift] Android routes missing from Reader UI generated Kotlin: ${androidMissingFromGenerated.join(", ")}`
    );
  }
  if (generatedMissingFromRegistry.length > 0) {
    console.error(
      `[contract-drift] generated Kotlin routes missing from Android registry: ${generatedMissingFromRegistry.join(", ")}`
    );
  }
  if (registryMissingFromGenerated.length > 0) {
    console.error(
      `[contract-drift] Android registry routes missing from generated Kotlin: ${registryMissingFromGenerated.join(", ")}`
    );
  }
  process.exit(1);
}

console.log(
  "[contract-drift] PASS: demo is a generated-route subset; Android registry and references exactly match generated"
);
