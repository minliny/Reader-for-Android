#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

function loadPlaywright() {
  try {
    return require("playwright");
  } catch (error) {
    const message = [
      "Playwright is required to validate frontend input pages.",
      "Run with a Node environment that has playwright installed, or set NODE_PATH to the bundled runtime modules.",
      `Original error: ${error.message}`
    ].join("\n");
    throw new Error(message);
  }
}

function assert(condition, message, failures) {
  if (!condition) {
    failures.push(message);
  }
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

async function validateTarget(browser, repoRoot, target) {
  const failures = [];
  const failedRequests = [];
  const consoleErrors = [];
  const page = await browser.newPage({
    viewport: target.viewport,
    deviceScaleFactor: 1
  });

  page.on("requestfailed", (request) => {
    failedRequests.push({
      url: request.url(),
      failure: request.failure() ? request.failure().errorText : ""
    });
  });

  page.on("console", (message) => {
    if (message.type() === "error") {
      consoleErrors.push(message.text());
    }
  });

  const htmlPath = path.join(repoRoot, target.html);
  const screenshotPath = path.join(repoRoot, target.screenshot);
  fs.mkdirSync(path.dirname(screenshotPath), { recursive: true });

  await page.goto(`file://${htmlPath}`, { waitUntil: "load" });
  await page.waitForSelector(target.selector, { timeout: 5000 });
  await page.screenshot({ path: screenshotPath, fullPage: true });

  const actual = await page.evaluate((targetInPage) => {
    const frame = document.querySelector(targetInPage.selector);
    const frameRect = frame ? frame.getBoundingClientRect() : null;
    const images = Array.from(document.images);
    const requiredTextPresent = targetInPage.requiredText.every((text) =>
      document.body.innerText.includes(text)
    );

    return {
      title: document.title,
      bodyWidth: document.body.scrollWidth,
      bodyHeight: document.body.scrollHeight,
      frame: frameRect
        ? {
            width: Math.round(frameRect.width),
            height: Math.round(frameRect.height)
          }
        : null,
      requiredTextPresent,
      rendererLoaded: Boolean(window[targetInPage.rendererGlobal]),
      stateCardCount: document.querySelectorAll(".bs-state-card, .rc-state-card, .st-state-card, .se-state-card").length,
      imageCount: images.length,
      missingImages: images.filter((image) => !image.complete || image.naturalWidth === 0).length,
      cssLinks: Array.from(document.styleSheets).filter((sheet) => sheet.href).length,
      expectedDomCount: targetInPage.expectedDomCount
        ? document.querySelectorAll(targetInPage.expectedDomCount.selector).length
        : null
    };
  }, target);

  assert(actual.rendererLoaded, `${target.rendererGlobal} was not loaded`, failures);
  assert(actual.requiredTextPresent, "required text was not fully rendered", failures);
  assert(failedRequests.length === 0, `${failedRequests.length} request(s) failed`, failures);
  assert(consoleErrors.length === 0, `${consoleErrors.length} console error(s) found`, failures);

  if (target.expectedFrame) {
    assert(Boolean(actual.frame), "expected frame was not found", failures);
    if (actual.frame) {
      assert(
        actual.frame.width === target.expectedFrame.width,
        `frame width ${actual.frame.width} !== ${target.expectedFrame.width}`,
        failures
      );
      assert(
        actual.frame.height === target.expectedFrame.height,
        `frame height ${actual.frame.height} !== ${target.expectedFrame.height}`,
        failures
      );
    }
  }

  if (typeof target.expectedStateCards === "number") {
    assert(
      actual.stateCardCount === target.expectedStateCards,
      `state card count ${actual.stateCardCount} !== ${target.expectedStateCards}`,
      failures
    );
  }

  if (target.expectedImages) {
    assert(
      actual.imageCount >= target.expectedImages.min,
      `image count ${actual.imageCount} < ${target.expectedImages.min}`,
      failures
    );
    assert(
      actual.missingImages === target.expectedImages.missing,
      `missing image count ${actual.missingImages} !== ${target.expectedImages.missing}`,
      failures
    );
  }

  if (target.expectedDomCount) {
    assert(
      actual.expectedDomCount >= target.expectedDomCount.min,
      `${target.expectedDomCount.selector} count ${actual.expectedDomCount} < ${target.expectedDomCount.min}`,
      failures
    );
  }

  await page.close();

  return {
    name: target.name,
    html: target.html,
    screenshot: screenshotPath,
    passed: failures.length === 0,
    failures,
    failedRequests,
    consoleErrors,
    actual
  };
}

async function main() {
  const repoRoot = path.resolve(__dirname, "../../..");
  const manifestPath = path.join(__dirname, "manifest.json");
  const outputPath = path.join(repoRoot, "docs/ui-design/frontend-input-design-draft-validation.json");
  const manifest = readJson(manifestPath);
  const { chromium } = loadPlaywright();

  const browser = await chromium.launch({ headless: true });
  const results = [];
  try {
    for (const target of manifest.targets) {
      results.push(await validateTarget(browser, repoRoot, target));
    }
  } finally {
    await browser.close();
  }

  const report = {
    generatedAt: new Date().toISOString(),
    manifest: path.relative(repoRoot, manifestPath),
    passed: results.every((result) => result.passed),
    results
  };

  fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);
  console.log(JSON.stringify(report, null, 2));

  if (!report.passed) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
