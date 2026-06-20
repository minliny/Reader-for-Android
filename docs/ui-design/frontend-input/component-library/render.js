(function attachReaderComponentLibrary(window) {
  function esc(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function pct(value) {
    const numeric = Number(value);
    return `${Math.max(0, Math.min(100, Number.isFinite(numeric) ? numeric : 0))}%`;
  }

  function icon(name) {
    const icons = {
      search: '<svg class="rl-icon" viewBox="0 0 48 48"><circle cx="21" cy="21" r="12"></circle><path d="M31 31 42 42"></path></svg>',
      more: '<svg class="rl-icon" viewBox="0 0 48 48"><circle cx="24" cy="10" r="2.8" fill="currentColor" stroke="none"></circle><circle cx="24" cy="24" r="2.8" fill="currentColor" stroke="none"></circle><circle cx="24" cy="38" r="2.8" fill="currentColor" stroke="none"></circle></svg>',
      back: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M29 10 15 24l14 14"></path><path d="M16 24h27"></path></svg>',
      sort: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M8 13h22"></path><path d="M8 24h16"></path><path d="M8 35h10"></path><path d="M34 12v22"></path><path d="m27 28 7 7 7-7"></path></svg>',
      settings: '<svg class="rl-icon" viewBox="0 0 48 48"><circle cx="24" cy="24" r="7"></circle><path d="M24 5v6M24 37v6M7.5 14.5l5.2 3M35.3 30.5l5.2 3M7.5 33.5l5.2-3M35.3 17.5l5.2-3M5 24h6M37 24h6"></path></svg>',
      bookshelf: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M12 10c5 0 9 1.5 12 5v25c-3-3.5-7-5-12-5V10Z" fill="currentColor" stroke="none"></path><path d="M36 10c-5 0-9 1.5-12 5v25c3-3.5 7-5 12-5V10Z" fill="currentColor" stroke="none"></path></svg>',
      discover: '<svg class="rl-icon" viewBox="0 0 48 48"><circle cx="24" cy="24" r="16"></circle><path d="m30 17-4 12-8 4 4-12 8-4Z"></path></svg>',
      rss: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M10 30a8 8 0 0 1 8 8"></path><path d="M10 20a18 18 0 0 1 18 18"></path><path d="M10 10a28 28 0 0 1 28 28"></path><circle cx="13" cy="35" r="2" fill="currentColor" stroke="none"></circle></svg>',
      "auto-page": '<svg class="rl-icon" viewBox="0 0 48 48"><circle cx="24" cy="24" r="17"></circle><path d="M22 16v16l12-8-12-8Z" fill="currentColor" stroke="none"></path><path d="M9 24h4M35 24h4M24 9v4M24 35v4"></path></svg>',
      replace: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M33 8h7v7"></path><path d="M8 22v-4a10 10 0 0 1 10-10h20"></path><path d="M15 40H8v-7"></path><path d="M40 26v4a10 10 0 0 1-10 10H10"></path></svg>',
      directory: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M13 12h22"></path><path d="M13 24h22"></path><path d="M13 36h22"></path><path d="M7 12h.01M7 24h.01M7 36h.01"></path></svg>',
      tts: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M12 26v-4"></path><path d="M18 34V14"></path><path d="M24 39V9"></path><path d="M30 34V14"></path><path d="M36 26v-4"></path></svg>',
      appearance: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M8 35 18 12h2l10 23"></path><path d="M12 27h14"></path><path d="M31 35V20"></path><path d="M31 20c7 0 9 3 9 7v8"></path></svg>',
      source: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M11 16h22l-6-6"></path><path d="M37 32H15l6 6"></path><path d="M33 10l6 6-6 6"></path><path d="M15 38l-6-6 6-6"></path></svg>',
      storage: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M10 15c0-4 6-7 14-7s14 3 14 7-6 7-14 7-14-3-14-7Z"></path><path d="M10 15v18c0 4 6 7 14 7s14-3 14-7V15"></path><path d="M10 24c0 4 6 7 14 7s14-3 14-7"></path></svg>',
      sync: '<svg class="rl-icon" viewBox="0 0 48 48"><path d="M33 8h7v7"></path><path d="M8 22v-4a10 10 0 0 1 10-10h20"></path><path d="M15 40H8v-7"></path><path d="M40 26v4a10 10 0 0 1-10 10H10"></path></svg>'
    };
    return icons[name] || icons.settings;
  }

  function componentCard(title, note, body) {
    return `
      <article class="rl-component-card">
        <div class="rl-component-meta">
          <h3>${esc(title)}</h3>
          <p>${esc(note)}</p>
        </div>
        <div class="rl-component-demo">${body}</div>
      </article>`;
  }

  function swatches(colors) {
    return `
      <div class="rl-swatch-grid">
        ${colors.map((color) => `
          <div class="rl-swatch">
            <div class="rl-swatch-color" style="--swatch: ${esc(color.value)}"></div>
            <div class="rl-swatch-name">${esc(color.name)}<br>${esc(color.value)}</div>
          </div>`).join("")}
      </div>`;
  }

  function chips(items) {
    return `<div class="rl-chip-row">${items.map((label, index) => `<button class="rl-chip${index === 0 ? " is-active" : ""}" type="button">${esc(label)}</button>`).join("")}</div>`;
  }

  function progress(value) {
    return `<div class="rl-progress" style="--value: ${pct(value)}"><span></span></div>`;
  }

  function slider(value) {
    return `
      <div class="rl-slider" style="--value: ${pct(value)}">
        <span class="rl-slider-track"></span>
        <span class="rl-slider-fill"></span>
        <span class="rl-slider-thumb"></span>
      </div>`;
  }

  function bookCard(book) {
    return `
      <article class="rl-book-card">
        <img class="rl-book-cover" src="${esc(book.cover)}" alt="${esc(book.title)}封面">
        <h4 class="rl-book-title">${esc(book.title)}</h4>
        <p class="rl-book-meta">${esc(book.author)}</p>
        <p class="rl-book-meta">${esc(book.chapter)}</p>
        ${progress(book.progress)}
      </article>`;
  }

  function bookRow(book) {
    return `
      <article class="rl-book-row">
        <img class="rl-book-cover" src="${esc(book.cover)}" alt="${esc(book.title)}封面">
        <div>
          <h4 class="rl-row-title">${esc(book.title)}</h4>
          <p class="rl-row-meta">${esc(book.author)} · ${esc(book.chapter)}</p>
        </div>
        <span class="rl-row-value">${esc(book.progress)}%</span>
      </article>`;
  }

  function rows(items) {
    return `
      <div class="rl-demo-stack">
        ${items.map((item) => `
          <article class="rl-setting-row">
            <span class="rl-row-icon">${icon(item.icon)}</span>
            <div>
              <h4 class="rl-row-title">${esc(item.title)}</h4>
              <p class="rl-row-meta">${esc(item.meta)}</p>
            </div>
            <span class="rl-row-value">${esc(item.value)}</span>
          </article>`).join("")}
      </div>`;
  }

  function mainNav(items) {
    return `
      <nav class="rl-bottom-nav" aria-label="公共主导航">
        ${items.map((item) => `
          <button class="rl-bottom-nav-item${item.active ? " is-active" : ""}" type="button">
            ${icon(item.type)}
            <span>${esc(item.label)}</span>
          </button>`).join("")}
      </nav>`;
  }

  function actions(items) {
    return `
      <div class="rl-quick-grid">
        ${items.map((item) => `
          <button class="rl-action" type="button">
            <span class="rl-action-icon">${icon(item.type)}</span>
            <span>${esc(item.label)}</span>
          </button>`).join("")}
      </div>`;
  }

  function modules(items) {
    return `
      <div class="rl-module-grid">
        ${items.map((item) => `
          <button class="rl-action${item.active ? " is-active" : ""}" type="button">
            <span class="rl-action-icon">${icon(item.type)}</span>
            <span>${esc(item.label)}</span>
          </button>`).join("")}
      </div>`;
  }

  function verticalSlider(value) {
    return `
      <div class="rl-panel rl-vertical-slider">
        <strong>亮度</strong>
        ${icon("settings")}
        <div class="rl-vertical-rail" style="--value: ${pct(value)}">
          <span class="rl-vertical-fill"></span>
          <span class="rl-vertical-thumb"></span>
        </div>
        <span>A</span>
        <span>系统</span>
      </div>`;
  }

  function states(items) {
    return `
      <div class="rl-demo-stack">
        <div class="rl-skeleton" aria-label="加载态">
          <span class="rl-skeleton-block"></span>
          <span class="rl-skeleton-line"></span>
          <span class="rl-skeleton-line is-short"></span>
        </div>
        ${items.map((item) => `
          <div class="rl-state-block">
            <div>
              <h4 class="rl-state-title">${esc(item.title)}</h4>
              <p class="rl-state-copy">${esc(item.copy)}</p>
            </div>
          </div>`).join("")}
      </div>`;
  }

  function renderComponentLibrary(target, data) {
    target.innerHTML = `
      <main class="rl-library">
        <header class="rl-library-header">
          <div>
            <h1 class="rl-library-title">${esc(data.meta.title)}</h1>
            <p class="rl-library-summary">${esc(data.meta.summary)}</p>
          </div>
          <span class="rl-library-badge">v${esc(data.meta.version)}</span>
        </header>

        <section class="rl-section">
          <div class="rl-section-head">
            <h2 class="rl-section-title">Foundations</h2>
            <p class="rl-section-note">所有页面先引用共享 token，再使用组件库局部结构。</p>
          </div>
          <div class="rl-component-grid is-two">
            ${componentCard("Color Tokens", "纸张、墨色、主色、强调色和边线。", swatches(data.colors))}
            ${componentCard("Typography / Elevation", "阅读正文用 serif，操作和列表用 sans。", `
              <div class="rl-demo-stack">
                <div class="rl-card"><h4 class="rl-card-title">卡片标题</h4><p class="rl-card-copy">使用共享圆角、边线和阴影，禁止随页面任意漂移。</p></div>
                <div class="rl-panel" style="padding:18px">Panel / Sheet 内层容器</div>
              </div>`)}
          </div>
        </section>

        <section class="rl-section">
          <div class="rl-section-head">
            <h2 class="rl-section-title">App Shell</h2>
            <p class="rl-section-note">主标签页、设置页和大部分书架链路先复用此组骨架。</p>
          </div>
          <div class="rl-component-grid">
            ${componentCard("AppFrame / StatusBar", "手机画布、系统状态栏和安全区。", `
              <div class="rl-app-frame" style="--frame-width: 254px; --frame-height: 180px">
                <header class="rl-status-bar" style="height:46px; padding:16px 18px 0; font-size:15px">
                  <span>10:30</span>
                  <span class="rl-system-icons" style="gap:5px; transform:scale(.58); transform-origin:right center">
                    <span class="rl-wifi"></span><span class="rl-signal"></span><span class="rl-battery"></span><span>82%</span>
                  </span>
                </header>
              </div>`)}
            ${componentCard("AppTopBar", "主页面标题、搜索和更多。", `
              <div class="rl-app-top-bar" style="padding:0">
                <h3 class="rl-app-title" style="font-size:34px">设置</h3>
                <div class="rl-top-actions" style="gap:18px">${icon("search")}${icon("more")}</div>
              </div>`)}
            ${componentCard("IconButton", "顶部搜索、更多、返回、设置等图标按钮。", `
              <div class="rl-demo-row">
                <button class="rl-icon-button is-filled">${icon("search")}</button>
                <button class="rl-icon-button">${icon("more")}</button>
                <button class="rl-icon-button is-filled">${icon("back")}</button>
                <button class="rl-icon-button">${icon("sort")}</button>
              </div>`)}
            ${componentCard("MainNav", "书架、发现、RSS、设置四个公共主导航按钮固定结构。", mainNav(data.bottomNav))}
            ${componentCard("SearchBar", "搜索页、书源页和内容搜索的基础输入槽。", `
              <div class="rl-search-bar">${icon("search")}<span>搜索书名、作者或书源</span><strong>取消</strong></div>`)}
          </div>
        </section>

        <section class="rl-section">
          <div class="rl-section-head">
            <h2 class="rl-section-title">Basic Controls</h2>
            <p class="rl-section-note">Chip、进度条和滑杆在书架、阅读控制、筛选页共用。</p>
          </div>
          <div class="rl-component-grid">
            ${componentCard("Chip", "分组、筛选、状态切换。", chips(data.chips))}
            ${componentCard("ProgressBar / Slider", "阅读进度和章节进度。", `<div class="rl-demo-stack">${progress(38)}${slider(42)}</div>`)}
            ${componentCard("Badge / Switch", "设置行状态提示和二级页开关。", `
              <div class="rl-demo-row">
                <span class="rl-badge">可用</span>
                <span class="rl-badge is-warning">待授权</span>
                <span class="rl-badge is-muted">未设置</span>
                <span class="rl-switch is-on"></span>
                <span class="rl-switch"></span>
              </div>`)}
          </div>
          <div class="rl-component-grid" style="margin-top:18px">
            ${componentCard("QuickAction", "阅读控制层快捷操作按钮。", actions(data.quickActions))}
            ${componentCard("ActionButton", "空态、导入、重试和管理入口。", `
              <div class="rl-demo-stack">
                <button class="rl-primary-button" type="button">添加书籍</button>
                <button class="rl-secondary-button" type="button">导入本地书</button>
                <button class="rl-text-button" type="button">换个分组看看</button>
              </div>`)}
          </div>
        </section>

        <section class="rl-section">
          <div class="rl-section-head">
            <h2 class="rl-section-title">Cards & Rows</h2>
            <p class="rl-section-note">书籍、设置、书源、RSS 和普通列表的基础单元。</p>
          </div>
          <div class="rl-component-grid">
            ${componentCard("BookCard", "封面网格卡片。", bookCard(data.book))}
            ${componentCard("BookRow", "搜索、目录、最近阅读可复用的行。", bookRow(data.book))}
            ${componentCard("ListRow / SettingRow", "设置链路、书源管理和缓存管理基础行。", rows(data.rows))}
          </div>
        </section>

        <section class="rl-section">
          <div class="rl-section-head">
            <h2 class="rl-section-title">Sheets & Panels</h2>
            <p class="rl-section-note">底表、阅读面板、模块导航和亮度滑杆。</p>
          </div>
          <div class="rl-component-grid">
            ${componentCard("BottomSheet", "排序筛选、书籍操作、阅读控制层外壳。", `
              <div class="rl-bottom-sheet">
                <span class="rl-grabber"></span>
                <div class="rl-demo-stack">${actions(data.quickActions)}${slider(42)}</div>
              </div>`)}
            ${componentCard("ReaderModuleNav", "目录、朗读、界面、设置模块切换。", modules(data.moduleNav))}
            ${componentCard("BrightnessSlider", "阅读控制层竖向亮度控制。", verticalSlider(56))}
          </div>
        </section>

        <section class="rl-section">
          <div class="rl-section-head">
            <h2 class="rl-section-title">States</h2>
            <p class="rl-section-note">加载、空、错误、权限状态必须语义分离。</p>
          </div>
          <div class="rl-component-grid is-two">
            ${componentCard("EmptyStateCard", "空状态必须说明原因，并提供一个主行动作。", `
              <div class="rl-empty-state-card">
                <svg class="rl-empty-illustration" viewBox="0 0 120 120">
                  <rect x="20" y="18" width="80" height="84" rx="13" fill="none" stroke="currentColor" stroke-width="4"></rect>
                  <path d="M60 30v60M34 48h26M34 66h26M34 84h26M60 48h26M60 66h26M60 84h26" stroke="currentColor" stroke-width="3"></path>
                </svg>
                <h4 class="rl-empty-title">暂无书籍</h4>
                <p class="rl-empty-copy">可以搜索书籍，也可以导入本地书。</p>
                <button class="rl-primary-button" type="button">添加书籍</button>
              </div>`)}
            ${componentCard("Loading / Empty / Error", "所有页面必须使用稳定容器，避免布局跳动。", states(data.states))}
            ${componentCard("Conversion Guardrail", "页面转换时必须先查组件库，缺组件再扩展。", `
              <div class="rl-state-block">
                <div>
                  <h4 class="rl-state-title">先复用，再扩展</h4>
                  <p class="rl-state-copy">新增页面不得重新定义顶部栏、底部导航、Chip、卡片、滑杆、底表和状态块。</p>
                </div>
              </div>`)}
          </div>
        </section>
      </main>`;
  }

  window.ReaderComponentLibrary = {
    renderComponentLibrary
  };
})(window);
