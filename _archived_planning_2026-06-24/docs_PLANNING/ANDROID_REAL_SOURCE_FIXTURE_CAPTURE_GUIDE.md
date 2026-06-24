# Android Real Source Fixture Capture Guide

**Date**: 2026-05-28
**Purpose**: Guide for manually capturing HTML fixtures from a browser for real source offline replay testing.
**Mode**: `manual-browser` — no automated network access required.

---

## 1. Overview

Since all tested real novel sites are either unreachable (TLS handshake), blocked (HTTP 403), or protected by anti-bot measures (returning empty for automated queries), we capture HTML fixtures manually via browser DevTools.

This approach:
- Does NOT bypass anti-bot or CAPTCHA
- Does NOT perform high-frequency requests
- Does NOT access network beyond normal browser use
- Produces fixture files for offline replay testing

---

## 2. Prerequisites

1. A小说站 that is accessible via normal browser (no VPN needed in normal network environment)
2. Chrome/Edge/Firefox DevTools (F12)
3. The app's fixture directory: `app/src/test/resources/fixtures/real-source/`
4. The book name to search: recommended "斗破苍穹" or any well-known novel

---

## 3. Source Recommendation

Before starting, confirm which source you want to use. If xingxingxsw.com works in your browser:

```
URL: https://www.xingxingxsw.com
Search URL pattern: /search.php?key={keyword}
```

You can also try other sites like:
- 笔趣阁 bqgz.cc
- 全书网 quanshu.com
- Any site you know works in your browser

**IMPORTANT**: Do NOT use this guide to probe sites that are blocked in your network. Only capture from sites you can access normally.

---

## 4. Capture Steps

### Step A: Search Results

1. Open browser, navigate to: `https://www.xingxingxsw.com/search.php?key=斗破苍穹`
2. Wait for page to fully load (scroll to bottom if needed)
3. Press F12 → DevTools → Network tab
4. Right-click → "Save all as HAR" (optional, for reference)
5. Right-click on the main page request → "Copy → Copy all as HAR"
6. Or simply: Save page as "Webpage, Complete" from browser menu
7. Save the HTML file as:
   ```
   app/src/test/resources/fixtures/real-source/xingxingxsw/search/search.html
   ```
8. Also save the query you used: write it in manifest.json

### Step B: Book Detail Page

1. From the search results page, click on any book link (e.g., `/xiaoshuo/12345.html`)
2. Wait for detail page to load
3. Save the page (same method as Step A)
4. Save as:
   ```
   app/src/test/resources/fixtures/real-source/xingxingxsw/detail/detail.html
   ```
5. Record the detail URL in manifest.json

### Step C: Table of Contents

1. Click on the "目录" or "章节目录" link on the detail page
2. Wait for TOC to load
3. Save the page
4. Save as:
   ```
   app/src/test/resources/fixtures/real-source/xingxingxsw/toc/toc.html
   ```
5. Record the TOC URL in manifest.json

### Step D: Chapter Content

1. Click on the first chapter link from the TOC
2. Wait for content to load
3. Save the page
4. Save as:
   ```
   app/src/test/resources/fixtures/real-source/xingxingxsw/content/chapter-001.html
   ```
5. Record the content URL in manifest.json

### Step E: Second Chapter (optional, for pagination test)

1. Click on the second chapter
2. Save as:
   ```
   app/src/test/resources/fixtures/real-source/xingxingxsw/content/chapter-002.html
   ```

---

## 5. Anti-Bot Notes

If a site returns empty or challenge pages in your browser:
- **Do NOT** try to bypass or refresh repeatedly
- **Do NOT** use automated tools
- **Do NOT** attempt to circumvent CAPTCHA
- **Report** the site as unavailable and choose a different source

---

## 6. File Delivery

After capturing, place files in:

```
app/src/test/resources/fixtures/real-source/<source-id>/
├── manifest.json          # Metadata (see §7)
├── search/
│   └── search.html       # Search results page
├── detail/
│   └── detail.html        # Book detail page
├── toc/
│   └── toc.html           # Table of contents page
└── content/
    ├── chapter-001.html    # First chapter
    └── chapter-002.html    # Second chapter (optional)
```

---

## 7. manifest.json Schema

```json
{
  "sourceId": "xingxingxsw",
  "sourceName": "星星小说网",
  "captureMode": "manual-browser",
  "capturedAt": "2026-05-28T00:00:00Z",
  "query": "斗破苍穹",
  "searchUrl": "https://www.xingxingxsw.com/search.php?key=斗破苍穹",
  "detailUrl": "https://www.xingxingxsw.com/xiaoshuo/12345.html",
  "tocUrl": "https://www.xingxingxsw.com/xiaoshuo/12345/",
  "contentUrl": "https://www.xingxingxsw.com/xiaoshuo/12345/1.html",
  "charset": "utf-8",
  "antiBotObserved": false,
  "notes": "Normal browser capture, no anti-bot encountered",
  "files": [
    "search/search.html",
    "detail/detail.html",
    "toc/toc.html",
    "content/chapter-001.html",
    "content/chapter-002.html"
  ]
}
```

---

## 8. manifest.json Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| sourceId | string | ✅ | Unique ID, e.g., `xingxingxsw`, `bqgz` |
| sourceName | string | ✅ | Display name in Chinese |
| captureMode | string | ✅ | Always `manual-browser` |
| capturedAt | string | ✅ | ISO 8601 timestamp |
| query | string | ✅ | Keyword used for search |
| searchUrl | string | ✅ | Full URL of search results page |
| detailUrl | string | ✅ | Full URL of book detail page |
| tocUrl | string | ✅ | Full URL of table of contents |
| contentUrl | string | ✅ | Full URL of first chapter |
| charset | string | ✅ | `utf-8`, `gbk`, `gb2312` |
| antiBotObserved | boolean | ✅ | `true` if challenge page appeared |
| notes | string | ❌ | Any observations |
| files | array | ✅ | List of relative file paths |

---

## 9. Offline Replay After Capture

Once fixture files are provided:

1. Place files in the correct directory structure
2. Create a `FixtureHttpTransport` that loads these files
3. Run `RealCoreBridge` through `SearchParser → Detail → TOC → Content`
4. Verify each stage returns parsed data
5. All tests must be deterministic and NOT access the network

---

## 10. Rules

- **DO**: Capture from sites you can normally access in a browser
- **DO**: Use simple well-known novels (斗破苍穹, 仙逆, 剑来)
- **DO**: Save complete HTML (not just fragment)
- **DO**: Fill manifest.json completely
- **DON'T**: Refresh repeatedly to bypass anti-bot
- **DON'T**: Use VPN or proxy to test blocked sites
- **DON'T**: Capture from sites requiring login
- **DON'T**: Perform stress testing or crawling