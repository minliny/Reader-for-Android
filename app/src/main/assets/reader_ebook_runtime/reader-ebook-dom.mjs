/** Unwired A3 draft. iOS 71773 same-byte DOM business core is delimited below.
 * Only trusted shell code calls this module against a script-disabled child document.
 * No HTML parser, text diff, URL rewriter, native message bridge, timer or global singleton.
 */
export function createReaderEbookDom(context, input) {
  const {document, window, Node, NodeFilter, Range, isCurrent} = context;
  if (!document || !window || typeof isCurrent !== 'function' || !document.head)
    throw Error('EXPLICIT_CHILD_DOCUMENT_CONTEXT_REQUIRED');
  if (window.document !== document || window.Node !== Node || window.NodeFilter !== NodeFilter || window.Range !== Range)
    throw Error('FOREIGN_DOM_REALM');
  let state = 'CREATED';
  const current = () => {
    let admitted = false;
    try { admitted = state !== 'RETIRED' && state !== 'FAILED' && window.document === document && isCurrent() === true; } catch {}
    if (!admitted) throw Error('RETIRED_DOCUMENT_OR_READER_LEASE');
  };
  const countScalars = text => {
    if (typeof text !== 'string') throw Error('CORE_TEXT_REQUIRED');
    let count = 0;
    for (const c of text) { const value = c.codePointAt(0); if (value >= 0xd800 && value <= 0xdfff) throw Error('UNPAIRED_SURROGATE'); count++; }
    return count;
  };
  const integer = (value, maximum) => {
    if (!Number.isSafeInteger(value) || value < 0 || value > maximum) throw Error('EXACT_SCALAR_REQUIRED');
    return value;
  };
  const scalarCount = countScalars(input.content), fullCount = countScalars(input.canonical);
  if (input.original !== input.content || !Array.isArray(input.edits) || input.edits.length !== 0)
    throw Error('CORE_PROCESSED_EDITS_REQUIRE_AN_APPROVED_ADAPTER');
  const start = integer(input.start, fullCount), end = integer(input.end, fullCount);
  if (start > end || input.scalarCount !== scalarCount) throw Error('CORE_WINDOW_MISMATCH');
  const config = {canonical: input.canonical, original: input.original, content: input.content,
    start, end, scalarCount, offset: integer(input.offset, scalarCount),
    topInset: integer(input.topInset, 100000), edits: []};
  current();
// BEGIN SAME-BYTE iOS ReaderEbookScript.swift:105-233
const whitespace=c=>/^\p{White_Space}$/u.test(c);
const significant=text=>{const values=[];let scalar=0;for(const c of text){if(!whitespace(c))values.push({c,scalar});scalar++;}return values;};
let points=[],expected=[],ready=false,lastAnchor=config.offset,userMoving=false,scrollTimer=0,pendingFrame=0,selectionActive=false,pageTurning=false,pageEpoch=0,layoutGeneration=0;
const body=document.body||document.documentElement;
const style=document.createElementNS('http://www.w3.org/1999/xhtml','style');document.head.appendChild(style);
const decodedWalker=()=>document.createTreeWalker(body,NodeFilter.SHOW_ELEMENT|NodeFilter.SHOW_TEXT,{
    acceptNode(node){
      if(node.nodeType===Node.ELEMENT_NODE){
        const tag=node.localName.toLowerCase();
        if(['script','style','template'].includes(tag)||node.hasAttribute('data-reader-overlay'))return NodeFilter.FILTER_REJECT;
        if(['img','image'].includes(tag))return NodeFilter.FILTER_ACCEPT;
        return NodeFilter.FILTER_SKIP;
      }
      return NodeFilter.FILTER_ACCEPT;
    }
  });
const walk=()=>{
  const found=[],walker=decodedWalker();
  let node;
  while((node=walker.nextNode())){
    if(node.nodeType===Node.TEXT_NODE){let unit=0;for(const c of node.data){if(!whitespace(c))found.push({node,start:unit,end:unit+c.length,c});unit+=c.length;}}
    else if(node.getAttribute('src')||node.getAttribute('data-src')||node.getAttribute('href')||node.getAttribute('xlink:href'))found.push({node,start:0,end:1,c:'\uFFFC',image:true});
  }
  return found;
};
const match=(canonical,stage)=>{
  const reference=significant(canonical),actual=walk();
  if(reference.length!==actual.length)throw Error(`canonical-length:${stage}:expected=${reference.length}:actual=${actual.length}`);
  for(let i=0;i<reference.length;i++)if(reference[i].c!==actual[i].c)throw Error(`canonical-text:${stage}:ordinal=${i}:expected=U+${reference[i].c.codePointAt(0).toString(16)}:actual=U+${actual[i].c.codePointAt(0).toString(16)}`);
  return {reference,actual};
};
const lower=(array,value,key)=>{let lo=0,hi=array.length;while(lo<hi){const mid=lo+((hi-lo)>>1);if(array[mid][key]<value)lo=mid+1;else hi=mid;}return lo;};
const endpoint=(point,end=false)=>{
  if(point.image){const parent=point.node.parentNode;return {node:parent,offset:Array.prototype.indexOf.call(parent.childNodes,point.node)+(end?1:0)};}
  return {node:point.node,offset:end?point.end:point.start};
};
const boundary=(array,reference,scalar)=>{
  const index=lower(reference,scalar,'scalar');
  if(index<array.length)return endpoint(array[index]);
  if(array.length)return endpoint(array[array.length-1],true);
  return {node:body,offset:0};
};
const setRange=(range,method,point)=>range[method](point.node,point.offset);
const documentWindow=()=>{
  // Authenticate the entire original entry, including text outside this chapter.
  // Only the two requested DOM/UTF-16 endpoints survive the streaming comparison;
  // full-flow character objects and node-reference arrays are never constructed.
  const reference=config.canonical[Symbol.iterator](),walker=decodedWalker();
  let scalar=0,referenceScalar=0,referenceCount=0,actualCount=0,mismatch='';
  let start=null,end=null,lastNode=null,lastEnd=0,lastImage=false;
  const nextReference=()=>{
    for(let value=reference.next();!value.done;value=reference.next()){
      const index=scalar++;
      if(!whitespace(value.value)){referenceScalar=index;referenceCount++;return value.value;}
    }
    return null;
  };
  const accept=(node,unit,past,c,image=false)=>{
    const expectedCharacter=nextReference();
    if(expectedCharacter!==null){
      if(start===null&&referenceScalar>=config.start)start=endpoint({node,start:unit,end:past,image});
      if(end===null&&referenceScalar>=config.end)end=endpoint({node,start:unit,end:past,image});
      if(!mismatch&&expectedCharacter!==c)mismatch=`canonical-text:document:ordinal=${actualCount}:expected=U+${expectedCharacter.codePointAt(0).toString(16)}:actual=U+${c.codePointAt(0).toString(16)}`;
    }
    actualCount++;lastNode=node;lastEnd=past;lastImage=image;
  };
  let node;
  while((node=walker.nextNode())){
    if(node.nodeType===Node.TEXT_NODE){
      let unit=0;for(const c of node.data){const past=unit+c.length;if(!whitespace(c))accept(node,unit,past,c);unit=past;}
    }else if(node.getAttribute('src')||node.getAttribute('data-src')||node.getAttribute('href')||node.getAttribute('xlink:href'))accept(node,0,1,'\uFFFC',true);
  }
  // Exhaust the canonical iterator even when the DOM ended first. Keep the
  // original length-first failure contract; no Range may mutate the DOM yet.
  while(nextReference()!==null){}
  if(referenceCount!==actualCount)throw Error(`canonical-length:document:expected=${referenceCount}:actual=${actualCount}`);
  if(mismatch)throw Error(mismatch);
  const pastLast=()=>lastNode?endpoint({node:lastNode,start:lastEnd,end:lastEnd,image:lastImage},true):{node:body,offset:0};
  return {start:start||pastLast(),end:end||pastLast()};
};
const restrictChapter=()=>{
  // Keep original semantic ancestors, document styles, tables, lists and links.
  // Range is the browser's native DOM operation, not a second HTML parser.
  document.querySelectorAll('body style,body link[rel~="stylesheet"]').forEach(node=>document.head.appendChild(node.cloneNode(true)));
  const chapter=documentWindow(),range=document.createRange();
  setRange(range,'setStart',chapter.start);
  setRange(range,'setEnd',chapter.end);
  // Range clones partially selected descendants, but deliberately omits its
  // common ancestor. Preserve that semantic chain too (for example a chapter
  // consisting of one p/em, or a slice inside a table cell).
  const ancestors=[];let ancestor=range.commonAncestorContainer;
  if(ancestor.nodeType!==Node.ELEMENT_NODE)ancestor=ancestor.parentElement;
  while(ancestor&&ancestor!==body){ancestors.push(ancestor);ancestor=ancestor.parentElement;}
  let fragment=range.extractContents();
  for(const original of ancestors){const shell=original.cloneNode(false);shell.appendChild(fragment);fragment=shell;}
  body.replaceChildren(fragment);
  const originalMap=match(config.original,'chapter-original');
  // Apply Core's confirmed rule output. The standard Swift diff is supplied
  // as data; no JavaScript regex/replacement engine re-runs business rules.
  for(const edit of config.edits.slice().reverse()){
    const patch=document.createRange();
    setRange(patch,'setStart',boundary(originalMap.actual,originalMap.reference,edit.startScalar));
    const first=lower(originalMap.reference,edit.startScalar,'scalar'),past=lower(originalMap.reference,edit.endScalar,'scalar');
    // End inside the last character actually replaced. Ending before the next
    // character can cross an inline ancestor (em/span/link), causing Range to
    // collapse outside it and silently discard the replacement's semantics.
    const end=past>first?endpoint(originalMap.actual[past-1],true):boundary(originalMap.actual,originalMap.reference,edit.startScalar);
    setRange(patch,'setEnd',end);
    if(patch.startContainer===patch.endContainer&&patch.startContainer.nodeType===Node.TEXT_NODE){
      patch.startContainer.replaceData(patch.startOffset,patch.endOffset-patch.startOffset,edit.text);
    }else{patch.deleteContents();if(edit.text)patch.insertNode(document.createTextNode(edit.text));}
  }
  body.normalize();
  const visible=match(config.content,'chapter-processed');points=visible.actual;expected=visible.reference;
};
const rangeAt=point=>{const range=document.createRange(),start=endpoint(point);range.setStart(start.node,start.offset);range.collapse(true);return range;};
const scalarAt=(node,offset)=>{
  const target=document.createRange();try{target.setStart(node,offset);target.collapse(true);}catch{return null;}
  let lo=0,hi=points.length;
  while(lo<hi){const mid=lo+((hi-lo)>>1);if(rangeAt(points[mid]).compareBoundaryPoints(Range.START_TO_START,target)<0)lo=mid+1;else hi=mid;}
  return lo<expected.length?expected[lo].scalar:config.scalarCount;
};
const restore=scalar=>{
  lastAnchor=Math.max(0,Math.min(config.scalarCount,scalar));
  const index=lower(expected,lastAnchor,'scalar');
  const point=points[Math.min(index,points.length-1)];if(!point){window.scrollTo(0,0);return;}
  let rect;if(point.image)rect=point.node.getBoundingClientRect();else{const range=rangeAt(point);range.setEnd(point.node,point.end);rect=range.getBoundingClientRect();}
  window.scrollTo(0,Math.max(0,window.scrollY+rect.top-config.topInset));
};
// END SAME-BYTE iOS ReaderEbookScript.swift:105-233
// BEGIN SAME-BYTE iOS ReaderEbookScript.swift:254-257
const canonicalRange=(start,end)=>{
  if(start>=end)return null;const range=document.createRange();
  setRange(range,'setStart',boundary(points,expected,start));setRange(range,'setEnd',boundary(points,expected,end));return range;
};

// END SAME-BYTE iOS ReaderEbookScript.swift:254-257
  // Explicit child-realm bindings for unchanged iOS layout/visible code.
  let innerWidth = window.innerWidth, observedPosition = null;
  const getSelection = () => window.getSelection();
  const send = (kind, value) => { if (kind === 'position') observedPosition = value; };
// BEGIN iOS 71773 ReaderEbookScript.swift:234-253 WITH ANDROID VISIBLE-CANDIDATE ADMISSION
const colorAndMetrics=options=>{
  const font=options.fontURL?`@font-face{font-family:ReaderSelectedFace;src:url(${JSON.stringify(options.fontURL)});font-display:block}`:'';
  const selectable=options.longPressSelectText!==false?'text':'none';
  const variations=Object.entries(options.fontVariations||{}).filter(([tag,value])=>/^[\x20-\x7e]{4}$/.test(tag)&&Number.isFinite(value)).map(([tag,value])=>`${JSON.stringify(tag)} ${value}`).join(',')||'normal';
  const family=options.systemFont?'font-family:-apple-system!important;':font?'font-family:ReaderSelectedFace!important;':'';
  const paper=options.paper??'transparent';
  const css=`${font}html{background:${paper}!important;color-scheme:${options.dark?'dark':'light'}}body{background:transparent!important;color:${options.ink}!important;font-size:${options.fontSize}px!important;line-height:${options.lineHeight}!important;margin:0!important;padding:${options.topInset}px ${options.rightInset}px ${options.bottomInset}px ${options.leftInset}px!important;overflow-wrap:anywhere;writing-mode:horizontal-tb!important}body,body *{-webkit-user-select:${selectable}!important;user-select:${selectable}!important;font-variation-settings:${variations}!important;${family}}body *{color:inherit!important;background-color:transparent!important}body,p,li,dd,td{letter-spacing:${options.letterSpacing}px!important;text-align:${options.alignment}!important}p{margin-block-start:0!important;margin-block-end:${options.paragraphSpacing}px!important;text-indent:${options.firstLineIndentEm}em!important}img,svg,image{max-width:100%!important;height:auto}img[data-reader-missing]{display:block;box-sizing:border-box;min-height:96px;border:1px solid ${options.meta};color:${options.meta};font-size:14px}a{color:inherit}::selection,::highlight(reader-notes),::highlight(reader-follow){background:${options.selection}}${options.reduceMotion?'*,*::before,*::after{animation:none!important;transition:none!important}':''}`;
  const changed=style.textContent!==css||config.layoutToken!==options.layoutToken;if(style.textContent!==css)style.textContent=css;
  Object.assign(config,options);
  if(config.longPressSelectText===false){getSelection()?.removeAllRanges();selectionActive=false;}
  return changed;
};
const visiblePosition=(report=true,accept=()=>true)=>{
  if(!ready||!userMoving||selectionActive)return;
  for(const y of [config.topInset+4,config.topInset+20,config.topInset+48])for(const x of [0.1,0.5,0.9]){
    const range=document.caretRangeFromPoint(Math.max(config.horizontalInset+2,innerWidth*x),y);
    if(!range||!body.contains(range.startContainer))continue;
    const scalar=scalarAt(range.startContainer,range.startOffset);if(scalar!==null&&accept(scalar)){lastAnchor=scalar;if(report)send('position',scalar);return;}
  }
};
// END iOS 71773 ReaderEbookScript.swift:234-253 WITH ANDROID VISIBLE-CANDIDATE ADMISSION
  const admitted = () => { current(); if (state !== 'ADMITTED') throw Error('DOM_NOT_ADMITTED'); };
  const guarded = operation => {
    current();
    try { return operation(); }
    catch (error) { state = 'FAILED'; ready = false; throw error; }
  };
  const rectangleAt = scalar => {
    const ordinal = lower(expected, scalar, 'scalar');
    const point = points[Math.min(ordinal, points.length - 1)];
    if (!point) return null; // Empty projection is not an invented visible line.
    let rect;
    if (point.image) rect = point.node.getBoundingClientRect();
    else { const range = rangeAt(point); range.setEnd(point.node, point.end); rect = range.getBoundingClientRect(); }
    return {top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right,
      width: rect.width, height: rect.height, viewportWidth: window.innerWidth, viewportHeight: window.innerHeight};
  };
  const usableVisibleRectangle = rect => rect !== null &&
    ['top','bottom','left','right','width','height','viewportWidth','viewportHeight'].every(key => Number.isFinite(rect[key])) &&
    rect.right > rect.left && rect.bottom > rect.top && rect.width > 0 && rect.height > 0 &&
    rect.viewportWidth > 0 && rect.viewportHeight > 0 &&
    rect.bottom > config.topInset && rect.top < rect.viewportHeight - config.bottomInset &&
    rect.right > 0 && rect.left < rect.viewportWidth;
  return Object.freeze({
    admit() {
      return guarded(() => {
        if (state !== 'CREATED') throw Error('DOCUMENT_ALREADY_ADMITTED');
        state = 'AUTHENTICATING';
        restrictChapter(); // Full entry authenticated before Range mutates it.
        ready = true;
        state = 'ADMITTED';
        return Object.freeze({scalarCount: config.scalarCount});
      });
    },
    restorePosition(scalar) {
      admitted(); integer(scalar, config.scalarCount);
      return guarded(() => { restore(scalar); return {scalar: lastAnchor, rect: rectangleAt(lastAnchor)}; });
    },
    measurePosition(scalar) { admitted(); integer(scalar, config.scalarCount); return rectangleAt(scalar); },
    applyLayout(options) {
      admitted();
      return guarded(() => { innerWidth = window.innerWidth; return colorAndMetrics(options); });
    },
    visibleAnchor() {
      admitted();
      if (window.getSelection()?.isCollapsed === false) return null;
      innerWidth = window.innerWidth; observedPosition = null; userMoving = true;
      let acceptedRectangle = null;
      try {
        visiblePosition(true, scalar => {
          const rect = rectangleAt(scalar);
          if (!usableVisibleRectangle(rect)) return false; // Try the next caret; do not commit an invisible projection.
          acceptedRectangle = rect;
          return true;
        });
      } finally { userMoving = false; }
      return observedPosition === null ? null : {scalar: observedPosition, rect: acceptedRectangle};
    },
    scalarForBoundary(node, offset) {
      admitted();
      if (!node || node.ownerDocument !== document || !body.contains(node) || !Number.isSafeInteger(offset) || offset < 0)
        throw Error('FOREIGN_SELECTION_BOUNDARY');
      return scalarAt(node, offset);
    },
    selectionScalars() {
      admitted();
      const selection = window.getSelection();
      if (!selection || selection.rangeCount !== 1 || selection.isCollapsed) return null;
      const range = selection.getRangeAt(0);
      if (!body.contains(range.startContainer) || !body.contains(range.endContainer)) throw Error('FOREIGN_SELECTION');
      const first = scalarAt(range.startContainer, range.startOffset), past = scalarAt(range.endContainer, range.endOffset);
      if (first === null || past === null || first >= past) return null;
      return {startScalar: first, endScalar: past}; // Native derives text from authoritative Core content.
    },
    rangeForScalars(start, end) {
      admitted(); integer(start, config.scalarCount); integer(end, config.scalarCount);
      if (start > end) throw Error('INVALID_SCALAR_RANGE');
      return canonicalRange(start, end);
    },
    retire() {
      // Never mutate a new document through a reused frame WindowProxy.
      state = 'RETIRED'; ready = false; points = []; expected = [];
    },
  });
}
