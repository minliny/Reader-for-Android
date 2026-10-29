import {createReaderEbookDom} from './reader-ebook-dom.mjs';
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const session = document.querySelector('meta[name="reader-render"]').content;
const frame = document.getElementById('publication');
if (!uuid.test(session) || location.origin !== 'https://reader-ebook.invalid' || location.pathname !== '/__reader/shell.html')
  throw Error('TRUSTED_SHELL_CONTEXT_REQUIRED');
let retired = false, serial = 0, sequence = 0, active = null, publication = null, confirmed = false, interacted = false, pending = 0, scrollEnabled = false;
const integer = value => { if (!Number.isSafeInteger(value) || value < 0) throw Error('EXACT_INTEGER_REQUIRED'); return value; };
const current = captured => !retired && active === captured;
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
const loadDocument = () => documentPromise ||= (async () => {
  const data = await json(`/__reader/config/${session}.json`);
  if (retired || data.protocolVersion !== 1 || data.session !== session) throw Error('FOREIGN_BOOTSTRAP');
  const expected = new URL(data.entryUrl);
  if (expected.origin !== location.origin || !expected.pathname.startsWith(`/publication/${session}/`) || expected.search || expected.hash)
    throw Error('FOREIGN_PUBLICATION_URL');
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
  const win = frame.contentWindow, doc = frame.contentDocument;
  if (retired || !doc || !doc.head || win.location.href !== expected.href) throw Error('FOREIGN_CHILD_DOCUMENT');
  const input = {...data.dom, original: data.dom.chapterText, content: data.dom.chapterText, edits: []};
  const dom = createReaderEbookDom({document: doc, window: win, Node: win.Node, NodeFilter: win.NodeFilter, Range: win.Range,
    isCurrent: () => !retired && frame.contentDocument === doc && win.document === doc}, input);
  dom.admit(); // Full canonical entry authenticates before the existing Range chapter crop.
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
  for (const face of doc.fonts) if (face.status === 'error') throw Error('PUBLICATION_FONT_UNAVAILABLE');
  for (const sheet of doc.querySelectorAll('link[rel~="stylesheet"]')) if (!sheet.disabled && !sheet.sheet) throw Error('PUBLICATION_STYLESHEET_UNAVAILABLE');
};
const measurement = captured => {
  if (!current(captured) || !publication || frame.contentDocument !== publication.doc) throw Error('RETIRED_LAYOUT');
  return {scalar: captured.layout.anchor, rect: publication.dom.measurePosition(captured.layout.anchor)};
};
async function configure(epoch, token) {
  if (retired || !uuid.test(token)) return;
  integer(epoch); if (active && epoch <= active.epoch) return;
  const captured = {epoch, token, serial: ++serial, layout: null}; active = captured;
  confirmed = false; interacted = false; frame.style.pointerEvents = 'none'; applyScrollPolicy();
  try {
    // Only native-owned immutable metadata is read. A newer native viewport may
    // precede module startup; adopt that advertised epoch rather than retrying a stale URI.
    const layout = await json(`/__reader/layout-current/${session}.json`);
    if (!current(captured)) return;
    integer(layout.layoutEpoch);
    if (layout.session !== session || !uuid.test(layout.layoutToken) || layout.layoutEpoch < epoch ||
        (layout.layoutEpoch === epoch && layout.layoutToken !== token)) throw Error('FOREIGN_LAYOUT_CONFIG');
    captured.epoch = layout.layoutEpoch; captured.token = layout.layoutToken;
    integer(layout.anchor); if (typeof layout.userScrollEnabled !== 'boolean') throw Error('SCROLL_POLICY_REQUIRED');
    scrollEnabled = layout.userScrollEnabled; captured.layout = layout;
    const book = await loadDocument(); if (!current(captured)) return;
    book.dom.applyLayout(layout.options);
    await waitAssets(book); if (!current(captured)) return;
    book.dom.restorePosition(layout.anchor); await nextPaint(book.win);
    if (current(captured)) send('ready', {phase: 'dom', ...measurement(captured)});
  } catch (error) { if (current(captured)) fail(error); }
}
Object.defineProperty(window, 'ReaderEbookRuntime', {value: Object.freeze({
  configure,
  probe(epoch, token) {
    const captured = active;
    if (!captured || captured.epoch !== epoch || captured.token !== token || !captured.layout) return;
    try { send('ready', {phase: 'paint', ...measurement(captured)}); } catch (error) { fail(error); }
  },
  confirm(epoch, token, enabled) {
    if (!active || active.epoch !== epoch || active.token !== token || retired) return;
    if (typeof enabled !== 'boolean') return;
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
