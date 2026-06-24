# Reader for Android Dynamic Source Capability Matrix

**Date**: 2026-05-15
**Purpose**: Track dynamic (JS/WebView) book source capabilities — what Reader-Core defines, what Android has implemented, and gaps.

## Capability Status

| ID | Capability | Core Protocol | Android Impl | Status | Notes |
|----|-----------|---------------|-------------|--------|-------|
| DYN-01 | WebView page load | WebViewRuntimeProtocol | WebRuntimeAdapter (interface) | CONTRACT | Fake adapter ready |
| DYN-02 | JS evaluation | JSExecutionAdapter | JsRequest/JsResponse model | CONTRACT | Fake, no real JS engine |
| DYN-03 | JS error mapping | - | JsErrorType (5 types) | CONTRACT | SYNTAX/RUNTIME/TIMEOUT/SECURITY/UNKNOWN |
| DYN-04 | Cookie injection | CookieJar protocol | CookieStore interface | CONTRACT | Per-source isolation |
| DYN-05 | Cookie scope per source | - | CookieScope/CookieRecord | CONTRACT | Fake store |
| DYN-06 | POST/API request body | URL DSL models | Not yet | GAP | S7-NUI-P0-005 planned |
| DYN-07 | Request headers/cookie injection | - | Not yet | GAP | S7-NUI-P0-006 planned |
| DYN-08 | Pagination/next-page | - | Not yet | GAP | S7-NUI-P0-007 planned |
| DYN-09 | Source validation | - | Not yet | GAP | S7-NUI-P0-008 planned |
| DYN-10 | Dynamic source offline replay | - | Not yet | GAP | S7-NUI-P0-011 planned |
| DYN-11 | Per-source runtime isolation | - | Not yet | GAP | S7-NUI-P0-012 planned |
| DYN-12 | Web runtime error mapping | - | Not yet | GAP | S7-NUI-P0-010 planned |

## Architecture Notes

- All dynamic source adapters use interface + fake implementation pattern
- Real WebView/JS engine integration is UI-dependent (deferred)
- CookieStore separates cookie data from credential encryption (deferred)
- POST/API request model follows Reader-Core URL DSL contract
- Per-source isolation prevents cookie and JS context leaks

## Status Legend

- `DONE`: Implemented and tested
- `CONTRACT`: Interface/model defined, fake impl exists
- `GAP`: Planned but not yet implemented
- `UI_BLOCKED`: Requires WebView UI integration
