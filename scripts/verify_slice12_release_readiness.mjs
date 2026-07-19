#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const androidRoot = path.resolve(scriptDir, "..");
const readerUiRoot = path.resolve(
  process.env.READER_UI_ROOT || path.join(androidRoot, "..", "Reader-UI")
);
const reportOnly = process.argv.includes("--report-only");
const blockers = [];
const facts = {
  androidRoot,
  readerUiRoot,
};

function addBlocker(code, message, details) {
  blockers.push({ code, message, ...(details === undefined ? {} : { details }) });
}

function sha256File(filePath) {
  const hash = crypto.createHash("sha256");
  hash.update(fs.readFileSync(filePath));
  return hash.digest("hex");
}

function readJson(filePath, label) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    addBlocker("INVALID_JSON", `${label} is missing or invalid`, error.message);
    return null;
  }
}

function git(args) {
  return execFileSync("git", ["-C", readerUiRoot, ...args], {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  }).trim();
}

function isSafeChild(root, relativePath) {
  const resolved = path.resolve(root, relativePath);
  return resolved === root || resolved.startsWith(`${root}${path.sep}`);
}

function hasPassedEvidence(slice, kind, targetKind) {
  return Array.isArray(slice?.evidence) && slice.evidence.some((entry) =>
    entry?.kind === kind &&
    entry?.result === "passed" &&
    (targetKind === undefined || entry?.targetKind === targetKind)
  );
}

let readerUiSourceSha = null;
let readerUiWorktreeClean = false;
try {
  readerUiSourceSha = git(["rev-parse", "HEAD"]);
  const dirty = git(["status", "--porcelain", "--untracked-files=normal"]);
  readerUiWorktreeClean = dirty.length === 0;
  facts.readerUiSourceSha = readerUiSourceSha;
  facts.readerUiWorktreeClean = readerUiWorktreeClean;
  if (!readerUiWorktreeClean) {
    addBlocker("READER_UI_DIRTY", "Reader UI worktree is not immutable/clean");
  }
} catch (error) {
  addBlocker("READER_UI_GIT", "Reader UI Git identity cannot be recomputed", error.message);
}

const uiManifestPath = path.join(readerUiRoot, "UI_RELEASE_MANIFEST.json");
const uiManifest = readJson(uiManifestPath, "Reader UI release manifest");
let readerUiManifestSha = null;
let readerUiManifestFilesVerified = false;
if (uiManifest && fs.existsSync(uiManifestPath)) {
  readerUiManifestSha = sha256File(uiManifestPath);
  facts.readerUiManifestSha256 = readerUiManifestSha;
  facts.readerUiVersion = uiManifest.version ?? null;

  const fileErrors = [];
  const files = Array.isArray(uiManifest.files) ? uiManifest.files : [];
  if (files.length === 0) fileErrors.push("manifest files array is empty");
  for (const entry of files) {
    if (!entry || typeof entry.path !== "string" || !isSafeChild(readerUiRoot, entry.path)) {
      fileErrors.push(`unsafe manifest path: ${entry?.path ?? "<missing>"}`);
      continue;
    }
    const absolute = path.resolve(readerUiRoot, entry.path);
    if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) {
      fileErrors.push(`missing file: ${entry.path}`);
      continue;
    }
    const stat = fs.statSync(absolute);
    if (stat.size !== entry.byteLength) {
      fileErrors.push(`byteLength mismatch: ${entry.path}`);
      continue;
    }
    if (sha256File(absolute) !== entry.sha256) {
      fileErrors.push(`sha256 mismatch: ${entry.path}`);
    }
  }
  readerUiManifestFilesVerified = fileErrors.length === 0;
  facts.readerUiManifestFileCount = files.length;
  facts.readerUiManifestFilesVerified = readerUiManifestFilesVerified;
  if (!readerUiManifestFilesVerified) {
    addBlocker(
      "READER_UI_MANIFEST_DRIFT",
      "Reader UI manifest contents do not match current files",
      { count: fileErrors.length, sample: fileErrors.slice(0, 20) }
    );
  }
}

const consumerLockPath = path.join(androidRoot, "READER_UI_CONSUMER.json");
const consumerLock = readJson(consumerLockPath, "Android Reader UI consumer lock");
let consumerLockSha = null;
if (consumerLock && fs.existsSync(consumerLockPath)) {
  consumerLockSha = sha256File(consumerLockPath);
  facts.consumerLockSha256 = consumerLockSha;
  facts.consumerReaderUiVersion = consumerLock.readerUiVersion ?? null;
  facts.consumerReleaseIdentity = consumerLock.releaseIdentity ?? null;

  if (consumerLock.releaseIdentity?.sourceSha !== readerUiSourceSha) {
    addBlocker("CONSUMER_SOURCE_DRIFT", "consumer source SHA does not match current Reader UI source");
  }
  if (consumerLock.releaseIdentity?.manifestSha256 !== readerUiManifestSha) {
    addBlocker("CONSUMER_MANIFEST_DRIFT", "consumer manifest SHA does not match current Reader UI manifest");
  }
  const expectedReleaseId = readerUiSourceSha && readerUiManifestSha
    ? `${readerUiSourceSha}:${readerUiManifestSha}`
    : null;
  if (consumerLock.releaseIdentity?.releaseId !== expectedReleaseId) {
    addBlocker("CONSUMER_RELEASE_ID", "consumer releaseId is not the recomputed source:manifest identity");
  }
  if (uiManifest?.version && consumerLock.readerUiVersion !== uiManifest.version) {
    addBlocker("CONSUMER_VERSION_DRIFT", "consumer Reader UI version does not match the release manifest version");
  }
}

const coreArtifactPath = process.env.READER_CORE_ARTIFACT
  ? path.resolve(process.env.READER_CORE_ARTIFACT)
  : null;
let readerCoreArtifactSha = null;
if (!coreArtifactPath) {
  addBlocker("CORE_ARTIFACT_REQUIRED", "READER_CORE_ARTIFACT must point to the exact release artifact");
} else if (!fs.existsSync(coreArtifactPath) || !fs.statSync(coreArtifactPath).isFile()) {
  addBlocker("CORE_ARTIFACT_MISSING", "Reader Core artifact path is not a file", coreArtifactPath);
} else {
  readerCoreArtifactSha = sha256File(coreArtifactPath);
  facts.readerCoreArtifactPath = coreArtifactPath;
  facts.readerCoreArtifactSha256 = readerCoreArtifactSha;
}

const evidenceManifestPath = process.env.ANDROID_EVIDENCE_MANIFEST
  ? path.resolve(process.env.ANDROID_EVIDENCE_MANIFEST)
  : null;
let evidenceManifestSchemaVerified = false;
let slice9Passed = false;
let slice10Passed = false;
let slice11Passed = false;
let slice12Passed = false;
let completeJourneyDeviceEvidence = false;
let talkBackEvidence = false;
let performanceEvidence = false;
let rollbackEvidence = false;

if (!evidenceManifestPath) {
  addBlocker("EVIDENCE_MANIFEST_REQUIRED", "ANDROID_EVIDENCE_MANIFEST must point to an Android execution manifest");
} else {
  const evidenceManifest = readJson(evidenceManifestPath, "Android evidence manifest");
  if (evidenceManifest) {
    facts.evidenceManifestPath = evidenceManifestPath;
    const schemaPath = path.join(readerUiRoot, "contracts", "platform-evidence-manifest.schema.json");
    const validatorPath = path.join(readerUiRoot, "contracts", "tests", "mini-validator.mjs");
    const schema = readJson(schemaPath, "platform evidence manifest schema");
    if (schema && fs.existsSync(validatorPath)) {
      try {
        const { validate } = await import(pathToFileURL(validatorPath).href);
        const errors = validate(schema, evidenceManifest);
        evidenceManifestSchemaVerified = errors.length === 0;
        facts.evidenceManifestSchemaVerified = evidenceManifestSchemaVerified;
        if (!evidenceManifestSchemaVerified) {
          addBlocker(
            "EVIDENCE_SCHEMA",
            "Android evidence manifest fails the canonical schema",
            errors.slice(0, 20)
          );
        }
      } catch (error) {
        addBlocker("EVIDENCE_VALIDATOR", "canonical evidence validator could not run", error.message);
      }
    }

    if (evidenceManifest.platform !== "android" || evidenceManifest.manifestKind !== "execution") {
      addBlocker("EVIDENCE_KIND", "evidence manifest must be an Android execution manifest");
    }
    const identity = evidenceManifest.releaseIdentity;
    if (
      identity?.contractVersion !== uiManifest?.version ||
      identity?.sourceSha !== readerUiSourceSha ||
      identity?.manifestSha !== readerUiManifestSha ||
      identity?.readerCoreArtifactSha !== readerCoreArtifactSha ||
      identity?.consumerLockSha !== consumerLockSha
    ) {
      addBlocker("EVIDENCE_IDENTITY", "evidence manifest releaseIdentity does not match recomputed artifacts");
    }

    const slices = evidenceManifest.slices ?? {};
    slice9Passed = slices["slice-9"]?.status === "passed";
    slice10Passed = slices["slice-10"]?.status === "passed";
    slice11Passed = slices["slice-11"]?.status === "passed";
    slice12Passed = slices["slice-12"]?.status === "passed";
    completeJourneyDeviceEvidence = hasPassedEvidence(slices["slice-12"], "video", "physical-device");
    talkBackEvidence = hasPassedEvidence(slices["slice-12"], "accessibility", "physical-device");
    performanceEvidence = hasPassedEvidence(slices["slice-12"], "performance", "physical-device");
    rollbackEvidence = hasPassedEvidence(slices["slice-12"], "rollback");
  }
}

facts.localAdmission = {
  readerUiSourceSha,
  readerUiManifestSha,
  readerCoreArtifactSha,
  consumerLockSha,
  readerUiWorktreeClean,
  readerUiManifestFilesVerified,
  evidenceManifestSchemaVerified,
  slice9Passed,
  slice10Passed,
  slice11Passed,
  slice12Passed,
  completeJourneyDeviceEvidence,
  talkBackEvidence,
  performanceEvidence,
  rollbackEvidence,
};

if (!slice9Passed) addBlocker("SLICE_9", "slice-9 is not passed in the Android execution manifest");
if (!slice10Passed) addBlocker("SLICE_10", "slice-10 is not passed in the Android execution manifest");
if (!slice11Passed) addBlocker("SLICE_11", "slice-11 is not passed in the Android execution manifest");
if (!slice12Passed) addBlocker("SLICE_12", "slice-12 is not passed in the Android execution manifest");
if (!completeJourneyDeviceEvidence) addBlocker("DEVICE_JOURNEY", "complete physical-device journey video is missing");
if (!talkBackEvidence) addBlocker("TALKBACK", "physical-device TalkBack evidence is missing");
if (!performanceEvidence) addBlocker("PERFORMANCE", "physical-device performance evidence is missing");
if (!rollbackEvidence) addBlocker("ROLLBACK", "upgrade/downgrade rollback evidence is missing");

const report = {
  schemaVersion: 1,
  gate: "android.slice12.release",
  ready: blockers.length === 0,
  facts,
  blockers,
};

process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
if (!reportOnly && !report.ready) process.exitCode = 1;
