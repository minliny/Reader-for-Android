#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const demoContractPath =
  process.env.READER_UI_DEMO_ROUTE_CONTRACT ||
  path.resolve(repoRoot, "../Reader-UI/frontend-demo-optimized/route-contract.js");
const androidRouteStatePath = path.join(
  repoRoot,
  "app/src/main/kotlin/com/reader/ui/shell/ReaderUiState.kt"
);
const androidRegistryPath = path.join(
  repoRoot,
  "app/src/main/kotlin/com/reader/ui/demo/DemoRouteRegistry.kt"
);
const androidAppShellPath = path.join(
  repoRoot,
  "app/src/main/kotlin/com/reader/ui/shell/AppShell.kt"
);
const strictNativeFamilies = process.argv.includes("--strict-native-families");

function readFile(requiredPath) {
  if (!fs.existsSync(requiredPath)) {
    throw new Error(`Missing required file: ${requiredPath}`);
  }
  return fs.readFileSync(requiredPath, "utf8");
}

function loadDemoRouteContract() {
  const source = readFile(demoContractPath);
  const sandbox = { window: {} };
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: demoContractPath });
  return sandbox.window.ReaderFrontendDemoDraftRouteContract.routes;
}

function extractRouteIdsConstants(source) {
  return [...source.matchAll(/const val\s+[A-Z0-9_]+(?:\s*:\s*String)?\s*=\s*"([^"]+)"/g)].map(
    (match) => match[1]
  );
}

function extractKotlinStringSet(source, name) {
  const regex = new RegExp(
    `val\\s+${name}\\s*:\\s*Set<String>\\s*=\\s*setOf\\(([\\s\\S]*?)\\)`,
    "m"
  );
  const match = source.match(regex);
  if (!match) {
    throw new Error(`Missing route set: ${name}`);
  }
  return [...match[1].matchAll(/"([^"]+)"/g)].map((item) => item[1]);
}

function unique(values) {
  return [...new Set(values)];
}

function difference(left, right) {
  return left.filter((item) => !right.has(item));
}

function intersection(left, right) {
  return left.filter((item) => right.has(item));
}

const demoRouteContract = loadDemoRouteContract();
const demoRoutes = Object.keys(demoRouteContract);
const demoRouteSet = new Set(demoRoutes);
const readerUiStateSource = readFile(androidRouteStatePath);
const registrySource = readFile(androidRegistryPath);
const appShellSource = readFile(androidAppShellPath);
const readerControlSource = readFile(
  path.join(repoRoot, "app/src/main/kotlin/com/reader/ui/reading/ReaderControlScreen.kt")
);
const immersiveReaderSource = readFile(
  path.join(repoRoot, "app/src/main/kotlin/com/reader/ui/reading/ImmersiveReadingScreen.kt")
);
const discoverRouteScreenSource = readFile(
  path.join(repoRoot, "app/src/main/kotlin/com/reader/ui/discover/DiscoverDemoRouteScreen.kt")
);
const demoRouteScreenSource = readFile(
  path.join(repoRoot, "app/src/main/kotlin/com/reader/ui/demo/DemoRouteScreen.kt")
);
const canonicalGraphModelSource = readFile(
  path.join(repoRoot, "app/src/main/kotlin/com/reader/ui/demo/CanonicalScreenGraphModel.kt")
);

const mainTabRoutes = ["bookshelf", "discover", "rss", "settings"];
const contextBoundRoutes = ["immersive-reading"];
const nativeDirectRoutes = unique([
  ...mainTabRoutes,
  ...extractRouteIdsConstants(readerUiStateSource).filter(
    (routeId) => !contextBoundRoutes.includes(routeId)
  )
]);
const familyGroups = {
  book: extractKotlinStringSet(registrySource, "bookStateRouteIds"),
  rss: extractKotlinStringSet(registrySource, "rssStateRouteIds"),
  restore: extractKotlinStringSet(registrySource, "restoreStateRouteIds"),
  discover: extractKotlinStringSet(registrySource, "discoverStateRouteIds"),
  source: extractKotlinStringSet(registrySource, "sourceStateRouteIds")
};
const nativeFamilySignals = {
  book: ["BookDetailScreen(", "BookDirectoryScreen(", "BookshelfEmptyRouteScreen(", "BookshelfSortFilterRouteScreen("],
  rss: ["RssRemainingDemoRouteScreen("],
  restore: ["RestoreScreen("],
  discover: ["DiscoverDemoRouteScreen("],
  source: ["SourceDemoRouteScreen("]
};
const nativeFamilyGroups = Object.fromEntries(
  Object.entries(familyGroups).filter(([name]) =>
    nativeFamilySignals[name].every((signal) => appShellSource.includes(signal))
  )
);
const temporaryFamilyFallbackGroups = Object.fromEntries(
  Object.entries(familyGroups).filter(([name]) => !(name in nativeFamilyGroups))
);
const familyRoutes = unique(Object.values(familyGroups).flat());
const nativeFamilyRoutes = unique(Object.values(nativeFamilyGroups).flat());
const temporaryFamilyFallbackRoutes = unique(Object.values(temporaryFamilyFallbackGroups).flat());
const legacyCoveredRoutes = new Set([
  ...nativeDirectRoutes,
  ...familyRoutes,
  ...contextBoundRoutes
]);
const canonicalGraphRenderer =
  demoRouteScreenSource.includes("CanonicalScreenGraphRouteRenderer(") &&
  demoRouteScreenSource.includes("query = CanonicalScreenGraphQuery(routeId)") &&
  canonicalGraphModelSource.includes("CanonicalScreenGraphPlanner::loadCanonical") &&
  canonicalGraphModelSource.includes("ScreenGraphRegistry.loadCanonical()");
const canonicalGraphRoutes = canonicalGraphRenderer
  ? difference(demoRoutes, legacyCoveredRoutes)
  : [];
const coveredRoutes = new Set([...legacyCoveredRoutes, ...canonicalGraphRoutes]);

const unknownRoutes = difference(demoRoutes, coveredRoutes);
const androidOnlyDirectRoutes = difference(nativeDirectRoutes, demoRouteSet);
const androidOnlyFamilyRoutes = difference(familyRoutes, demoRouteSet);
const familyDirectOverlaps = intersection(familyRoutes, new Set(nativeDirectRoutes));
const mainTabShellStateRoutes = demoRoutes.filter(
  (routeId) => demoRouteContract[routeId]?.shell === "MainTabShell" && !mainTabRoutes.includes(routeId)
);
const androidMainTabStateRoutes = unique([
  ...familyGroups.book.filter((routeId) => demoRouteContract[routeId]?.shell === "MainTabShell"),
  ...familyGroups.discover.filter((routeId) => demoRouteContract[routeId]?.shell === "MainTabShell"),
  ...canonicalGraphRoutes.filter((routeId) => demoRouteContract[routeId]?.shell === "MainTabShell")
]);
const missingMainTabStateRoutes = difference(mainTabShellStateRoutes, new Set(androidMainTabStateRoutes));
const mainTabShellFrameSignals = [
  "private fun MainTabShellFrame(",
  "activeTab = MainTab.BOOKSHELF",
  "activeTab = MainTab.DISCOVER",
  "BookshelfEmptyRouteScreen(",
  "BookshelfSortFilterRouteScreen(",
  "DiscoverDemoRouteScreen("
];
const missingMainTabShellFrameSignals = mainTabShellFrameSignals.filter(
  (signal) => !appShellSource.includes(signal)
);
const sourceSwitchAsFlowShell =
  registrySource.includes("RouteIds.SOURCE_SWITCH -> ReaderRoute.SourceSwitchFlow()") &&
  readerUiStateSource.includes("data class SourceSwitchFlow(") &&
  readerControlSource.includes("ReaderSourceSwitchWindow(") &&
  !readerControlSource.includes("RouteIds.SOURCE_SWITCH -> \"source\"");
const readerSharedSurface =
  immersiveReaderSource.includes("fun ReaderReadingSurface(") &&
  readerControlSource.includes("ReaderReadingSurface(") &&
  readerControlSource.includes("factory = ImmersiveReadingViewModelFactory(");
const stableReaderShellHost =
  appShellSource.includes("ReaderShellScreen(") &&
  !appShellSource.includes("ImmersiveReadingScreen(") &&
  !appShellSource.includes("ReaderControlScreen(");
const readerFullUtilityPanels =
  readerControlSource.includes("ReaderFullPagePanel(") &&
  readerControlSource.includes("ReaderUtilityPanel(") &&
  readerControlSource.includes("RouteIds.READER_FULL_DIRECTORY -> \"directory\"") &&
  readerControlSource.includes("RouteIds.READER_FULL_TTS -> \"tts\"") &&
  readerControlSource.includes("RouteIds.READER_FULL_APPEARANCE -> \"appearance\"") &&
  readerControlSource.includes("RouteIds.READER_FULL_SETTINGS -> \"settings\"") &&
  readerControlSource.includes("RouteIds.READER_BOOK_CACHE -> \"cache\"") &&
  readerControlSource.includes("RouteIds.READER_DEBUG_INFO -> \"debug\"") &&
  readerControlSource.includes("if (renderModuleNav)");
const discoverSubpageShells =
  appShellSource.includes("shell = discoverShellForRoute(route.id)") &&
  appShellSource.includes("DiscoverDemoRouteIds.SOURCE_LOGIN -> DiscoverDemoRouteShell.Library") &&
  appShellSource.includes("DiscoverDemoRouteIds.RULE_TEST,") &&
  appShellSource.includes("DiscoverDemoRouteIds.SOURCE_BULK -> DiscoverDemoRouteShell.Settings") &&
  discoverRouteScreenSource.includes("DiscoverLibraryShell(") &&
  discoverRouteScreenSource.includes("DiscoverSettingsShell(");

console.log(`[route-coverage] demo routes: ${demoRoutes.length}`);
console.log(`[route-coverage] native direct: ${nativeDirectRoutes.length}`);
console.log(`[route-coverage] typed family: ${familyRoutes.length}`);
Object.entries(familyGroups).forEach(([name, routes]) => {
  console.log(`[route-coverage]   ${name}: ${routes.length}`);
});
console.log(`[route-coverage] native family wired: ${nativeFamilyRoutes.length}`);
Object.entries(nativeFamilyGroups).forEach(([name, routes]) => {
  console.log(`[route-coverage]   native ${name}: ${routes.length}`);
});
console.log(`[route-coverage] temporary family fallback: ${temporaryFamilyFallbackRoutes.length}`);
Object.entries(temporaryFamilyFallbackGroups).forEach(([name, routes]) => {
  console.log(`[route-coverage]   fallback ${name}: ${routes.length}`);
});
console.log(`[route-coverage] context-bound: ${contextBoundRoutes.length}`);
console.log(`[route-coverage] canonical screen graph: ${canonicalGraphRoutes.length}`);
console.log(`[route-coverage] unclassified fallback: ${unknownRoutes.length}`);
console.log(`[route-coverage] MainTabShell state routes: ${mainTabShellStateRoutes.length}`);

if (
  unknownRoutes.length > 0 ||
  androidOnlyDirectRoutes.length > 0 ||
  androidOnlyFamilyRoutes.length > 0 ||
  familyDirectOverlaps.length > 0 ||
  missingMainTabStateRoutes.length > 0 ||
  missingMainTabShellFrameSignals.length > 0 ||
  !sourceSwitchAsFlowShell ||
  !readerSharedSurface ||
  !stableReaderShellHost ||
  !readerFullUtilityPanels ||
  !discoverSubpageShells ||
  (strictNativeFamilies && temporaryFamilyFallbackRoutes.length > 0)
) {
  if (unknownRoutes.length > 0) {
    console.error(`[route-coverage] unclassified demo routes: ${unknownRoutes.join(", ")}`);
  }
  if (androidOnlyDirectRoutes.length > 0) {
    console.error(
      `[route-coverage] native direct routes not in demo contract: ${androidOnlyDirectRoutes.join(", ")}`
    );
  }
  if (androidOnlyFamilyRoutes.length > 0) {
    console.error(
      `[route-coverage] typed family routes not in demo contract: ${androidOnlyFamilyRoutes.join(", ")}`
    );
  }
  if (familyDirectOverlaps.length > 0) {
    console.error(
      `[route-coverage] routes classified both native direct and family: ${familyDirectOverlaps.join(", ")}`
    );
  }
  if (missingMainTabStateRoutes.length > 0) {
    console.error(
      `[route-coverage] MainTabShell state routes missing native state classification: ${missingMainTabStateRoutes.join(", ")}`
    );
  }
  if (missingMainTabShellFrameSignals.length > 0) {
    console.error(
      `[route-coverage] MainTabShell frame wiring signals missing: ${missingMainTabShellFrameSignals.join(", ")}`
    );
  }
  if (!sourceSwitchAsFlowShell) {
    console.error("[route-coverage] source-switch must be a SourceSwitchFlow with a window, not a ReaderControl panel");
  }
  if (!readerSharedSurface) {
    console.error("[route-coverage] ReaderControl must reuse the immersive ReaderReadingSurface");
  }
  if (!stableReaderShellHost) {
    console.error("[route-coverage] Reader routes must render through a single ReaderShellScreen host");
  }
  if (!readerFullUtilityPanels) {
    console.error("[route-coverage] Reader full/utility routes must use full-page and utility panels, not the compact dock");
  }
  if (!discoverSubpageShells) {
    console.error("[route-coverage] Discover source-login/rule-test/source-bulk must map to LibraryShell/SettingsShell owners");
  }
  if (strictNativeFamilies && temporaryFamilyFallbackRoutes.length > 0) {
    console.error(
      `[route-coverage] temporary family fallback routes: ${temporaryFamilyFallbackRoutes.join(", ")}`
    );
  }
  process.exit(1);
}
