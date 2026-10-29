import {createReaderEbookDom} from './reader-ebook-dom.mjs';
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const session = document.querySelector('meta[name="reader-render"]').content;
const frame = document.getElementById('publication');
if (!uuid.test(session) || location.origin !== 'https://reader-ebook.invalid' || location.pathname !== '/__reader/shell.html')
  throw Error('TRUSTED_SHELL_CONTEXT_REQUIRED');
let retired = false, serial = 0, sequence = 0, active = null, publication = null, confirmed = false, interacted = false, pending = 0, scrollEnabled = false;
const integer = value => { if (!Number.isSafeInteger(value) || value < 0) throw Error('EXACT_INTEGER_REQUIRED'); return value; };
const current = captured => !retired && active === captured;
// Observer-only: captured identity is never replaced with a newer active layout after an await.
const diagnosticClock = () => { try { const v=performance.now(); return Number.isFinite(v)&&v>=0?v:0; } catch { return 0; } };
const diagnosticPhase = (captured, phase) => {
  try {
    if (!captured || !current(captured) || !Number.isSafeInteger(sequence + 1)) return;
    const elapsed = diagnosticClock() - captured.observerStartedMs;
    if (!Number.isFinite(elapsed) || elapsed < 0 || elapsed > 86400000) return;
    ReaderEbookHost.postMessage(JSON.stringify({protocolVersion:1,session,sequence:++sequence,kind:'phase',
      value:{phase,sinceLayoutStartMs:elapsed,layoutToken:captured.token,layoutEpoch:captured.epoch}}));
  } catch {} // Observation cannot throw into DOM, layout or business failure handling.
};
const send = (kind, value) => {
  if (retired || !active) return;
  if (!Number.isSafeInteger(++sequence)) throw Error('MESSAGE_SEQUENCE_EXHAUSTED');
  ReaderEbookHost.postMessage(JSON.stringify({protocolVersion: 1, session, sequence, kind,
    value: {...value, layoutToken: active.token, layoutEpoch: active.epoch}}));
};
const fail = error => {
  if (retired) return;
  send('failure', {code: String(error?.message || error).slice(0, 512)});
  retired = true; confirmed = false; frame.style.pointerEvents = 'none'; publication?.dom.retire();
};
const json = async path => {
  const response = await fetch(path, {cache: 'no-store', credentials: 'omit', redirect: 'error'});
  if (!response.ok) throw Error('LOCAL_RESOURCE_' + response.status);
  return response.json();
};
const selectionActive = () => publication?.win.getSelection()?.isCollapsed === false;
const applyScrollPolicy = () => {
  if (!publication) return;
  const value = confirmed && scrollEnabled ? 'auto' : 'hidden';
  publication.doc.documentElement.style.setProperty('overflow-y', value, 'important');
  publication.doc.body.style.setProperty('overflow-y', value, 'important');
};
const reportSelection = () => { if (confirmed && !retired) send('selection', {active: selectionActive()}); };
const nextPaint = win => new Promise(resolve => win.requestAnimationFrame(() => win.requestAnimationFrame(resolve)));
let documentPromise;
const loadDocument = captured => documentPromise ||= (async () => {
  diagnosticPhase(captured, 'CONFIG_FETCH_BEGIN');
  const data = await json(`/__reader/config/${session}.json`);
  diagnosticPhase(captured, 'CONFIG_FETCH_COMPLETE');
  if (retired || data.protocolVersion !== 1 || data.session !== session) throw Error('FOREIGN_BOOTSTRAP');
  const expected = new URL(data.entryUrl);
  if (expected.origin !== location.origin || !expected.pathname.startsWith(`/publication/${session}/`) || expected.search || expected.hash)
    throw Error('FOREIGN_PUBLICATION_URL');
  diagnosticPhase(captured, 'FRAME_LOAD_BEGIN');
  await new Promise((resolve, reject) => {
    const loaded = () => {
      try {
        if (frame.contentWindow.location.href !== expected.href) return; // Initial about:blank is not the publication.
        frame.removeEventListener('load', loaded); resolve();
      } catch (error) { reject(error); }
    };
    frame.addEventListener('load', loaded); frame.addEventListener('error', () => reject(Error('PUBLICATION_LOAD_FAILED')), {once: true});
    frame.src = expected.href;
  });
  diagnosticPhase(captured, 'FRAME_LOAD_COMPLETE');
  const win = frame.contentWindow, doc = frame.contentDocument;
  if (retired || !doc || !doc.head || win.location.href !== expected.href) throw Error('FOREIGN_CHILD_DOCUMENT');
  const metadata = data.dom;
  if (!metadata || typeof metadata.originalIsContent !== 'boolean' || !Array.isArray(metadata.edits))
    throw Error('NATIVE_PROCESSED_TEXT_METADATA_REQUIRED');
  let input;
  if (metadata.originalIsContent) {
    if (Object.prototype.hasOwnProperty.call(metadata, 'original') || metadata.edits.length !== 0)
      throw Error('NATIVE_IDENTITY_ALIAS_MISMATCH');
    input = {...metadata, original: metadata.content}; // Explicit Native-proven identity, no second 16MiB text copy.
  } else {
    if (typeof metadata.original !== 'string' || metadata.original === metadata.content || metadata.edits.length === 0)
      throw Error('NATIVE_PROCESSED_EDITS_REQUIRED');
    input = metadata; // Preserve the exact supplied original/content/edits; never overwrite confirmed edits.
  }
  const dom = createReaderEbookDom({document: doc, window: win, Node: win.Node, NodeFilter: win.NodeFilter, Range: win.Range,
    isCurrent: () => !retired && frame.contentDocument === doc && win.document === doc,
    phase: code => diagnosticPhase(captured, code)}, input);
  diagnosticPhase(captured, 'DOM_ADMIT_BEGIN');
  dom.admit(); // Full canonical entry authenticates before the existing Range chapter crop.
  diagnosticPhase(captured, 'DOM_ADMIT_COMPLETE');
  publication = {win, doc, dom};
  const gesture = event => { if (event.isTrusted && confirmed) interacted = true; };
  for (const name of ['pointerdown', 'touchstart', 'wheel', 'keydown']) doc.addEventListener(name, gesture, {passive: true, capture: true});
  win.addEventListener('scroll', () => {
    if (!confirmed || !scrollEnabled || selectionActive() || !interacted || pending || retired) return;
    const captured = active;
    pending = win.requestAnimationFrame(() => {
      pending = 0;
      if (!current(captured) || !confirmed || !scrollEnabled || selectionActive()) return;
      try { const value = dom.visibleAnchor(); if (value?.rect) send('position', {scalar: value.scalar, rect: value.rect}); }
      catch (error) { fail(error); }
    });
  }, {passive: true});
  doc.addEventListener('click', event => {
    const node = event.target?.nodeType === win.Node.ELEMENT_NODE ? event.target : event.target?.parentElement;
    const link = node?.closest('a[href]');
    if (!link) {
      if (event.isTrusted && confirmed && !selectionActive()) send('tap', {});
      return;
    }
    event.preventDefault();
    if (selectionActive() || !scrollEnabled) return;
    try {
      const target = new URL(link.getAttribute('href'), doc.baseURI);
      if (!confirmed || target.origin !== expected.origin || target.pathname !== expected.pathname || target.search || !target.hash)
        throw Error('LINK_OUTSIDE_CURRENT_CHAPTER_NOT_ADMITTED');
      const element = doc.getElementById(decodeURIComponent(target.hash.slice(1)));
      if (!element || !doc.body.contains(element)) throw Error('LINK_TARGET_OUTSIDE_CURRENT_CHAPTER');
      const scalar = dom.scalarForBoundary(element, 0);
      if (scalar === null) throw Error('LINK_TARGET_HAS_NO_CANONICAL_POSITION');
      interacted = event.isTrusted; dom.restorePosition(scalar);
    } catch (error) { fail(error); }
  }, true);
  doc.addEventListener('selectionchange', reportSelection);
  if (!win.ResizeObserver) throw Error('RESIZE_OBSERVER_REQUIRED');
  new win.ResizeObserver(() => {
    if (confirmed && !retired) send('ready', {phase: 'invalidated'});
  }).observe(doc.body);
  return publication;
})();
const waitAssets = async ({doc}) => {
  if (!doc.fonts) throw Error('FONT_LOADING_API_REQUIRED');
  for (const image of doc.images) image.loading = 'eager';
  await Promise.all(Array.from(doc.images, image => image.complete ? Promise.resolve() : new Promise((resolve, reject) => {
    image.addEventListener('load', resolve, {once: true}); image.addEventListener('error', () => reject(Error('PUBLICATION_IMAGE_FAILED')), {once: true});
  })));
  for (const image of doc.images) if (!image.complete || image.naturalWidth <= 0) throw Error('PUBLICATION_IMAGE_UNAVAILABLE');
  await doc.fonts.ready;
  // Chromium resolves missing embedded faces through its normal font fallback.
  // Layout and canonical-position admission still use the actually rendered DOM.
  for (const sheet of doc.querySelectorAll('link[rel~="stylesheet"]')) if (!sheet.disabled && !sheet.sheet) throw Error('PUBLICATION_STYLESHEET_UNAVAILABLE');
};
const measurement = captured => {
  if (!current(captured) || !publication || frame.contentDocument !== publication.doc) throw Error('RETIRED_LAYOUT');
  return {scalar: captured.layout.anchor, rect: publication.dom.measurePosition(captured.layout.anchor)};
};
async function configure(epoch, token) {
  if (retired || !uuid.test(token)) return;
  integer(epoch); if (active && epoch <= active.epoch) return;
  const captured = {epoch, token, serial: ++serial, layout: null, observerStartedMs: diagnosticClock()}; active = captured;
  diagnosticPhase(captured, 'CONFIGURE_ENTER');
  confirmed = false; interacted = false; frame.style.pointerEvents = 'none'; applyScrollPolicy();
  try {
    // Only native-owned immutable metadata is read. A newer native viewport may
    // precede module startup; adopt that advertised epoch rather than retrying a stale URI.
    diagnosticPhase(captured, 'LAYOUT_FETCH_BEGIN');
    const layout = await json(`/__reader/layout-current/${session}.json`);
    if (!current(captured)) return;
    integer(layout.layoutEpoch);
    if (layout.session !== session || !uuid.test(layout.layoutToken) || layout.layoutEpoch < epoch ||
        (layout.layoutEpoch === epoch && layout.layoutToken !== token)) throw Error('FOREIGN_LAYOUT_CONFIG');
    captured.epoch = layout.layoutEpoch; captured.token = layout.layoutToken;
    diagnosticPhase(captured, 'LAYOUT_FETCH_COMPLETE');
    integer(layout.anchor); if (typeof layout.userScrollEnabled !== 'boolean') throw Error('SCROLL_POLICY_REQUIRED');
    scrollEnabled = layout.userScrollEnabled; captured.layout = layout;
    diagnosticPhase(captured, 'DOCUMENT_WAIT_BEGIN');
    const book = await loadDocument(captured); if (!current(captured)) return;
    diagnosticPhase(captured, 'DOCUMENT_WAIT_COMPLETE');
    book.dom.applyLayout(layout.options);
    diagnosticPhase(captured, 'ASSET_WAIT_BEGIN');
    await waitAssets(book); if (!current(captured)) return;
    diagnosticPhase(captured, 'ASSET_WAIT_COMPLETE');
    diagnosticPhase(captured, 'RESTORE_BEGIN');
    book.dom.restorePosition(layout.anchor);
    diagnosticPhase(captured, 'RESTORE_COMPLETE');
    await nextPaint(book.win);
    if (current(captured)) { diagnosticPhase(captured, 'READY_DOM_SEND'); send('ready', {phase: 'dom', ...measurement(captured)}); }
  } catch (error) { if (current(captured)) fail(error); }
}
Object.defineProperty(window, 'ReaderEbookRuntime', {value: Object.freeze({
  configure,
  probe(epoch, token) {
    const captured = active;
    if (!captured || captured.epoch !== epoch || captured.token !== token || !captured.layout) return;
    try { diagnosticPhase(captured, 'READY_PAINT_SEND'); send('ready', {phase: 'paint', ...measurement(captured)}); } catch (error) { fail(error); }
  },
  confirm(epoch, token, enabled) {
    const captured = active;
    if (!captured || captured.epoch !== epoch || captured.token !== token || retired) return;
    if (typeof enabled !== 'boolean') return;
    diagnosticPhase(captured, 'READY_CONFIRM');
    confirmed = true; scrollEnabled = enabled; interacted = false; frame.style.pointerEvents = 'auto';
    applyScrollPolicy(); reportSelection();
  },
  setScrollEnabled(epoch, token, enabled) {
    if (!active || active.epoch !== epoch || active.token !== token || retired || typeof enabled !== 'boolean') return;
    scrollEnabled = enabled; applyScrollPolicy();
  },
  retire() { retired = true; confirmed = false; publication?.dom.retire(); frame.style.pointerEvents = 'none'; }
}), writable: false, configurable: false});
window.addEventListener('error', event => fail(Error(event.message || 'TRUSTED_RUNTIME_ERROR')));
window.addEventListener('unhandledrejection', event => fail(event.reason));
configure(Number(document.querySelector('meta[name="reader-epoch"]').content), document.querySelector('meta[name="reader-token"]').content);
