(function attachReaderFrontendDemoDraft(window) {
  function esc(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function icon(name, className) {
    if (window.ReaderShellKit && window.ReaderShellKit.icon) {
      return window.ReaderShellKit.icon(name, className || "fd-icon");
    }
    if (window.ReaderAssetIcons && window.ReaderAssetIcons.renderIcon) {
      return window.ReaderAssetIcons.renderIcon(name, className || "fd-icon");
    }
    return `<span class="${esc(className || "fd-icon")}" data-icon-missing="${esc(name)}" aria-hidden="true"></span>`;
  }

  function chevron(className) {
    return icon("chevron", className || "fd-inline-chevron");
  }

  function chapterIsCurrent(chapter) {
    return Boolean(chapter && (chapter.current || chapter.state === "当前"));
  }

  function chapterMarkers(chapter) {
    if (!chapter) {
      return [];
    }
    if (Array.isArray(chapter.markers)) {
      return chapter.markers.filter(Boolean);
    }
    const markers = [];
    if (chapter.cached) {
      markers.push("已缓存");
    }
    if (chapter.bookmarked) {
      markers.push("书签");
    }
    return markers;
  }

  function chapterMarkerText(chapter) {
    return chapterMarkers(chapter).join(" · ");
  }

  function chapterRowIcon(chapter) {
    const markers = chapterMarkers(chapter);
    if (markers.includes("书签")) {
      return "bookmark";
    }
    if (markers.includes("已缓存")) {
      return "storage";
    }
    return "directory";
  }

  function cover(data, coverKey) {
    return esc((data.covers || {})[coverKey] || "");
  }

  function shellKit() {
    if (!window.ReaderShellKit) {
      throw new Error("ReaderShellKit is required before frontend-demo-draft/render.js");
    }
    return window.ReaderShellKit;
  }

  function phoneShellClasses(extra) {
    return {
      frameClass: `fd-phone ${extra || ""}`.trim(),
      statusBarClass: "fd-status-bar",
      systemIconsClass: "fd-system-icons",
      signalClass: "fd-signal",
      wifiClass: "fd-wifi",
      batteryClass: "fd-battery",
      topBarClass: "fd-top-bar",
      topActionsClass: "fd-top-actions",
      iconButtonClass: "fd-icon-button",
      iconClass: "fd-icon",
      contentClass: "fd-phone-content",
      navClass: "fd-main-nav",
      navItemClass: "fd-main-nav-item",
      navIconShellClass: "fd-main-nav-icon-shell",
      navIconClass: "fd-nav-icon",
      stateHostClass: "fd-state-host"
    };
  }

  const routes = {
    bookshelf: { title: "书架（Bookshelf）", shell: "MainTabShell" },
    discover: { title: "发现（Discover）", shell: "MainTabShell" },
    rss: { title: "RSS", shell: "MainTabShell" },
    settings: { title: "设置首页（Settings Home）", shell: "MainTabShell" },
    "book-search": { title: "书籍搜索（Book Search）", shell: "LibraryShell" },
    "book-detail": { title: "书籍详情（Book Detail）", shell: "LibraryShell" },
    "book-directory": { title: "书籍目录（Book Directory）", shell: "LibraryShell" },
    "bookshelf-empty": { title: "书架空状态（Bookshelf Empty）", shell: "MainTabShell" },
    "sort-filter": { title: "排序与筛选（Sort and Filter）", shell: "LibraryShell" },
    "group-management": { title: "分组管理（Group Management）", shell: "LibraryShell" },
    "local-import": { title: "本地书导入（Local Import）", shell: "LibraryShell" },
    "immersive-reading": { title: "沉浸阅读（Immersive Reading）", shell: "ReaderShell" },
    reader: { title: "阅读控制层（Reader Control Layer）", shell: "ReaderShell" },
    "toc-bookmarks": { title: "目录与书签（TOC and Bookmarks）", shell: "ReaderShell" },
    "reader-appearance": { title: "阅读外观（Reading Appearance）", shell: "ReaderShell" },
    tts: { title: "朗读（Read Aloud）", shell: "ReaderShell" },
    "reader-settings": { title: "阅读设置（Reading Settings）", shell: "ReaderShell" },
    "auto-page": { title: "自动翻页（Auto Page）", shell: "ReaderShell" },
    "content-search": { title: "内容搜索（Content Search）", shell: "ReaderShell" },
    "content-replacement": { title: "内容替换（Content Replacement）", shell: "ReaderShell" },
    "source-switch": { title: "换源（Source Switching）", shell: "FlowShell" },
    "settings-general": { title: "App 通用设置（General Settings）", shell: "SettingsShell" },
    "bookshelf-search-settings": { title: "书架与搜索设置（Bookshelf and Search Settings）", shell: "SettingsShell" },
    "privacy-permissions": { title: "隐私与权限（Privacy and Permissions）", shell: "SettingsShell" },
    "cache-management": { title: "缓存管理（Cache Management）", shell: "SettingsShell" },
    "about-feedback": { title: "关于与反馈（About and Feedback）", shell: "SettingsShell" },
    "sync-backup": { title: "同步与备份（Sync and Backup）", shell: "SettingsShell" },
    "source-management": { title: "书源管理（Source Management）", shell: "SettingsShell" }
  };

  function bookCard(data, book) {
    const coverSrc = cover(data, book.coverKey);
    return `
      <article class="fd-book-card" data-book-card>
        <button class="fd-book-cover-frame" type="button" data-book-cover data-route="immersive-reading" data-book-title="${esc(book.title)}" data-book-author="${esc(book.author)}" data-book-chapter="${esc(book.chapter)}" data-cover-src="${coverSrc}" aria-label="打开 ${esc(book.title)}">
          <img src="${coverSrc}" alt="${esc(book.title)}封面">
        </button>
        <strong>${esc(book.title)}</strong>
        <span>${esc(book.author)}</span>
      </article>`;
  }

  function bookFocusLayer(data) {
    const first = data.mainTabs.books[0] || {};
    return `
      <section class="fd-book-focus-layer" data-book-focus-layer aria-hidden="true" aria-label="书籍封面操作层">
        <button class="fd-book-focus-backdrop" type="button" data-close-book-focus aria-label="关闭书籍操作层"></button>
        <section class="fd-book-focus-menu" role="dialog" aria-modal="true" aria-label="书籍操作">
          <header>
            <span class="fd-book-focus-cover" data-focus-cover aria-hidden="true" style="--focus-cover:url('${cover(data, first.coverKey)}')"></span>
            <strong data-focus-title>${esc(first.title || "长夜余火")}</strong>
            <small data-focus-meta>${esc(first.author || "爱潜水的乌贼")} · ${esc(first.chapter || "第 32 章 雨夜")}</small>
          </header>
          <div>
            <button type="button" data-book-action="multi-select">${icon("check", "fd-small-icon")}<span>多选</span></button>
            <button type="button" data-book-action="branch" data-route="group-management">${icon("people", "fd-small-icon")}<span>分支</span></button>
            <button type="button" data-route="book-detail">${icon("info", "fd-small-icon")}<span>书籍详情</span></button>
            <button class="is-danger" type="button" data-book-action="delete">${icon("trash", "fd-small-icon")}<span>删除</span></button>
          </div>
        </section>
      </section>`;
  }

  function bookshelfMoreLayer() {
    const items = [
      { icon: "check", title: "批量管理", meta: "选择多本书后移动或删除", action: "multi-select" },
      { icon: "people", title: "分组管理", meta: "编辑书架分组与归属", route: "group-management" },
      { icon: "book-open", title: "本地书导入", meta: "导入本地文件到书架", route: "local-import" },
      { icon: "sort", title: "排序与筛选", meta: "调整当前书架内容顺序", route: "sort-filter" }
    ];
    return `
      <section class="fd-bookshelf-more-layer" data-bookshelf-more-layer aria-hidden="true" aria-label="书架更多操作">
        <button class="fd-bookshelf-more-backdrop" type="button" data-close-bookshelf-more aria-label="关闭书架更多操作"></button>
        <section class="fd-bookshelf-more-menu" role="dialog" aria-modal="true" aria-label="书架更多操作">
          <h2>书架更多操作</h2>
          ${items.map((item) => `
            <button type="button"${item.route ? ` data-route="${esc(item.route)}"` : ` data-book-action="${esc(item.action)}"`}>
              ${icon(item.icon, "fd-small-icon")}
              <span><strong>${esc(item.title)}</strong><small>${esc(item.meta)}</small></span>
            </button>
          `).join("")}
        </section>
      </section>`;
  }

  function mainTabBookshelf(data, appState) {
    const first = data.mainTabs.books[0];
    const bookshelfView = appState?.bookshelfView === "list" ? "list" : "cover";
    return shellKit().renderMainTabShell(Object.assign(phoneShellClasses("fd-main-tab-phone"), {
      data,
      title: "书架",
      activeType: "bookshelf",
      actions: ["search", "more"],
      ariaLabel: "书架",
      contentHtml: `
        <nav class="fd-chip-row" aria-label="书架分组">
          ${["全部", "默认", "本地书", "追更"].map((item, index) => `<button class="${index === 0 ? "is-active" : ""}" type="button">${esc(item)}</button>`).join("")}
        </nav>
        <section class="fd-continue-card">
          <button class="fd-continue-cover-button" type="button" data-book-cover data-route="immersive-reading" data-book-title="${esc(first.title)}" data-book-author="${esc(first.author)}" data-book-chapter="${esc(first.chapter)}" data-cover-src="${cover(data, first.coverKey)}" aria-label="打开 ${esc(first.title)}">
            <img src="${cover(data, first.coverKey)}" alt="${esc(first.title)}封面">
          </button>
          <div>
            <h2>继续阅读</h2>
            <strong>${esc(first.title)}</strong>
            <span class="fd-continue-author">${esc(first.author)}</span>
          </div>
          <button class="fd-continue-action-button" type="button" data-route="immersive-reading">阅读</button>
        </section>
        <section class="fd-section-head">
          <div>
            <h2>我的书架</h2>
          </div>
          <span>
            <button class="${bookshelfView === "cover" ? "is-active" : ""}" type="button" aria-label="封面视图" data-bookshelf-view-button="cover" aria-pressed="${bookshelfView === "cover" ? "true" : "false"}">${icon("grid", "fd-small-icon")}</button>
            <button class="${bookshelfView === "list" ? "is-active" : ""}" type="button" aria-label="列表视图" data-bookshelf-view-button="list" aria-pressed="${bookshelfView === "list" ? "true" : "false"}">${icon("list", "fd-small-icon")}</button>
            <button type="button" aria-label="书架显示设置" data-route="bookshelf-search-settings" data-settings-scope="bookshelf-display">${icon("gear", "fd-small-icon")}</button>
          </span>
        </section>
        <section class="fd-book-grid ${bookshelfView === "list" ? "is-list-view" : "is-cover-view"}" data-book-grid data-bookshelf-view="${bookshelfView}" aria-label="${bookshelfView === "list" ? "书籍列表" : "书籍封面网格"}">
          ${data.mainTabs.books.map((book) => bookCard(data, book)).join("")}
        </section>`,
      stateHostHtml: `
        <p class="fd-nav-feedback">当前 Tab：书架</p>
        ${bookFocusLayer(data)}
        ${bookshelfMoreLayer()}`
    }));
  }

  function mainTabFeedbackHtml(appState) {
    const message = appState?.mainTabFeedback || "";
    return message ? `<p class="fd-nav-feedback" data-main-tab-feedback>${esc(message)}</p>` : "";
  }

  function mainTabDiscover(data, appState) {
    return shellKit().renderMainTabShell(Object.assign(phoneShellClasses("fd-main-tab-phone"), {
      data,
      title: "发现",
      activeType: "discover",
      actions: ["search", "more"],
      ariaLabel: "发现",
      contentHtml: `
        <button class="fd-search-entry" type="button" data-route="book-search">${icon("search", "fd-small-icon")}<span>搜索书名、作者或书源</span></button>
        <section class="fd-source-summary">
          <div>${icon("source-stack", "fd-medium-icon")}<strong>书源广场</strong><span>128 个可用 · 12 个更新</span></div>
          <button type="button" data-route="source-management">管理</button>
        </section>
        <section class="fd-ranking-list">
          <h2>今日推荐</h2>
          ${data.mainTabs.discovery.map((item) => `
            <article class="fd-ranking-row" role="button" tabindex="0" data-route="book-detail">
              <em>${esc(item.rank)}</em>
              <img src="${cover(data, item.coverKey)}" alt="${esc(item.title)}封面">
              <span><strong>${esc(item.title)}</strong><small>${esc(item.meta)}</small></span>
              ${icon("chevron", "fd-small-icon")}
            </article>
          `).join("")}
        </section>`,
      stateHostHtml: mainTabFeedbackHtml(appState)
    }));
  }

  function mainTabRss(data, appState) {
    const entries = data.mainTabs.rss || [
      { title: "长夜余火更新到第 33 章", meta: "优书网 · 2 分钟前", route: "immersive-reading" },
      { title: "书源维护公告", meta: "RSS 订阅 · 未读", route: "source-management" },
      { title: "本地导入文件已完成解析", meta: "系统通知 · 12 分钟前", route: "book-detail" }
    ];

    return shellKit().renderMainTabShell(Object.assign(phoneShellClasses("fd-main-tab-phone"), {
      data,
      title: "RSS",
      activeType: "rss",
      actions: ["search", "more"],
      ariaLabel: "RSS",
      contentHtml: `
        <button class="fd-search-entry" type="button" data-route="book-search">
          ${icon("search", "fd-small-icon")}<span>搜索订阅、书名或来源</span>
        </button>
        <section class="fd-ranking-list">
          <h2>订阅更新</h2>
          ${entries.map((item, index) => `
            <article class="fd-feed-row" role="button" tabindex="0" data-route="${esc(item.route)}">
              <em>${index + 1}</em>
              <span><strong>${esc(item.title)}</strong><small>${esc(item.meta)}</small></span>
              ${icon("chevron", "fd-small-icon")}
            </article>
          `).join("")}
        </section>`,
      stateHostHtml: mainTabFeedbackHtml(appState)
    }));
  }

  function mainTabSettings(data, appState) {
    const rows = [
      { icon: "settings", title: "App 通用设置", meta: "主题、网络、基础行为", route: "settings-general" },
      { icon: "bookshelf", title: "书架与搜索设置", meta: "布局、列数、搜索历史", route: "bookshelf-search-settings" },
      { icon: "shield", title: "隐私与权限", meta: "本地书、通知、剪贴板", route: "privacy-permissions" },
      { icon: "storage", title: "缓存管理", meta: "封面、章节、日志缓存", route: "cache-management" },
      { icon: "source", title: "书源管理", meta: "启用、检测、日志", route: "source-management" },
      { icon: "sync", title: "同步与备份", meta: "WebDAV、备份、恢复", route: "sync-backup" },
      { icon: "info", title: "关于与反馈", meta: "版本、协议、帮助", route: "about-feedback" }
    ];

    return shellKit().renderMainTabShell(Object.assign(phoneShellClasses("fd-main-tab-phone"), {
      data,
      title: "设置",
      activeType: "settings",
      actions: ["search", "more"],
      ariaLabel: "设置首页",
      contentHtml: `
        <section class="fd-setting-section" data-slot="settingSection">
          <h2>设置</h2>
          ${rows.map((row) => `
            <article class="fd-setting-row" role="button" tabindex="0" data-route="${esc(row.route)}">
              <span>${icon(row.icon, "fd-small-icon")}</span>
              <strong>${esc(row.title)}<small>${esc(row.meta)}</small></strong>
              ${icon("chevron", "fd-small-icon")}
            </article>
          `).join("")}
        </section>`,
      stateHostHtml: mainTabFeedbackHtml(appState)
    }));
  }

  function bookSearchScreen(data, appState) {
    const phase = appState?.bookSearchPhase === "after" ? "after" : "before";
    const searchResults = [
      {
        title: "三体",
        author: "刘慈欣",
        source: "优书网",
        latest: "最新：第 35 章 尾声",
        coverKey: "threeBody",
        inShelf: true
      },
      {
        title: "三体：黑暗森林",
        author: "刘慈欣",
        source: "书仓",
        latest: "匹配：黑暗森林 · 已完结",
        coverKey: "threeBody",
        inShelf: false
      },
      {
        title: "三体：死神永生",
        author: "刘慈欣",
        source: "本地",
        latest: "最新：广播纪元",
        coverKey: "threeBody",
        inShelf: false
      },
      {
        title: "三体全集",
        author: "刘慈欣",
        source: "快读",
        latest: "匹配：合集版本 · 需确认目录",
        coverKey: "threeBody",
        inShelf: true
      }
    ];
    const searchBeforeHtml = `
      <section class="fd-search-state fd-search-state-before" data-search-state="before">
        <header class="fd-search-section-head">
          <h2>搜索历史</h2>
          <button type="button">清空</button>
        </header>
        <div class="fd-search-history-list" aria-label="搜索历史">
          ${[
            ["长夜余火", "书名 · 网络"],
            ["三体", "书名 · 全部"],
            ["爱潜水的乌贼", "作者 · 网络"],
            ["本地导入", "关键词 · 本地"]
          ].map(([keyword, meta]) => `
            <button class="fd-search-history-row" type="button" data-search-submit>
              ${icon("clock", "fd-small-icon")}
              <span><strong>${esc(keyword)}</strong><small>${esc(meta)}</small></span>
              <em>填入</em>
            </button>`).join("")}
        </div>
      </section>`;
    const searchAfterHtml = `
      <section class="fd-search-results" data-search-state="after">
        <header class="fd-search-section-head">
          <h2>搜索结果</h2>
        </header>
        <p>找到 24 个结果 · 已标注书架状态</p>
        <div class="fd-search-result-list" aria-label="搜索结果列表">
          ${searchResults.map((book) => `
            <article class="fd-search-result-row" role="button" tabindex="0" data-route="book-detail">
              <img src="${cover(data, book.coverKey)}" alt="${esc(book.title)}封面">
              <span class="fd-search-result-main">
                <strong>${esc(book.title)}</strong>
                <small><b>${esc(book.author)}</b><em>${esc(book.source)}</em></small>
                <small>${esc(book.latest)}</small>
              </span>
              <span class="fd-search-result-state ${book.inShelf ? "is-in-shelf" : ""}">${book.inShelf ? "已在书架" : "未加入"}</span>
              <button type="button"${book.inShelf ? ` data-route="immersive-reading"` : ` data-add-search-shelf`}>${book.inShelf ? "阅读" : "加入书架"}</button>
            </article>
          `).join("")}
        </div>
      </section>`;
    return shellKit().renderLibraryShell(Object.assign(phoneShellClasses("fd-library-phone"), {
      data,
      title: "书籍搜索",
      ariaLabel: "书籍搜索",
      topBarClass: "fd-back-bar",
      bottomActionHostClass: "fd-bottom-action-host",
      contentHtml: `
        <button class="fd-search-entry fd-keyboard-target" type="button" data-open-keyboard>
          ${icon("search", "fd-small-icon")}<span>${phase === "after" ? "三体" : "搜索书名、作者、关键词"}</span>
        </button>
        <nav class="fd-chip-row ${phase === "before" ? "fd-search-scope-hidden" : ""}" aria-label="搜索范围">
          ${["全部", "书名", "作者", "书源"].map((item, index) => `<button class="${index === 0 ? "is-active" : ""}" type="button">${esc(item)}</button>`).join("")}
        </nav>
        ${phase === "after" ? searchAfterHtml : searchBeforeHtml}
        ${keyboardLayer()}`,
      bottomActionHtml: `
        <div class="fd-fixed-action-row">
          ${phase === "after"
            ? `<button type="button" data-search-reset>重新搜索</button><button type="button" data-route="book-detail">查看详情</button>`
            : `<button type="button" data-search-submit data-primary-search-submit>搜索</button><button type="button">清除历史</button>`}
        </div>`
    }));
  }

  function libraryScreen(data) {
    const book = data.library.book;
    const sourceName = String(book.source || "").split("·")[0].trim() || "当前书源";
    const intro = book.intro || "旧世界的余烬尚未冷却，新的秩序已经在废墟之上生长。主角沿着被遗忘的线索追寻真相，也在一次次选择里确认自己想守住的东西。";
    return shellKit().renderLibraryShell(Object.assign(phoneShellClasses("fd-library-phone"), {
      data,
      title: "书籍详情",
      ariaLabel: "书籍详情",
      topBarClass: "fd-back-bar",
      bottomActionHostClass: "fd-bottom-action-host",
      sheetHostClass: "fd-sheet-host",
      dialogHostClass: "fd-dialog-host",
      contentHtml: `
        <section class="fd-book-hero fd-book-detail-hero">
          <img src="${cover(data, book.coverKey)}" alt="${esc(book.title)}封面">
          <div class="fd-book-identity">
            <h2>${esc(book.title)}</h2>
            <p class="fd-book-author">${esc(book.author)}</p>
            <dl class="fd-book-facts">
              <div>
                <dt>最新</dt>
                <dd>${esc(book.latest)}</dd>
              </div>
            </dl>
            <div class="fd-book-inline-source-row">
              <span>书源：${esc(sourceName)}</span>
              <button class="fd-book-inline-source-button" type="button" data-open-sheet>更换书源</button>
            </div>
          </div>
        </section>
        <section class="fd-book-summary-card">
          <h2>简介</h2>
          <p>${esc(intro)}</p>
        </section>
        <section class="fd-chapter-list fd-book-chapter-preview">
          <header>
            <h2>章节信息</h2>
            <button class="fd-inline-route" type="button" data-route="book-directory">${icon("directory", "fd-small-icon")}完整目录</button>
          </header>
          ${data.library.chapters.map((chapter) => `
            <article class="${chapterIsCurrent(chapter) ? "is-current" : ""}" role="button" tabindex="0" data-route="immersive-reading">
              <span>${esc(chapter.title)}</span>
              <span class="fd-chapter-marker-slots" aria-label="章节标识">
                <em class="is-download-slot ${chapterMarkers(chapter).includes("已缓存") ? "is-active" : ""}" title="${chapterMarkers(chapter).includes("已缓存") ? "已下载" : "未下载"}">${icon(chapterMarkers(chapter).includes("已缓存") ? "check" : "download", "fd-small-icon")}</em>
                <em class="is-bookmark-slot ${chapterMarkers(chapter).includes("书签") ? "is-active" : ""}" title="书签">${icon("bookmark", "fd-small-icon")}</em>
              </span>
            </article>
          `).join("")}
        </section>`,
      bottomActionHtml: `
        <div class="fd-fixed-action-row">
          <button type="button" data-route="immersive-reading">继续阅读</button>
          <button class="is-danger" type="button" data-open-dialog>移除书架</button>
        </div>`,
      sheetHtml: `
        <section class="fd-demo-sheet" aria-hidden="true" data-demo-sheet>
          <div class="fd-sheet-grabber"></div>
          <h2>更换书源</h2>
          <button type="button">优书网</button>
          <button type="button">书仓搜索</button>
          <button type="button">本地缓存</button>
          <button type="button" data-close-sheet>关闭</button>
        </section>`,
      dialogHtml: `
        <section class="fd-demo-dialog" aria-hidden="true" data-demo-dialog>
          <h2>确认删除？</h2>
          <p>只从书架移除，不删除本地文件和阅读记录。</p>
          <div>
            <button type="button" data-close-dialog>取消</button>
            <button type="button" data-close-dialog>删除</button>
          </div>
        </section>`
    }));
  }

  function bookshelfEmptyScreen(data) {
    return shellKit().renderMainTabShell(Object.assign(phoneShellClasses("fd-main-tab-phone"), {
      data,
      title: "书架",
      activeType: "bookshelf",
      actions: ["search", "more"],
      ariaLabel: "书架空状态",
      contentHtml: `
        <section class="fd-empty-state" data-slot="stateHost">
          ${icon("bookshelf", "fd-empty-icon")}
          <h2>书架还是空的</h2>
          <p>空状态只替换内容区，不替换 MainTabShell、顶部栏或底部四栏导航。</p>
          <div>
            <button type="button" data-route="book-search">搜索书籍</button>
            <button type="button" data-route="local-import">导入本地书</button>
            <button type="button" data-route="discover">去发现</button>
          </div>
        </section>`
    }));
  }

  function bookDirectoryScreen(data) {
    return shellKit().renderLibraryShell(Object.assign(phoneShellClasses("fd-library-phone"), {
      data,
      title: "书籍目录",
      ariaLabel: "书籍目录",
      topBarClass: "fd-back-bar",
      contentHtml: `
        <section class="fd-chapter-list">
          <h2>长夜余火 · 章节</h2>
          ${data.library.chapters.concat([
            { title: "第 34 章 旧地图", markers: ["已缓存"] },
            { title: "第 35 章 夜行", markers: [] },
            { title: "第 36 章 灯塔之后", markers: ["书签"] }
          ]).map((chapter) => `
            <article class="${chapterIsCurrent(chapter) ? "is-current" : ""}" role="button" tabindex="0" data-route="immersive-reading">
              ${icon(chapterRowIcon(chapter), "fd-small-icon")}
              <span>${esc(chapter.title)}</span>
              ${chapterMarkerText(chapter) ? `<em>${esc(chapterMarkerText(chapter))}</em>` : ""}
            </article>
          `).join("")}
        </section>`
    }));
  }

  function sortFilterScreen(data) {
    return shellKit().renderLibraryShell(Object.assign(phoneShellClasses("fd-library-phone"), {
      data,
      title: "排序与筛选",
      ariaLabel: "排序与筛选",
      topBarClass: "fd-back-bar",
      bottomActionHostClass: "fd-bottom-action-host",
      contentHtml: `
        <section class="fd-filter-panel">
          <h2>排序</h2>
          <nav class="fd-chip-row" aria-label="排序方式">
            ${["最近更新", "阅读进度", "书名", "作者"].map((item, index) => `<button class="${index === 0 ? "is-active" : ""}" type="button">${esc(item)}</button>`).join("")}
          </nav>
        </section>
        <section class="fd-filter-panel">
          <h2>筛选</h2>
          <nav class="fd-chip-row fd-chip-wrap" aria-label="筛选范围">
            ${["全部", "追更", "本地书", "未读", "已完结", "更新失败"].map((item, index) => `<button class="${index === 0 ? "is-active" : ""}" type="button">${esc(item)}</button>`).join("")}
          </nav>
          <p>应用后回写书架的分组、排序和筛选上下文，列表末项仍需滚到主导航上方。</p>
        </section>`,
      bottomActionHtml: `
        <div class="fd-fixed-action-row">
          <button type="button" data-route-back>应用</button>
          <button type="button" data-route-back>重置</button>
        </div>`
    }));
  }

  function groupManagementScreen(data) {
    const groups = [
      { name: "默认分组", meta: "8 本 · 当前分组" },
      { name: "本地书", meta: "2 本 · 可重命名" },
      { name: "追更", meta: "5 本 · 更新优先" }
    ];
    return shellKit().renderLibraryShell(Object.assign(phoneShellClasses("fd-library-phone"), {
      data,
      title: "分组管理",
      ariaLabel: "分组管理",
      topBarClass: "fd-back-bar",
      bottomActionHostClass: "fd-bottom-action-host",
      contentHtml: `
        <div class="fd-action-row">
          <button type="button">新建分组</button>
        </div>
        <section class="fd-management-list">
          <h2>书架分组</h2>
          ${groups.map((group) => `
            <article>
              ${icon("sort", "fd-small-icon")}
              <span><strong>${esc(group.name)}</strong><small>${esc(group.meta)}</small></span>
              <button type="button">重命名</button>
            </article>
          `).join("")}
        </section>`,
      bottomActionHtml: `
        <div class="fd-fixed-action-row">
          <button type="button" data-route-back>保存排序</button>
          <button type="button" data-route-back>完成</button>
        </div>`
    }));
  }

  function localImportScreen(data) {
    const imports = [
      { title: "雨夜.epub", meta: "已识别 · 可加入默认分组", state: "完成" },
      { title: "旧书扫描.txt", meta: "编码检测中", state: "72%" },
      { title: "缺失章节.mobi", meta: "格式不支持，可重试", state: "失败" }
    ];
    return shellKit().renderLibraryShell(Object.assign(phoneShellClasses("fd-library-phone"), {
      data,
      title: "本地书导入",
      ariaLabel: "本地书导入",
      topBarClass: "fd-back-bar",
      bottomActionHostClass: "fd-bottom-action-host",
      contentHtml: `
        <section class="fd-import-card">
          ${icon("download", "fd-medium-icon")}
          <span><strong>选择本地文件</strong><small>系统文件选择器属于外部边界，返回后恢复本页进度。</small></span>
          <button type="button">选择</button>
        </section>
        <section class="fd-management-list">
          <h2>导入结果</h2>
          ${imports.map((item) => `
            <article>
              ${icon(item.state === "失败" ? "warning" : "book-open", "fd-small-icon")}
              <span><strong>${esc(item.title)}</strong><small>${esc(item.meta)}</small></span>
              <em>${esc(item.state)}</em>
            </article>
          `).join("")}
        </section>`,
      bottomActionHtml: `
        <div class="fd-fixed-action-row">
          <button type="button" data-route-back>完成</button>
          <button type="button">重试失败项</button>
        </div>`
    }));
  }

  function keyboardLayer() {
    return `
      <section class="fd-demo-keyboard" aria-hidden="true" data-keyboard-host>
        <div class="fd-keyboard-panel">
          <label>
            <span>搜索书籍</span>
            <input type="text" value="三体" data-keyboard-input aria-label="搜索书籍">
          </label>
          <button type="button" data-close-keyboard>完成</button>
          <div class="fd-keyboard-keys" aria-hidden="true">
          ${["Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "A", "S", "D", "F", "G", "H", "J", "K", "L"].map((key) => `<i>${key}</i>`).join("")}
          </div>
        </div>
      </section>`;
  }

  function pct(value) {
    const text = String(value == null ? "0" : value);
    const numeric = Number(text.replace("%", ""));
    return `${Math.max(0, Math.min(100, Number.isFinite(numeric) ? numeric : 0))}%`;
  }

  function numericPercent(value, fallback) {
    const numeric = Number(String(value == null ? "" : value).replace("%", ""));
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    return Math.max(0, Math.min(100, numeric));
  }

  const readerModuleRoutes = {
    directory: "toc-bookmarks",
    tts: "tts",
    appearance: "reader-appearance",
    settings: "reader-settings"
  };

  const readerStateByRoute = {
    "immersive-reading": { mode: "immersive" },
    reader: { mode: "control" },
    "toc-bookmarks": { mode: "module", module: "directory" },
    tts: { mode: "module", module: "tts" },
    "reader-appearance": { mode: "module", module: "appearance" },
    "reader-settings": { mode: "module", module: "settings" },
    "content-search": { mode: "quick", quick: "search" },
    "auto-page": { mode: "quick", quick: "auto-page" },
    "content-replacement": { mode: "quick", quick: "replace" }
  };

  function readerRouteState(route) {
    return Object.assign({ route }, readerStateByRoute[route] || readerStateByRoute.reader);
  }

  function isReaderStateRoute(route) {
    return Boolean(readerStateByRoute[route]);
  }

  function initialRouteStackFor(route) {
    if (["bookshelf", "discover", "rss", "settings"].includes(route)) {
      return [route];
    }
    if (routes[route]?.shell === "SettingsShell") {
      return ["settings", route];
    }
    return ["bookshelf", route];
  }

  function shouldLoadReaderTransition(previousRoute, targetRoute) {
    if (!isReaderStateRoute(previousRoute) || !isReaderStateRoute(targetRoute)) {
      return false;
    }
    if (previousRoute === targetRoute) {
      return false;
    }
    return targetRoute !== "reader" && targetRoute !== "immersive-reading";
  }

  function readerChapterTitle(data) {
    return data.reader.chapterTitle || "雨夜";
  }

  function readerChapterMeta(data) {
    return data.reader.chapterMeta || "第 32 章";
  }

  function readerChapters(data) {
    const chapters = data.library && Array.isArray(data.library.chapters) ? data.library.chapters : [];
    return chapters.length > 0
      ? chapters
      : [
          { title: "第 31 章 归途", markers: ["已缓存"] },
          { title: "第 32 章 雨夜", current: true, markers: ["书签"] },
          { title: "第 33 章 灯塔", markers: [] }
        ];
  }

  function initialReaderChapterIndex(data) {
    const chapters = readerChapters(data);
    const current = chapters.findIndex((chapter) => chapterIsCurrent(chapter));
    return current >= 0 ? current : 0;
  }

  function currentReaderChapter(data, appState) {
    const chapters = readerChapters(data);
    const maxIndex = Math.max(0, chapters.length - 1);
    const rawIndex = Number.isFinite(Number(appState?.readerChapterIndex))
      ? Number(appState.readerChapterIndex)
      : initialReaderChapterIndex(data);
    const index = Math.max(0, Math.min(maxIndex, rawIndex));
    return {
      index,
      count: chapters.length,
      chapter: chapters[index] || chapters[0] || { title: readerChapterMeta(data), current: true, markers: [] }
    };
  }

  function readerChapterProgressValue(data, appState) {
    const config = readerChapterProgressConfig(data);
    const raw = Number.isFinite(Number(appState?.readerChapterProgress))
      ? Number(appState.readerChapterProgress)
      : config.defaultValue;
    return Math.max(config.min, Math.min(config.max, Number.isFinite(raw) ? raw : config.defaultValue));
  }

  function readerChapterProgressConfig(data) {
    const progress = data.reader?.chapterProgress || {};
    const min = Number.isFinite(Number(progress.min)) ? Number(progress.min) : 0;
    const max = Number.isFinite(Number(progress.max)) ? Number(progress.max) : 100;
    const normalizedMin = Math.max(0, Math.min(100, min));
    const normalizedMax = Math.max(normalizedMin, Math.min(100, max));
    const rawValue = Number.parseFloat(String(progress.progress || data.reader?.bottomReadout?.progress || "38%").replace("%", ""));
    return {
      min: normalizedMin,
      max: normalizedMax,
      step: Number.isFinite(Number(progress.step)) ? Number(progress.step) : 1,
      defaultValue: Math.max(normalizedMin, Math.min(normalizedMax, Number.isFinite(rawValue) ? rawValue : normalizedMin))
    };
  }

  function readerChapterNumber(chapterTitle, fallback) {
    const match = String(chapterTitle || "").match(/第\s*(\d+)\s*章/);
    const parsed = match ? Number.parseInt(match[1], 10) : NaN;
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function readerTotalChapterCount(data, fallback) {
    const match = String(data.reader?.bottomReadout?.chapter || "").match(/\/\s*(\d+)\s*章/);
    const parsed = match ? Number.parseInt(match[1], 10) : NaN;
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function readerBookProgressLabel(data, appState) {
    const value = readerChapterProgressValue(data, appState);
    return `书籍进度 ${Number.isInteger(value) ? value.toFixed(0) : value.toFixed(1)}%`;
  }

  function normalizeReaderTypography(data) {
    const typography = (data.reader && data.reader.typography) || {};
    return {
      fontSize: Number.isFinite(Number(typography.fontSize)) ? Number(typography.fontSize) : 18,
      lineHeight: Number.isFinite(Number(typography.lineHeight)) ? Number(typography.lineHeight) : 1.96,
      paragraphGap: Number.isFinite(Number(typography.paragraphGap)) ? Number(typography.paragraphGap) : 16,
      letterSpacing: Number.isFinite(Number(typography.letterSpacing)) ? Number(typography.letterSpacing) : 0,
      fontFamily: typography.fontFamily || readerDefaultFontValue(data)
    };
  }

  function readerTypographyConfig(data) {
    const config = data.reader?.typographyConfig || {};
    const normalizeConfig = (item) => ({
      min: Number.isFinite(Number(item?.min)) ? Number(item.min) : 0,
      max: Number.isFinite(Number(item?.max)) ? Number(item.max) : 100,
      step: Number.isFinite(Number(item?.step)) ? Number(item.step) : 1,
      precision: Number.isFinite(Number(item?.precision)) ? Number(item.precision) : 0
    });
    return {
      fontSize: normalizeConfig(config.fontSize),
      lineHeight: normalizeConfig(config.lineHeight),
      paragraphGap: normalizeConfig(config.paragraphGap),
      letterSpacing: normalizeConfig(config.letterSpacing)
    };
  }

  function readerThemeOptions(data) {
    const options = data.reader?.themeOptions;
    return Array.isArray(options) && options.length > 0
      ? options
      : [{ value: "paper", label: "纸色", swatch: "#fff7ec", paperStart: "#fff9f0", paperEnd: "#f7ead9", ink: "#2b241d" }];
  }

  function readerFontOptions(data) {
    const options = data.reader?.fontOptions;
    return Array.isArray(options) && options.length > 0
      ? options
      : [{ label: "宋体", value: "serif", fontStack: "var(--fd-serif)" }];
  }

  function readerDefaultThemeValue(data) {
    const options = readerThemeOptions(data);
    return data.reader?.themeDefault || options[0].value;
  }

  function readerDefaultFontValue(data) {
    const options = readerFontOptions(data);
    return data.reader?.typography?.fontFamily || options[0].value;
  }

  function readerFontFamilyValue(data, fontFamily) {
    const options = readerFontOptions(data);
    const selected = options.find((item) => item.value === fontFamily) || options[0];
    return selected.fontStack || "var(--fd-serif)";
  }

  function readerTypographyStyle(data, typography) {
    const safe = typography || normalizeReaderTypography(data);
    return [
      `--reader-font-size:${esc(safe.fontSize)}px`,
      `--reader-line-height:${esc(safe.lineHeight)}`,
      `--reader-paragraph-gap:${esc(safe.paragraphGap)}px`,
      `--reader-letter-spacing:${esc(safe.letterSpacing)}px`,
      `--reader-font-family:${readerFontFamilyValue(data, safe.fontFamily)}`
    ].join(";");
  }

  function currentReaderTheme(data, appState) {
    const options = readerThemeOptions(data);
    const value = appState?.readerTheme || readerDefaultThemeValue(data);
    return options.find((item) => item.value === value) || options[0];
  }

  function readerThemeStyle(data, appState) {
    const theme = currentReaderTheme(data, appState);
    return [
      `--reader-paper-start:${esc(theme.paperStart)}`,
      `--reader-paper-end:${esc(theme.paperEnd)}`,
      `--reader-ink:${esc(theme.ink)}`
    ].join(";");
  }

  function readerBrightnessConfig(data) {
    const brightness = data.reader?.brightness || {};
    const min = Number.isFinite(Number(brightness.min)) ? Number(brightness.min) : 0;
    const max = Number.isFinite(Number(brightness.max)) ? Number(brightness.max) : 100;
    const normalizedMin = Math.max(0, Math.min(100, min));
    const normalizedMax = Math.max(normalizedMin, Math.min(100, max));
    return {
      min: normalizedMin,
      max: normalizedMax,
      step: Number.isFinite(Number(brightness.step)) ? Number(brightness.step) : 1,
      defaultValue: Math.max(normalizedMin, Math.min(normalizedMax, numericPercent(brightness.value, normalizedMax)))
    };
  }

  function readerBrightnessValue(data, appState) {
    const config = readerBrightnessConfig(data);
    const current = Number(appState?.readerBrightness);
    return Math.round(Math.max(config.min, Math.min(config.max, Number.isFinite(current) ? current : config.defaultValue)));
  }

  function readerBrightnessStyle(data, appState) {
    const config = readerBrightnessConfig(data);
    const value = readerBrightnessValue(data, appState);
    const dim = Math.max(0, Math.min(0.32, (config.max - value) / 280));
    return `--reader-brightness:${esc(value)}%;--reader-brightness-dim:${esc(dim.toFixed(3))}`;
  }

  function readerTtsConfig(data) {
    const config = data.reader?.tts || {};
    const defaults = config.defaults || {};
    return {
      sentenceMin: Number.isFinite(Number(config.sentenceMin)) ? Number(config.sentenceMin) : Number(defaults.sentenceIndex) || 0,
      sentenceMax: Number.isFinite(Number(config.sentenceMax)) ? Number(config.sentenceMax) : Number(defaults.sentenceIndex) || 0,
      defaults,
      options: config.options || {}
    };
  }

  function readerControlSettingsConfig(data) {
    const config = data.reader?.controlSettings || {};
    return {
      defaults: config.defaults || {},
      options: config.options || {}
    };
  }

  function typographyNumber(value, fractionDigits) {
    return Number(value).toFixed(fractionDigits).replace(/\.?0+$/, "");
  }

  function typographyPanelRows(data, typography) {
    return `
      <div class="fd-reader-step-row" data-typography-row="font-size">
        <strong>字号</strong>
        <span>
          <button type="button" data-reader-typography-action="font-size-decrease">-</button>
          <em data-reader-typography-value="font-size">${esc(typographyNumber(typography.fontSize, 0))}</em>
          <button type="button" data-reader-typography-action="font-size-increase">+</button>
        </span>
      </div>
      <div class="fd-reader-step-row" data-typography-row="line-height">
        <strong>行距</strong>
        <span>
          <button type="button" data-reader-typography-action="line-height-decrease">-</button>
          <em data-reader-typography-value="line-height">${esc(typographyNumber(typography.lineHeight, 2))}</em>
          <button type="button" data-reader-typography-action="line-height-increase">+</button>
        </span>
      </div>
      <div class="fd-reader-step-row" data-typography-row="paragraph-gap">
        <strong>段距</strong>
        <span>
          <button type="button" data-reader-typography-action="paragraph-gap-decrease">-</button>
          <em data-reader-typography-value="paragraph-gap">${esc(typographyNumber(typography.paragraphGap, 0))}</em>
          <button type="button" data-reader-typography-action="paragraph-gap-increase">+</button>
        </span>
      </div>
      <div class="fd-reader-step-row" data-typography-row="letter-spacing">
        <strong>字距</strong>
        <span>
          <button type="button" data-reader-typography-action="letter-spacing-decrease">-</button>
          <em data-reader-typography-value="letter-spacing">${esc(typographyNumber(typography.letterSpacing, 1))}</em>
          <button type="button" data-reader-typography-action="letter-spacing-increase">+</button>
        </span>
      </div>
      <div class="fd-reader-font-row" aria-label="字体">
        ${readerFontOptions(data).map((item) => `
          <button class="${typography.fontFamily === item.value ? "is-active" : ""}" type="button" data-reader-typography-set="fontFamily" data-reader-typography-value="${esc(item.value)}">${esc(item.label)}</button>
        `).join("")}
      </div>`;
  }

  function readerTextBlocks(data) {
    const directText = data.reader && Array.isArray(data.reader.readingText) ? data.reader.readingText : [];
    if (directText.length > 0) {
      return directText.map((item) => String(item || "")).filter(Boolean);
    }
    const legacyPages = data.reader && Array.isArray(data.reader.readingPages) ? data.reader.readingPages : [];
    return legacyPages
      .flatMap((page) => Array.isArray(page.paragraphs) ? page.paragraphs : [])
      .map((item) => String(item || ""))
      .filter(Boolean);
  }

  function fallbackReaderPages(data) {
    return [{
      progress: (data.reader && data.reader.bottomReadout && data.reader.bottomReadout.progress) || "38%",
      paragraphs: readerTextBlocks(data)
    }];
  }

  function readerPages(data, appState) {
    const runtimePages = appState && Array.isArray(appState.readerPages) ? appState.readerPages : [];
    return runtimePages.length > 0 ? runtimePages : fallbackReaderPages(data);
  }

  function currentReaderPage(data, appState) {
    const pages = readerPages(data, appState);
    const maxIndex = Math.max(0, pages.length - 1);
    const rawIndex = Number(appState?.readerPageIndex || 0);
    const index = Math.max(0, Math.min(maxIndex, Number.isFinite(rawIndex) ? rawIndex : 0));
    return {
      index,
      count: pages.length,
      page: pages[index] || pages[0] || { progress: "38%", paragraphs: [] }
    };
  }

  function readerPageReadout(data, appState) {
    const pageState = currentReaderPage(data, appState);
    const chapterProgress = `${readerChapterProgressValue(data, appState)}%`;
    const progress = appState && Number.isFinite(Number(appState.readerChapterProgress))
      ? chapterProgress
      : pageState.page.progress || chapterProgress;
    return {
      pageNumber: pageState.index + 1,
      pageCount: pageState.count,
      progress,
      pageLabel: `第 ${pageState.index + 1} / ${pageState.count} 页`,
      progressLabel: `${progress} · 第 ${pageState.index + 1} / ${pageState.count} 页`
    };
  }

  function sharedReaderSurface(data, dismissRoute, appState, options) {
    const typography = appState?.readerTypography || normalizeReaderTypography(data);
    const pageState = currentReaderPage(data, appState);
    const pageReadout = readerPageReadout(data, appState);
    const disableTurnAnimation = Boolean(options && options.disableTurnAnimation);
    const turnDirection = !disableTurnAnimation && appState?.readerTurnDirection ? ` fd-reader-page-turn-${esc(appState.readerTurnDirection)}` : "";
    const paragraphs = pageState.page.paragraphs.length > 0 ? pageState.page.paragraphs : readerTextBlocks(data);
    const chapterState = currentReaderChapter(data, appState);
    const chapterTitle = chapterState.chapter.title || `${readerChapterMeta(data)} ${readerChapterTitle(data)}`;
    const chapterTitleHtml = pageState.index === 0 ? `<h1>${esc(chapterTitle.replace(/^第\s*\d+\s*章\s*/, ""))}</h1>` : "";
    const paginationMode = appState?.readerPages?.length ? "runtime" : "fallback";
    return `
      <div class="fd-ir-background-layer" data-dev-region="ReadingBackground" aria-hidden="true" style="${readerThemeStyle(data, appState)}"></div>
      <article class="fd-ir-reading-layer${turnDirection}" aria-label="正文排版层" data-dev-region="ReadingTextLayer" data-reader-pagination="${esc(paginationMode)}" data-reader-surface-signature="${esc(chapterTitle)}" data-reader-page-index="${esc(pageState.index)}" data-reader-page-count="${esc(pageState.count)}" style="${readerTypographyStyle(data, typography)};${readerThemeStyle(data, appState)}">
        ${chapterTitleHtml}
        ${paragraphs.map((line) => `<p>${esc(line)}</p>`).join("")}
      </article>
      <div class="fd-reader-brightness-dim" data-reader-brightness-dim aria-hidden="true" style="${readerBrightnessStyle(data, appState)}"></div>
      <footer class="fd-reader-footer fd-reader-page-footer" data-dev-region="FooterProgressInfo" data-reader-page-readout>${esc(pageReadout.progressLabel)}</footer>
      ${dismissRoute ? `<button class="fd-reader-dismiss-zone" type="button" data-dev-region="ControlDismissZone" data-reader-dismiss="${esc(dismissRoute)}" aria-label="隐藏阅读控制层"></button>` : ""}`;
  }

  function readerInfoOverlay(data, appState) {
    const readout = data.reader.bottomReadout || {};
    const pageReadout = readerPageReadout(data, appState);
    const chapterState = currentReaderChapter(data, appState);
    return `
      <section class="fd-ir-info-layer" data-dev-region="ImmersiveInfoLayer" aria-label="阅读信息层">
        <span>${esc(data.reader.title)} · ${esc(chapterState.chapter.title || readerChapterMeta(data))}</span>
        <span>${esc(data.reader.status.time)}</span>
        <span>${esc(pageReadout.progress || readout.progress || "38%")}</span>
        <span>${esc(pageReadout.pageLabel)}</span>
      </section>`;
  }

  function readerTapZones(data, appState) {
    const pageState = currentReaderPage(data, appState);
    return `
      <section class="fd-ir-tap-zone-layer" data-dev-region="ImmersiveTapZones" aria-label="透明点击热区层">
        <button class="fd-immersive-hotzone fd-hotzone-prev" type="button" aria-label="上一页" data-dev-region="PrevPageHotzone" data-reader-page-action="prev" aria-disabled="${pageState.index === 0 ? "true" : "false"}"></button>
        <button class="fd-immersive-hotzone fd-hotzone-center" type="button" aria-label="打开阅读控制层" data-dev-region="ControlLayerHotzone" data-route="reader"></button>
        <button class="fd-immersive-hotzone fd-hotzone-next" type="button" aria-label="下一页" data-dev-region="NextPageHotzone" data-reader-page-action="next" aria-disabled="${pageState.index >= pageState.count - 1 ? "true" : "false"}"></button>
      </section>`;
  }

  function readerMoreMenuHtml(appState) {
    if (!appState?.readerMoreOpen) return "";
    const items = [
      ["刷新本章", "重新拉取当前章节正文"],
      ["刷新目录", "更新章节目录和缓存状态"],
      ["打开来源页", "查看当前书源详情"],
      ["复制本章链接", "复制当前章节来源地址"],
      ["书籍缓存", "管理当前书籍缓存"],
      ["调试信息", "打开阅读调试信息"]
    ];
    return `
      <div class="fd-reader-more-layer" data-reader-more-layer>
        <button class="fd-reader-more-backdrop" type="button" data-reader-more-close aria-label="关闭阅读更多菜单"></button>
        <section class="fd-reader-more-menu" role="menu" aria-label="阅读更多菜单">
          ${items.map(([title, desc]) => `<button type="button" role="menuitem" data-reader-more-action="${esc(title)}"><strong>${esc(title)}</strong><small>${esc(desc)}</small></button>`).join("")}
        </section>
      </div>`;
  }

  function readerTopOverlay(data, appState) {
    return `
      <section class="fd-reader-top" data-dev-region="ReaderTopBar">
        <button type="button" aria-label="返回" data-reader-exit>${icon("back", "fd-icon")}</button>
        <span><strong>${esc(data.reader.title)}</strong><small>${esc(data.reader.sourceLine)}</small></span>
        <button type="button" data-route="source-switch">${icon("source", "fd-small-icon")}换源</button>
        <button type="button" aria-label="更多" data-reader-more-toggle aria-expanded="${appState?.readerMoreOpen ? "true" : "false"}">${icon("more", "fd-small-icon")}</button>
      </section>
      ${readerMoreMenuHtml(appState)}`;
  }

  function readerQuickActionPanel(type) {
    const panels = {
      search: {
        title: "内容搜索",
        meta: "仅在当前书籍正文内定位结果",
        body: `
          <label class="fd-reader-panel-search">${icon("search", "fd-small-icon")}<span>雨夜</span></label>
          <div class="fd-reader-module-list">
            <button type="button" data-route="immersive-reading"><strong>第 32 章 雨夜</strong><small>雨夜的风格外冷 · 当前结果 1/2</small></button>
            <button type="button" data-route="immersive-reading"><strong>第 33 章 灯塔</strong><small>雨夜之后，远处灯塔亮起 · 结果 2/2</small></button>
          </div>`
      },
      "auto-page": {
        title: "自动翻页",
        meta: "启动后回到沉浸阅读并保留退出入口",
        body: `
          <div class="fd-reader-step-row"><strong>翻页速度</strong><span><button type="button">-</button><em>8 秒</em><button type="button">+</button></span></div>
          <div class="fd-reader-segment-row"><button class="is-active" type="button">连续</button><button type="button">单页</button><button type="button">跟随朗读</button></div>
          <button class="fd-reader-primary-action" type="button" data-route="immersive-reading">开始自动翻页</button>`
      },
      replace: {
        title: "内容替换",
        meta: "临时规则只影响当前阅读正文渲染",
        body: `
          <section class="fd-replace-summary" aria-label="内容替换总览">
            <button class="is-on" type="button" aria-pressed="true"><strong>启用内容替换</strong><span>当前书 3 条 · 命中 5 处</span></button>
          </section>
          <div class="fd-replace-rule-list" aria-label="替换规则摘要">
            <button class="fd-replace-rule-row is-on" type="button" data-route="content-replacement">
              <strong><span>雨夜称呼</span><em>已启用</em></strong>
              <small>雨夜 -> 雨声</small>
              <i><b>当前章节</b><b>命中 3 处</b></i>
            </button>
            <button class="fd-replace-rule-row is-on" type="button" data-route="content-replacement">
              <strong><span>旧称统一</span><em>已启用</em></strong>
              <small>旧城 -> 纸上城市</small>
              <i><b>当前书</b><b>命中 2 处</b></i>
            </button>
            <button class="fd-replace-rule-row" type="button" data-route="content-replacement">
              <strong><span>标点清理</span><em>暂停</em></strong>
              <small>正则规则 -> 规范标点</small>
              <i><b>本次临时</b><b>未命中</b></i>
            </button>
          </div>
          <button class="fd-reader-inline-entry fd-replace-add-entry" type="button" data-route="content-replacement">新增规则<span>${chevron()}</span></button>
          <button class="fd-reader-primary-action" type="button" data-route="reader">保存并返回控制层</button>`
      }
    };
    const panel = panels[type];
    if (!panel) return "";
    return `
        <section class="fd-reader-module-panel fd-reader-quick-detail" data-dev-region="ReaderQuickPanel" aria-label="${esc(panel.title)}">
        <header>
          <span><strong>${esc(panel.title)}</strong><small>${esc(panel.meta)}</small></span>
          <button type="button" data-route="reader">关闭</button>
        </header>
        ${panel.body}
      </section>`;
  }

  function readerModulePanel(type, appState, data) {
    if (type === "directory") {
      const tocMode = appState?.readerTocMode === "bookmark" ? "bookmark" : "directory";
      const currentChapterState = currentReaderChapter(data, appState);
      const directoryItems = readerChapters(data).slice(0, 5);
      const listHtml = tocMode === "bookmark" ? `
            <button type="button" data-route="immersive-reading">
              <i>${icon("bookmark", "fd-small-icon")}</i>
              <strong>第 28 章 旧梦</strong>
              <small>“他在雨声里停下脚步...”</small>
              <em>昨天 23:10</em>
            </button>
            <button class="is-current" type="button" data-route="immersive-reading">
              <i>${icon("bookmark", "fd-small-icon")}</i>
              <strong>第 32 章 雨夜</strong>
              <small>“雨，下了一整夜。”</small>
              <em>当前位置</em>
            </button>
            <button type="button" data-route="immersive-reading">
              <i>${icon("bookmark", "fd-small-icon")}</i>
              <strong>第 32 章 雨夜</strong>
              <small>“没有署名，只有短短四个字。”</small>
              <em>38%</em>
            </button>` : directoryItems.map((chapter, index) => `
            <button class="${index === currentChapterState.index ? "is-current" : ""}" type="button" data-reader-directory-index="${index}">
              <strong>${esc(chapter.title)}</strong>
              ${chapterMarkerText(chapter) ? `<em>${esc(chapterMarkerText(chapter))}</em>` : ""}
            </button>`).join("");
      return `
        <section class="fd-reader-module-panel fd-reader-toc-panel" data-dev-region="ReaderModulePanel" aria-label="目录与书签">
          <nav class="fd-reader-segment-row" aria-label="目录书签切换">
            <button class="${tocMode === "directory" ? "is-active" : ""}" type="button" data-reader-toc-mode="directory">目录</button>
            <button class="${tocMode === "bookmark" ? "is-active" : ""}" type="button" data-reader-toc-mode="bookmark">书签</button>
          </nav>
          <div class="fd-reader-module-list ${tocMode === "bookmark" ? "fd-reader-bookmark-list" : "fd-reader-directory-list"}">
            ${listHtml}
          </div>
          <button class="fd-reader-inline-entry" type="button" data-route="book-directory">${tocMode === "bookmark" ? "查看完整目录 / 书签" : "查看完整目录"}<span>${chevron()}</span></button>
        </section>`;
    }
    if (type === "tts") {
      const tts = appState.readerTts || {};
      const ttsDefaults = readerTtsConfig(data).defaults;
      return `
        <section class="fd-reader-module-panel fd-reader-tts-panel" data-dev-region="ReaderModulePanel" aria-label="朗读">
          <div class="fd-reader-transport">
            <button type="button" data-reader-tts-action="prev">上一句</button>
            <button class="is-primary ${tts.playing ? "is-playing" : ""}" type="button" data-reader-tts-action="toggle" aria-label="${tts.playing ? "暂停朗读" : "开始朗读"}">${icon(tts.playing ? "pause" : "play", "fd-small-icon")}</button>
            <button type="button" data-reader-tts-action="next">下一句</button>
          </div>
          <div class="fd-reader-option-grid">
            <button type="button" data-reader-tts-cycle="speed"><strong>语速</strong><span>${esc(tts.speed || ttsDefaults.speed)}</span><em>${chevron()}</em></button>
            <button type="button" data-reader-tts-cycle="voice"><strong>音色</strong><span>${esc(tts.voice || ttsDefaults.voice)}</span><em>${chevron()}</em></button>
            <button type="button" data-reader-tts-cycle="scope"><strong>范围</strong><span>${esc(tts.scope || ttsDefaults.scope)}</span><em>${chevron()}</em></button>
            <button type="button" data-reader-tts-cycle="timer"><strong>定时</strong><span>${esc(tts.timer || ttsDefaults.timer)}</span><em>${chevron()}</em></button>
          </div>
        </section>`;
    }
    if (type === "appearance") {
      const typography = appState?.readerTypography || normalizeReaderTypography(data);
      const activeTheme = currentReaderTheme(data, appState);
      const quickThemes = readerThemeOptions(data).slice(0, 4);
      const quickFonts = readerFontOptions(data).slice(0, 3);
      return `
        <section class="fd-reader-module-panel fd-reader-appearance-panel" data-dev-region="ReaderModulePanel" aria-label="阅读外观">
          <div class="fd-reader-theme-grid" aria-label="阅读主题">
            ${quickThemes.map((item) => `
              <button class="${activeTheme.value === item.value ? "is-active" : ""}" type="button" data-reader-theme="${esc(item.value)}" aria-pressed="${activeTheme.value === item.value ? "true" : "false"}">
                <span style="--swatch:${esc(item.swatch)}"></span>
                <strong>${esc(item.label)}</strong>
              </button>
            `).join("")}
          </div>
          <div class="fd-reader-appearance-grid">
            <section class="fd-reader-mini-control" data-typography-row="font-size" aria-label="字号">
              <strong>字号</strong>
              <span>
                <button type="button" data-reader-typography-action="font-size-decrease">A-</button>
                <em data-reader-typography-value="font-size">${esc(typographyNumber(typography.fontSize, 0))}</em>
                <button type="button" data-reader-typography-action="font-size-increase">A+</button>
              </span>
            </section>
            <section class="fd-reader-mini-control" aria-label="行距">
              <strong>行距</strong>
              <span>
                <button type="button" data-reader-typography-action="line-height-decrease">紧凑</button>
                <em data-reader-typography-value="line-height">${esc(typographyNumber(typography.lineHeight, 2))}</em>
                <button type="button" data-reader-typography-action="line-height-increase">宽松</button>
              </span>
            </section>
          </div>
          <div class="fd-reader-font-row fd-reader-font-row-wide" aria-label="字体">
            ${quickFonts.map((item) => `
              <button class="${typography.fontFamily === item.value ? "is-active" : ""}" type="button" data-reader-typography-set="fontFamily" data-reader-typography-value="${esc(item.value)}">${esc(item.label)}</button>
            `).join("")}
          </div>
          <section class="fd-reader-custom-area" aria-label="自定义">
            <div>
              <button type="button" data-route="reader-settings"><strong>背景色</strong><span>自定义${chevron()}</span></button>
              <button type="button" data-route="reader-settings"><strong>页边距</strong><span>默认${chevron()}</span></button>
            </div>
          </section>
          <button class="fd-reader-inline-entry" type="button" data-route="reader-settings">更多界面设置<span>${chevron()}</span></button>
        </section>`;
    }
    if (type === "settings") {
      const settings = appState.readerSettings || {};
      const settingDefaults = readerControlSettingsConfig(data).defaults;
      return `
        <section class="fd-reader-module-panel fd-reader-settings-panel" data-dev-region="ReaderModulePanel" aria-label="阅读设置">
          <div class="fd-reader-settings-list">
            <button type="button" data-reader-setting-toggle="autoPage"><i>${icon("auto-page", "fd-small-icon")}</i><strong>自动翻页</strong><span class="fd-reader-switch ${settings.autoPage ? "is-on" : ""}" aria-hidden="true"></span></button>
            <button type="button" data-reader-setting-cycle="tapMode"><i>${icon("gesture", "fd-small-icon")}</i><strong>点击翻页方式</strong><em>${esc(settings.tapMode || settingDefaults.tapMode)}${chevron()}</em></button>
            <button type="button" data-reader-setting-toggle="volumePage"><i>${icon("volume", "fd-small-icon")}</i><strong>音量键翻页</strong><span class="fd-reader-switch ${settings.volumePage ? "is-on" : ""}" aria-hidden="true"></span></button>
            <button type="button" data-reader-setting-cycle="pageAnimation"><i>${icon("motion", "fd-small-icon")}</i><strong>翻页动画</strong><em>${esc(settings.pageAnimation || settingDefaults.pageAnimation)}${chevron()}</em></button>
            <button type="button" data-reader-setting-toggle="landscapeLock"><i>${icon("permission", "fd-small-icon")}</i><strong>横屏锁定</strong><span class="fd-reader-switch ${settings.landscapeLock ? "is-on" : ""}" aria-hidden="true"></span></button>
            <button type="button" data-reader-setting-toggle="keepScreenOn"><i>${icon("sun", "fd-small-icon")}</i><strong>屏幕常亮</strong><span class="fd-reader-switch ${settings.keepScreenOn ? "is-on" : ""}" aria-hidden="true"></span></button>
            <button type="button" data-reader-setting-toggle="statusInfo"><i>${icon("progress", "fd-small-icon")}</i><strong>页脚进度信息</strong><span class="fd-reader-switch ${settings.statusInfo ? "is-on" : ""}" aria-hidden="true"></span></button>
            <button type="button" data-reader-setting-toggle="hapticFeedback"><i>${icon("gesture", "fd-small-icon")}</i><strong>触摸反馈</strong><span class="fd-reader-switch ${settings.hapticFeedback ? "is-on" : ""}" aria-hidden="true"></span></button>
            <button type="button" data-reader-setting-toggle="cacheNext"><i>${icon("download", "fd-small-icon")}</i><strong>自动缓存后续章节</strong><span class="fd-reader-switch ${settings.cacheNext ? "is-on" : ""}" aria-hidden="true"></span></button>
          </div>
          <button class="fd-reader-inline-entry" type="button" data-route="reader-settings">${icon("settings", "fd-small-icon")}更多阅读设置<span>${chevron()}</span></button>
        </section>`;
    }
    return "";
  }

  function readerModuleNavHtml(data, activeType) {
    const normalizedType = activeType || "";
    return data.reader.modules.map((item) => `
      <button class="fd-reader-module${item.type === normalizedType ? " is-active" : ""}" type="button" data-route="${esc(readerModuleRoutes[item.type] || "reader")}" data-module="${esc(item.type)}"${item.type === normalizedType ? ' aria-current="page"' : ""}>
        <span>${icon(item.icon || item.type, "fd-medium-icon")}</span>
        <small>${esc(item.label)}</small>
      </button>
    `).join("");
  }

  function readerBrightnessRail(data, appState) {
    const brightness = data.reader.brightness || {};
    const brightnessConfig = readerBrightnessConfig(data);
    const isAuto = Boolean(appState?.readerBrightnessAuto);
    const value = readerBrightnessValue(data, appState);
    return `
      <aside class="fd-brightness-rail" aria-label="亮度控制" data-dev-region="BrightnessRail" style="--brightness:${esc(value)}%">
        ${icon("sun", "fd-small-icon")}
        <i data-reader-brightness-track role="slider" aria-label="调整亮度" aria-orientation="vertical" aria-valuemin="${esc(brightnessConfig.min)}" aria-valuemax="${esc(brightnessConfig.max)}" aria-valuenow="${esc(value)}" tabindex="0"><b></b></i>
        <button class="fd-brightness-auto-toggle${isAuto ? " is-active" : ""}" type="button" data-reader-brightness-auto aria-pressed="${isAuto ? "true" : "false"}" aria-label="${esc(brightness.autoText || "自动亮度")}">${esc(brightness.autoLabel || "A")}</button>
      </aside>`;
  }

  function readerControlMain(data, appState) {
    const chapter = data.reader.chapterProgress || {};
    const chapterProgressConfig = readerChapterProgressConfig(data);
    const chapterState = currentReaderChapter(data, appState);
    const chapterProgress = readerChapterProgressValue(data, appState);
    const chapterTitle = chapterState.chapter.title || chapter.title || "第 32 章 雨夜";
    const chapterNumber = readerChapterNumber(chapterTitle, chapterState.index + 1);
    const totalChapterCount = readerTotalChapterCount(data, chapterState.count);
    return `
      <div class="fd-reader-control-main" data-dev-region="BottomControlPanel">
        <nav class="fd-reader-actions" aria-label="快捷操作">
          ${data.reader.quickActions.map((item) => `
            <button type="button" data-route="${esc(item.type === "search" ? "content-search" : item.type === "auto-page" ? "auto-page" : "content-replacement")}" data-quick-action="${esc(item.type)}">${icon(item.type, "fd-medium-icon")}<span>${esc(item.label)}</span></button>
          `).join("")}
        </nav>
        <section class="fd-reader-chapter-panel" aria-label="书籍进度">
          <div class="fd-reader-chapter-row">
            <strong data-reader-current-chapter>${esc(chapterTitle)}</strong>
            <span>
              <button type="button" data-reader-chapter-action="prev" aria-disabled="${chapterState.index === 0 ? "true" : "false"}">${esc(chapter.previousLabel || "上一章")}</button>
              <button type="button" data-reader-chapter-action="next" aria-disabled="${chapterState.index >= chapterState.count - 1 ? "true" : "false"}">${esc(chapter.nextLabel || "下一章")}</button>
            </span>
          </div>
          <button class="fd-reader-progress" type="button" style="--progress:${esc(pct(`${chapterProgress}%`))}" data-reader-chapter-progress aria-label="调整书籍进度" aria-valuemin="${esc(chapterProgressConfig.min)}" aria-valuemax="${esc(chapterProgressConfig.max)}" aria-valuenow="${esc(chapterProgress)}">
            <i><b></b></i>
          </button>
          <small>${esc(readerBookProgressLabel(data, appState))} · 第 ${esc(chapterNumber)} / ${esc(totalChapterCount)} 章</small>
        </section>
      </div>`;
  }

  function readerLoadingPanel(route) {
    const routeTitle = (routes[route] || routes.reader).title.replace(/（.+$/, "");
    return `
      <section class="fd-reader-loading-panel" data-reader-loading aria-live="polite" aria-label="ReaderShell 加载状态">
        <i aria-hidden="true"></i>
        <strong>正在加载${esc(routeTitle)}</strong>
        <small>保持同一正文底层，只替换控制面板内容</small>
      </section>`;
  }

  function readerBottomSheetHtml(data, state, route, isLoading, appState) {
    if (state.mode === "immersive") {
      return "";
    }
    let bodyHtml = "";
    if (isLoading) {
      bodyHtml = readerLoadingPanel(route);
    } else if (state.mode === "quick") {
      bodyHtml = readerQuickActionPanel(state.quick);
    } else if (state.mode === "module") {
      bodyHtml = readerModulePanel(state.module, appState, data);
    } else {
      bodyHtml = readerControlMain(data, appState);
    }
    return `
      <div class="fd-reader-grabber"></div>
      ${bodyHtml}
      ${readerBrightnessRail(data, appState)}`;
  }

  function readerStateScreen(data, route, options, appState) {
    const baseState = readerRouteState(route);
    const isLoading = Boolean(options && options.loading);
    const state = isLoading ? Object.assign({}, baseState, { mode: "loading" }) : baseState;
    const isImmersive = baseState.mode === "immersive" && !isLoading;
    const activeModule = baseState.mode === "module" ? baseState.module : "";
    const frameMode = isImmersive ? "immersive" : state.mode;

    return shellKit().renderReaderShell({
      frameClass: `fd-reader-frame fd-reader-flow-frame fd-reader-mode-${esc(frameMode)}${isImmersive ? " fd-immersive-frame" : ""}`,
      readingSurfaceClass: "fd-reading-surface",
      overlayClass: `fd-reader-overlay${isImmersive ? " fd-immersive-overlay" : ""}`,
      bottomSheetHostClass: isImmersive ? "fd-reader-sheet fd-reader-sheet-empty" : "fd-reader-sheet",
      moduleNavClass: isImmersive ? "fd-reader-module-nav fd-reader-module-nav-empty" : "fd-reader-module-nav",
      stateHostClass: "fd-reader-state-host",
      stateHostHtml: `<div class="fd-reader-global-brightness-dim" data-reader-brightness-dim aria-hidden="true" style="${readerBrightnessStyle(data, appState)}"></div>`,
      ariaLabel: (routes[route] || routes.reader).title,
      readingSurfaceHtml: sharedReaderSurface(data, isImmersive ? "" : "immersive-reading", appState),
      overlayHtml: isImmersive ? `${readerInfoOverlay(data, appState)}${readerTapZones(data, appState)}` : readerTopOverlay(data, appState),
      bottomSheetHtml: readerBottomSheetHtml(data, state, route, isLoading, appState),
      moduleNavHtml: isImmersive ? "" : readerModuleNavHtml(data, activeModule)
    });
  }

  function readerProgressBase(data) {
    const raw = data.reader?.bottomReadout?.progress || "38%";
    const parsed = Number.parseFloat(String(raw).replace("%", ""));
    return Number.isFinite(parsed) ? parsed : 38;
  }

  function readerProgressForPage(data, index, count) {
    const base = readerProgressBase(data);
    const span = Math.max(2, Math.min(8, Math.ceil(count * 0.45)));
    const value = count <= 1 ? base : base + (index / Math.max(1, count - 1)) * span;
    const rounded = Math.round(value * 10) / 10;
    return `${Number.isInteger(rounded) ? rounded.toFixed(0) : rounded}%`;
  }

  function readerSourceSignature(blocks) {
    return blocks.map((item) => item.length).join(".");
  }

  function readerSplitIndex(text, bestIndex) {
    if (bestIndex >= text.length) {
      return text.length;
    }
    const start = Math.max(1, bestIndex - 12);
    const punctuation = "，。！？；：、,.!?;:";
    for (let index = bestIndex; index >= start; index -= 1) {
      if (punctuation.includes(text.charAt(index - 1))) {
        return index;
      }
    }
    return Math.max(1, bestIndex);
  }

  function updateReaderPagination(screenHost, data, appState) {
    const layer = screenHost.querySelector(".fd-ir-reading-layer");
    if (!layer || !appState) {
      return false;
    }
    const rect = layer.getBoundingClientRect();
    const width = Math.round(rect.width);
    const height = Math.round(rect.height);
    if (width <= 0 || height <= 0) {
      return false;
    }

    const typography = appState.readerTypography || normalizeReaderTypography(data);
    const sourceBlocks = readerTextBlocks(data);
    const key = [
      width,
      height,
      typography.fontSize,
      typography.lineHeight,
      typography.paragraphGap,
      typography.letterSpacing,
      typography.fontFamily,
      readerSourceSignature(sourceBlocks)
    ].join("|");

    if (appState.readerPaginationKey === key && Array.isArray(appState.readerPages) && appState.readerPages.length > 0) {
      return false;
    }

    const measurer = document.createElement("article");
    measurer.className = "fd-ir-reading-layer fd-ir-measure-layer";
    measurer.setAttribute("aria-hidden", "true");
    measurer.style.cssText = [
      readerTypographyStyle(data, typography),
      "position:fixed",
      "inset:auto",
      "left:-10000px",
      "top:0",
      `width:${width}px`,
      `height:${height}px`,
      "overflow:hidden",
      "visibility:hidden",
      "pointer-events:none",
      "z-index:-1"
    ].join(";");
    document.body.appendChild(measurer);

    const writeMeasureContent = (paragraphs, includeTitle) => {
      const titleHtml = includeTitle ? `<h1>${esc(readerChapterTitle(data))}</h1>` : "";
      measurer.innerHTML = `${titleHtml}${paragraphs.map((paragraph) => `<p>${esc(paragraph)}</p>`).join("")}`;
      return measurer.scrollHeight <= height + 1;
    };

    const fitSplitIndex = (paragraphs, text, includeTitle) => {
      let low = 1;
      let high = text.length;
      let best = 0;
      while (low <= high) {
        const middle = Math.floor((low + high) / 2);
        const candidate = text.slice(0, middle).trimEnd();
        if (candidate && writeMeasureContent(paragraphs.concat(candidate), includeTitle)) {
          best = middle;
          low = middle + 1;
        } else {
          high = middle - 1;
        }
      }
      return best > 0 ? readerSplitIndex(text, best) : 0;
    };

    const pages = [];
    let blockIndex = 0;
    let offset = 0;
    const maxPages = 80;
    while (blockIndex < sourceBlocks.length && pages.length < maxPages) {
      const includeTitle = pages.length === 0;
      const pageParagraphs = [];
      let madeProgress = false;

      while (blockIndex < sourceBlocks.length) {
        const source = sourceBlocks[blockIndex] || "";
        const remaining = source.slice(offset);
        if (!remaining.trim()) {
          blockIndex += 1;
          offset = 0;
          continue;
        }

        const fullParagraphs = pageParagraphs.concat(remaining);
        if (writeMeasureContent(fullParagraphs, includeTitle)) {
          pageParagraphs.push(remaining);
          blockIndex += 1;
          offset = 0;
          madeProgress = true;
          continue;
        }

        const splitIndex = fitSplitIndex(pageParagraphs, remaining, includeTitle);
        if (splitIndex > 0) {
          pageParagraphs.push(remaining.slice(0, splitIndex).trimEnd());
          offset += splitIndex;
          madeProgress = true;
        }
        break;
      }

      if (!madeProgress && blockIndex < sourceBlocks.length) {
        const source = sourceBlocks[blockIndex] || "";
        const forced = source.slice(offset, Math.min(source.length, offset + 1));
        if (forced) {
          pageParagraphs.push(forced);
          offset += forced.length;
          madeProgress = true;
        }
      }

      if (pageParagraphs.length > 0) {
        pages.push({ progress: "", paragraphs: pageParagraphs });
      } else {
        break;
      }

      if (blockIndex < sourceBlocks.length && offset >= sourceBlocks[blockIndex].length) {
        blockIndex += 1;
        offset = 0;
      }
    }

    measurer.remove();
    if (pages.length === 0) {
      return false;
    }

    pages.forEach((page, index) => {
      page.progress = readerProgressForPage(data, index, pages.length);
    });
    appState.readerPages = pages;
    appState.readerPaginationKey = key;
    appState.readerPageIndex = Math.max(0, Math.min(Number(appState.readerPageIndex) || 0, pages.length - 1));
    return true;
  }

  function settingsPageFor(route, data) {
    const previewBooks = (data.mainTabs.books || []).slice(0, 3).map((book) => ({
      title: book.title,
      meta: book.chapter,
      update: book.progress,
      badge: "1",
      cover: cover(data, book.coverKey)
    }));
    const pages = {
      "settings-general": {
        title: "通用设置",
        sections: [
          {
            title: "基础偏好",
            rows: [
              { type: "segment", icon: "palette", title: "App主题", value: "跟随系统", options: ["跟随系统", "浅色", "深色"] },
              { type: "select", icon: "globe", title: "语言", value: "简体中文", options: ["简体中文", "繁體中文", "English"] },
              { type: "select", icon: "home", title: "启动时打开", value: "书架", options: ["书架", "发现", "RSS", "设置"] }
            ]
          },
          {
            title: "行为与反馈",
            rows: [
              { type: "switch", icon: "refresh", title: "自动检查更新", meta: "有新版本时自动检查并提示", enabled: true },
              { type: "switch", icon: "top", title: "点击当前底栏回顶部", meta: "再次点击当前底栏按钮时回到页面顶部", enabled: true },
              { type: "switch", icon: "motion", title: "减少动态效果", meta: "降低动画效果以提升流畅度", enabled: true },
              { type: "switch", icon: "bug", title: "崩溃日志", meta: "仅保存本地诊断日志，便于排查问题", enabled: true, status: "已开启", statusTone: "good" },
              { type: "select", icon: "play", title: "动画效果", meta: "设置界面内的动画播放效果强度", value: "标准", options: ["减少", "标准", "增强"] }
            ]
          }
        ],
        actions: [{ tone: "danger", icon: "refresh", title: "恢复默认", meta: "恢复通用设置默认值", overlay: "dialog" }],
        confirm: { title: "恢复通用设置？", copy: "恢复后将重置 App 主题、语言、启动页面和行为偏好。", confirmLabel: "确认恢复" }
      },
      "bookshelf-search-settings": {
        title: "书架与搜索",
        sections: [
          {
            title: "书架",
            rows: [
              { type: "segment", icon: "grid", title: "默认展示", value: "封面", options: ["封面", "列表"] },
              { type: "stepper", icon: "columns", title: "封面列数", value: "3列", minLabel: "-", maxLabel: "+" },
              { type: "select", icon: "folder", title: "默认分组", value: "全部", options: ["全部", "长篇追读", "资料", "未分组"] },
              { type: "switch", icon: "badge", title: "显示更新标记", meta: "在书籍封面上显示更新标记", enabled: true }
            ],
            preview: { coverTitle: "封面模式预览", listTitle: "列表模式预览", books: previewBooks }
          },
          {
            title: "搜索",
            rows: [
              { type: "select", icon: "search", title: "搜索范围", value: "全局", options: ["当前分组", "书架", "全局"] },
              { type: "select", icon: "sort", title: "结果排序", value: "相关度", options: ["相关度", "最近阅读", "最近更新"] },
              { type: "switch", icon: "people", title: "合并同名同作者", meta: "搜索结果合并相同书名和作者的作品", enabled: true },
              { type: "switch", icon: "clock", title: "搜索历史", meta: "记录搜索关键词以便快速访问", enabled: true },
              { type: "select", icon: "list", title: "搜索历史数量", meta: "设置保存的搜索历史条数上限", value: "20条", options: ["10条", "20条", "50条"] }
            ]
          }
        ],
        actions: [{ tone: "danger", icon: "trash", title: "清空搜索历史", meta: "清除本机保存的搜索记录", overlay: "dialog" }],
        confirm: { title: "清空搜索历史？", copy: "清空后无法恢复，已保存的搜索关键词会被移除。", confirmLabel: "确认清空" }
      },
      "privacy-permissions": {
        title: "隐私与权限",
        sections: [
          {
            title: "系统权限",
            rows: [
              { type: "link", icon: "folder", title: "文件访问", meta: "访问设备上的文件和媒体内容", status: "已授权", statusTone: "good", value: "已允许" },
              { type: "link", icon: "bell", title: "通知权限", meta: "接收系统通知和消息提醒", status: "未授权", statusTone: "warn", actionLabel: "去设置" },
              { type: "link", icon: "battery", title: "电池优化", meta: "后台运行与电池使用受系统管理", value: "受系统管理" }
            ]
          },
          {
            title: "隐私设置",
            rows: [
              { type: "switch", icon: "eyeOff", title: "隐私开关", meta: "不记录最近阅读与搜索历史", enabled: false },
              { type: "switch", icon: "clock", title: "保存搜索历史", meta: "记录搜索关键词以便快速访问", enabled: true },
              { type: "switch", icon: "bug", title: "发送崩溃日志", meta: "仅在你主动确认后发送", enabled: false }
            ]
          },
          {
            title: "数据与说明",
            rows: [
              { type: "link", icon: "info", title: "网络访问说明", meta: "说明网络访问用途和可用边界" },
              { type: "link", icon: "file", title: "本地数据说明", meta: "了解本地数据的存储与使用" },
              { type: "link", icon: "shield", title: "隐私说明", meta: "查看隐私政策与相关条款" }
            ]
          }
        ],
        actions: [{ tone: "danger", icon: "trash", title: "清除隐私数据", meta: "清除所有隐私相关数据与记录", overlay: "dialog" }],
        confirm: { title: "清除隐私数据？", copy: "清除后将移除搜索历史、阅读痕迹和本地隐私记录。", confirmLabel: "确认清除" }
      },
      "cache-management": {
        title: "缓存管理",
        storage: { title: "缓存占用", value: "1.28 GB", percent: "62%", copy: "正在计算各类缓存占用，分类结果会在下方同步更新。" },
        sections: [
          {
            title: "缓存分类",
            rows: [
              { type: "link", icon: "clock", title: "总缓存", meta: "所有缓存数据占用的存储空间", value: "1.28 GB" },
              { type: "link", icon: "book", title: "书籍缓存", meta: "章节内容、目录等阅读数据", value: "892 MB" },
              { type: "link", icon: "image", title: "封面缓存", meta: "书籍封面图片缓存", value: "126 MB" },
              { type: "link", icon: "search", title: "搜索缓存", meta: "搜索记录、索引和结果缓存", value: "84 MB" },
              { type: "link", icon: "source", title: "RSS 缓存", meta: "订阅内容、图片和附件缓存", value: "178 MB" }
            ]
          },
          {
            title: "缓存策略",
            rows: [
              { type: "switch", icon: "storage", title: "优先读取缓存", meta: "优先使用本地缓存提升阅读速度", enabled: true },
              { type: "switch", icon: "download", title: "自动缓存后续章节", meta: "预读并缓存后续章节以便离线阅读", enabled: true },
              { type: "select", icon: "list", title: "缓存范围", meta: "设置预读并缓存的章节数量", value: "5 章", options: ["3 章", "5 章", "10 章"] },
              { type: "select", icon: "folder", title: "下载与缓存位置", meta: "选择下载和缓存文件的存储位置", value: "内部存储", options: ["内部存储", "外部存储"] }
            ]
          }
        ],
        actions: [{ tone: "danger", icon: "trash", title: "清理缓存", meta: "删除所有缓存数据，释放存储空间", overlay: "dialog" }],
        confirm: { title: "确认清理缓存？", copy: "清理后会删除章节、封面、搜索和 RSS 缓存，不会删除书架记录。", confirmLabel: "确认清理" }
      },
      "about-feedback": {
        title: "关于与反馈",
        metrics: [
          { icon: "book", label: "当前版本", value: "Reader 1.0.0" },
          { icon: "check", label: "更新状态", value: "已是最新版本" }
        ],
        sections: [
          {
            title: "应用信息",
            rows: [
              { type: "link", icon: "refresh", title: "检查更新", meta: "检查应用是否有新版本", value: "已是最新版本" },
              { type: "link", icon: "file", title: "更新日志", meta: "查看版本更新记录" },
              { type: "link", icon: "link", title: "开源许可", meta: "查看开源软件许可信息" }
            ]
          },
          {
            title: "帮助与反馈",
            rows: [
              { type: "link", icon: "info", title: "使用帮助", meta: "查看常见问题与使用指南" },
              { type: "link", icon: "message", title: "问题反馈", meta: "提交问题或建议给开发团队" },
              { type: "link", icon: "log", title: "导出诊断日志", meta: "导出应用运行日志以便排查问题", overlay: "dialog" }
            ]
          },
          {
            title: "法律与隐私",
            rows: [
              { type: "link", icon: "shield", title: "隐私协议", meta: "了解我们如何收集与使用数据" },
              { type: "link", icon: "file", title: "用户协议", meta: "阅读完整的用户协议" }
            ]
          }
        ],
        confirm: { title: "导出诊断日志？", copy: "导出的日志只包含运行信息，不包含书籍正文。", confirmLabel: "确认导出" }
      },
      "sync-backup": {
        title: "同步与备份",
        sections: [
          {
            title: "本地备份",
            rows: [
              { type: "link", icon: "clock", title: "立即备份", meta: "手动备份所有数据到本地", value: "今天 10:30" },
              { type: "switch", icon: "clock", title: "自动备份", meta: "每天保留最近 7 份备份", enabled: true },
              { type: "select", icon: "folder", title: "备份位置", meta: "选择本地备份文件存储位置", value: "内部存储", options: ["内部存储", "外部文件夹", "WebDAV"] },
              { type: "link", icon: "upload", title: "导出数据", meta: "将数据导出为文件到本地" },
              { type: "link", icon: "download", title: "恢复备份", meta: "从备份文件导入数据并恢复", overlay: "dialog" }
            ]
          },
          {
            title: "WebDAV",
            rows: [
              { type: "link", icon: "source", title: "WebDAV 未配置", meta: "配置 WebDAV 以同步数据", actionLabel: "去配置" },
              { type: "switch", icon: "refresh", title: "自动同步阅读进度", meta: "通过 WebDAV 自动同步阅读进度", enabled: false },
              { type: "select", icon: "warning", title: "同步冲突处理", meta: "当出现数据冲突时如何处理", value: "询问我", options: ["询问我", "保留本地", "使用远端"] }
            ]
          },
          {
            title: "同步状态",
            rows: [
              { type: "link", icon: "refresh", title: "上次同步：尚未同步", meta: "暂无同步记录", actionLabel: "立即同步" },
              { type: "link", icon: "warning", title: "同步失败", meta: "WebDAV 未配置时无法自动同步", status: "待配置", statusTone: "warn" }
            ]
          }
        ],
        records: [
          { icon: "file", title: "备份记录 2026-06-20 10:30", meta: "本地备份 · 12.8 MB", status: "可恢复", tone: "good" },
          { icon: "file", title: "备份记录 2026-06-19 22:10", meta: "本地备份 · 12.4 MB", status: "已校验", tone: "info" }
        ],
        confirm: { title: "恢复备份？", copy: "恢复备份会覆盖当前书架、阅读进度和设置，请确认已完成当前数据备份。", confirmLabel: "确认恢复" }
      },
      "source-management": {
        title: "书源管理",
        metrics: [
          { icon: "source", label: "个书源", value: "12" },
          { icon: "check", label: "个启用", value: "8" },
          { icon: "warning", label: "个异常", value: "4" },
          { icon: "clock", label: "刚刚检测", value: "10:30" }
        ],
        searchBox: { placeholder: "搜索框：搜索书源名称或域名" },
        filters: [
          { label: "全部", active: true },
          { label: "已启用" },
          { label: "异常" },
          { label: "未检测" },
          { label: "自定义" }
        ],
        groups: [
          { label: "全部分组", active: true },
          { label: "玄幻书源" },
          { label: "起点导入" },
          { label: "测试书源" }
        ],
        sections: [
          {
            title: "批量操作",
            rows: [
              { type: "link", icon: "refresh", title: "检测", meta: "检测全部启用书源的可用状态", actionLabel: "开始检测" },
              { type: "link", icon: "info", title: "详情", meta: "查看书源状态、分组和检测入口" },
              { type: "link", icon: "edit", title: "编辑", meta: "进入书源详情后可编辑规则配置", overlay: "edit" },
              { type: "link", icon: "log", title: "错误日志", meta: "查看最近一次检测失败原因", overlay: "log" },
              { type: "switch", icon: "source", title: "启用开关", meta: "控制选中书源是否参与搜索与换源", enabled: true, overlay: "dialog" }
            ]
          }
        ],
        sources: [
          { title: "起点中文网", meta: "qidian.com · 起点导入", status: "可用", tone: "good", enabled: true },
          { title: "笔趣阁", meta: "biquge.example · 玄幻书源", status: "异常", tone: "warn", enabled: true },
          { title: "本地导入源", meta: "本地文件导入 · 自定义", status: "未检测", tone: "muted", enabled: false },
          { title: "测试书源", meta: "test.example · 测试书源", status: "可用", tone: "good", enabled: true }
        ],
        fab: { icon: "add", label: "新增" },
        subPanels: [
          { type: "edit", title: "SourceEditForm · 新增书源", rows: [{ label: "书源名称", value: "测试书源" }, { label: "域名", value: "test.example" }, { label: "分组", value: "测试书源" }], action: "保存" },
          { type: "log", title: "LogPanel · 错误日志", rows: [{ label: "ERROR", value: "笔趣阁目录解析失败，返回字段缺失。" }, { label: "WARN", value: "本地导入源尚未检测，可手动点击检测。" }] }
        ],
        confirm: { title: "禁用书源？", copy: "禁用后该书源不会参与搜索、发现和阅读中换源。", confirmLabel: "确认禁用" }
      }
    };
    return pages[route] || pages["settings-general"];
  }

  function settingsBadge(label, tone) {
    if (!label) return "";
    return `<em class="fd-settings-badge is-${esc(tone || "muted")}">${esc(label)}</em>`;
  }

  function settingsSwitch(enabled) {
    return `<span class="fd-settings-switch${enabled ? " is-on" : ""}" aria-hidden="true"><i></i></span>`;
  }

  function settingsSegment(row) {
    return `
      <span class="fd-settings-segment" aria-label="${esc(row.title)}">
        ${(row.options || []).map((option) => `<button class="${option === row.value ? "is-active" : ""}" type="button">${esc(option)}</button>`).join("")}
      </span>`;
  }

  function settingsStepper(row) {
    return `
      <span class="fd-settings-stepper" aria-label="${esc(row.title)}">
        <button type="button">${esc(row.minLabel || "-")}</button>
        <strong>${esc(row.value)}</strong>
        <button type="button">${esc(row.maxLabel || "+")}</button>
      </span>`;
  }

  function settingsRowSide(row) {
    const status = settingsBadge(row.status, row.statusTone);
    const selector = "";
    const stepper = row.type === "stepper" ? settingsStepper(row) : "";
    const toggle = row.type === "switch" ? settingsSwitch(row.enabled) : "";
    const value = row.value && !selector && !stepper ? `<strong class="fd-settings-value">${esc(row.value)}</strong>` : "";
    const action = row.actionLabel ? `<button class="fd-settings-row-action" type="button">${esc(row.actionLabel)}</button>` : "";
    const chevron = row.options || ["link", "select", "danger"].includes(row.type) ? icon("chevron", "fd-small-icon") : "";
    return `${status}${selector}${stepper}${value}${action}${toggle}${chevron}`;
  }

  function settingsOptionKey(route, title) {
    return `${route}:${String(title || "").replace(/\s+/g, "-")}`;
  }

  function settingsOptionDropdownHtml(row, route, appState) {
    if (!row.options || !row.options.length) return "";
    const key = settingsOptionKey(route, row.title);
    if (appState?.settingsExpandedOption !== key) return "";
    const current = row.value;
    const options = row.options;
    return `
      <div class="fd-settings-option-dropdown" data-settings-option-dropdown="${esc(key)}" role="listbox" aria-label="${esc(row.title)}可选项">
        ${options.map((option) => `<button class="${option === current ? "is-selected" : ""}" type="button" role="option" aria-selected="${option === current ? "true" : "false"}" data-settings-option-choice="${esc(key)}" data-settings-option-value="${esc(option)}"><span>${esc(option)}</span>${option === current ? icon("check", "fd-small-icon") : ""}</button>`).join("")}
      </div>`;
  }

  function settingsRowHtml(row, route, appState) {
    const key = row.options ? settingsOptionKey(route, row.title) : "";
    const optionOpen = row.options && appState?.settingsExpandedOption === key;
    const overlayAttr = row.overlay ? ` data-settings-overlay="${esc(row.overlay)}"` : row.options ? ` data-settings-option-key="${esc(key)}"` : "";
    return `
      <article class="fd-setting-row${row.tone === "danger" ? " is-danger" : ""}${optionOpen ? " is-option-open" : ""}"${overlayAttr} role="${overlayAttr ? "button" : "group"}" tabindex="${overlayAttr ? "0" : "-1"}">
        <span>${icon(row.icon || "settings", "fd-small-icon")}</span>
        <strong>${esc(row.title)}${row.meta ? `<small>${esc(row.meta)}</small>` : ""}</strong>
        <em>${settingsRowSide(row)}</em>
        ${settingsOptionDropdownHtml(row, route, appState)}
      </article>`;
  }

  function settingsBookPreview(preview) {
    if (!preview) return "";
    const books = preview.books || [];
    return `
      <section class="fd-settings-bookshelf-preview" aria-label="书架布局预览">
        <div>
          <h3>${esc(preview.coverTitle || "封面模式预览")}</h3>
          <div>${books.map((book) => `<span><img src="${esc(book.cover)}" alt="${esc(book.title)}封面"><i>${esc(book.badge || "")}</i><strong>${esc(book.title)}</strong></span>`).join("")}</div>
        </div>
        <div>
          <h3>${esc(preview.listTitle || "列表模式预览")}</h3>
          ${books.map((book) => `<article><img src="${esc(book.cover)}" alt="${esc(book.title)}封面"><span><strong>${esc(book.title)}</strong><small>${esc(book.meta)}</small></span><em>${esc(book.update)}</em></article>`).join("")}
        </div>
      </section>`;
  }

  function settingsSectionHtml(section, route, appState) {
    return `
      <section class="fd-setting-section" data-slot="settingSection">
        <h2>${esc(section.title)}</h2>
        ${section.rows.map((row) => settingsRowHtml(row, route, appState)).join("")}
        ${settingsBookPreview(section.preview)}
      </section>`;
  }

  function settingsMetricsHtml(metrics) {
    if (!metrics || !metrics.length) return "";
    return `
      <section class="fd-settings-metric-grid" aria-label="设置概览指标">
        ${metrics.map((item) => `<article>${icon(item.icon, "fd-small-icon")}<span><strong>${esc(item.value)}</strong><small>${esc(item.label)}</small></span></article>`).join("")}
      </section>`;
  }

  function settingsStorageHtml(storage) {
    if (!storage) return "";
    return `
      <section class="fd-settings-storage-card" aria-label="缓存占用">
        <header><strong>${esc(storage.title)}</strong><span>${esc(storage.value)}</span></header>
        <i style="--used:${esc(pct(storage.percent || "0%"))}"><b></b></i>
        <p>${esc(storage.copy)}</p>
      </section>`;
  }

  function settingsSearchHtml(searchBox) {
    if (!searchBox) return "";
    return `<label class="fd-settings-search-box">${icon("search", "fd-small-icon")}<span>${esc(searchBox.placeholder)}</span></label>`;
  }

  function settingsChipsHtml(items, label) {
    if (!items || !items.length) return "";
    return `<nav class="fd-settings-chip-row" aria-label="${esc(label)}">${items.map((item) => `<button class="${item.active ? "is-active" : ""}" type="button">${esc(item.label)}</button>`).join("")}</nav>`;
  }

  function settingsActionRowsHtml(actions) {
    if (!actions || !actions.length) return "";
    return `
      <section class="fd-settings-action-list" aria-label="设置操作">
        ${actions.map((item) => `
          <button class="${item.tone === "danger" ? "is-danger" : ""}" type="button" data-settings-overlay="${esc(item.overlay || "dialog")}">
            ${icon(item.icon || "info", "fd-small-icon")}
            <span><strong>${esc(item.title)}</strong><small>${esc(item.meta)}</small></span>
            ${icon("chevron", "fd-small-icon")}
          </button>`).join("")}
      </section>`;
  }

  function settingsRecordsHtml(records) {
    if (!records || !records.length) return "";
    return `
      <section class="fd-settings-record-list" aria-label="备份记录">
        <h2>备份记录</h2>
        ${records.map((item) => `<article>${icon(item.icon || "file", "fd-small-icon")}<span><strong>${esc(item.title)}</strong><small>${esc(item.meta)}</small></span>${settingsBadge(item.status, item.tone)}</article>`).join("")}
      </section>`;
  }

  function settingsSourceRowsHtml(sources) {
    if (!sources || !sources.length) return "";
    return `
      <section class="fd-settings-source-list" aria-label="书源列表">
        <h2>书源列表</h2>
        ${sources.map((item) => `
          <article class="fd-settings-source-row">
            ${icon("source", "fd-small-icon")}
            <span><strong>${esc(item.title)}</strong><small>${esc(item.meta)}</small></span>
            ${settingsBadge(item.status, item.tone)}
            ${settingsSwitch(item.enabled)}
          </article>`).join("")}
      </section>`;
  }

  function settingsSubPanelsHtml(panels) {
    if (!panels || !panels.length) return "";
    return `
      <section class="fd-settings-subpanels" aria-label="书源子状态">
        ${panels.map((panel) => `
          <article class="fd-settings-subpanel is-${esc(panel.type)}">
            <h2>${esc(panel.title)}</h2>
            ${(panel.rows || []).map((row) => `<p><strong>${esc(row.label)}</strong><span>${esc(row.value)}</span></p>`).join("")}
            ${panel.action ? `<button type="button">${esc(panel.action)}</button>` : ""}
          </article>`).join("")}
      </section>`;
  }

  function settingsOptionSheetHtml(page) {
    const optionRows = (page.sections || []).flatMap((section) => section.rows || []).filter((row) => row.options && row.options.length);
    const row = optionRows[0];
    if (!row) return "";
    return `
      <section class="fd-demo-sheet fd-settings-option-sheet" aria-hidden="false">
        <div class="fd-sheet-grabber"></div>
        <h2>${esc(row.title)}</h2>
        ${(row.options || []).map((option) => `<button class="${option === row.value ? "is-selected" : ""}" type="button">${esc(option)}</button>`).join("")}
        <button type="button" data-close-settings-overlay>取消</button>
      </section>`;
  }

  function settingsDialogHtml(page) {
    const confirm = page.confirm || {};
    if (!confirm.title) return "";
    return `
      <section class="fd-demo-dialog fd-settings-confirm-dialog" aria-hidden="false">
        <h2>${esc(confirm.title)}</h2>
        <p>${esc(confirm.copy)}</p>
        <div>
          <button type="button" data-close-settings-overlay>${esc(confirm.cancelLabel || "取消")}</button>
          <button type="button" data-close-settings-overlay>${esc(confirm.confirmLabel || "确认")}</button>
        </div>
      </section>`;
  }

  function settingsScreen(data, route, appState) {
    const page = settingsPageFor(route, data);
    const values = appState?.settingsValues || {};
    (page.sections || []).forEach((section) => {
      (section.rows || []).forEach((row) => {
        if (row.options && row.options.length) {
          const key = settingsOptionKey(route, row.title);
          if (values[key]) {
            row.value = values[key];
          }
        }
      });
    });
    const overlay = appState?.settingsOverlay || "";
    const frameState = overlay === "sheet" ? " has-sheet" : overlay === "dialog" ? " has-dialog" : "";
    const contentHtml = `
      ${settingsMetricsHtml(page.metrics)}
      ${settingsStorageHtml(page.storage)}
      ${settingsSearchHtml(page.searchBox)}
      ${settingsChipsHtml(page.filters, "书源状态筛选")}
      ${settingsChipsHtml(page.groups, "书源分组筛选")}
      ${(page.sections || []).map((section) => settingsSectionHtml(section, route, appState)).join("")}
      ${settingsActionRowsHtml(page.actions)}
      ${settingsRecordsHtml(page.records)}
      ${settingsSourceRowsHtml(page.sources)}
      ${settingsSubPanelsHtml(page.subPanels)}
      ${page.fab ? `<button class="fd-settings-fab" type="button">${icon(page.fab.icon || "add", "fd-small-icon")}<span>${esc(page.fab.label)}</span></button>` : ""}`;
    return shellKit().renderSettingsShell(Object.assign(phoneShellClasses(`fd-settings-phone${frameState}`), {
      data,
      title: page.title,
      ariaLabel: page.title,
      topBarClass: "fd-back-bar",
      contentClass: "fd-phone-content fd-settings-content",
      toastHostClass: "fd-toast-host",
      dialogHostClass: "fd-dialog-host",
      stateHostClass: "fd-settings-state-host",
      contentHtml,
      toastHtml: page.toast ? `<section class="fd-settings-toast">${esc(page.toast)}</section>` : "",
      dialogHtml: `${overlay === "sheet" ? settingsOptionSheetHtml(page) : ""}${overlay === "dialog" ? settingsDialogHtml(page) : ""}`
    }));
  }

  function sourceSwitchFilterTabs(filters) {
    return (filters || ["全部", "更新快", "已缓存", "可用"]).map((filter, index) => `
      <button class="${index === 0 ? "is-active" : ""}" type="button">${esc(filter)}</button>
    `).join("");
  }

  function sourceLatencyRank(item, index) {
    const speed = String(item.speed || "");
    const match = speed.match(/(\d+(?:\.\d+)?)/);
    if (!match) {
      return Number.MAX_SAFE_INTEGER + index;
    }
    return Number.parseFloat(match[1]);
  }

  function sourceCandidateRow(item, index) {
    const isCurrent = item.state === "当前";
    const isSelected = isCurrent;
    const canSwitch = !isCurrent && item.state !== "落后" && item.state !== "失效";
    const latestChapter = item.latestChapter || item.chapter || "最新章节";
    const speedLabel = /\d/.test(item.speed || "") ? `延迟 ${item.speed}` : item.speed;
    return `
      <article class="${isCurrent ? "is-current" : ""}${isSelected ? " is-selected" : ""}${canSwitch ? " is-switchable" : " is-muted"}" data-source-index="${index}" data-source-name="${esc(item.source)}" tabindex="0" role="button" aria-label="选择 ${esc(item.source)}">
        <span class="fd-source-row-main">
          <strong><b>${esc(item.source)}</b><em>${esc(speedLabel)}</em></strong>
          <small>最新章节：${esc(latestChapter)}</small>
        </span>
      </article>`;
  }

  function flowScreen(data, appState) {
    const flow = data.flow || {};
    const candidates = (flow.candidates || [])
      .map((item, index) => Object.assign({ _sourceOrder: index }, item))
      .sort((left, right) => {
        const latencyDelta = sourceLatencyRank(left, left._sourceOrder) - sourceLatencyRank(right, right._sourceOrder);
        return latencyDelta || left._sourceOrder - right._sourceOrder;
      });
    const current = candidates.find((item) => item.state === "当前") || candidates[0] || {};
    return shellKit().renderFlowShell({
      frameClass: "fd-flow-frame fd-source-phone-flow fd-source-reader-continuation",
      stepClass: "fd-flow-step fd-source-continuity-slot",
      comparisonClass: "fd-flow-comparison fd-source-window-slot",
      resultClass: "fd-flow-result fd-source-unused-slot",
      stateHostClass: "fd-source-unused-slot",
      ariaLabel: "换源",
      stepHtml: `
        <section class="fd-source-reader-continuity fd-source-control-continuity" aria-label="阅读控制层背景">
          ${sharedReaderSurface(data, "", appState, { disableTurnAnimation: true })}
          <section class="fd-source-control-overlay" aria-label="换源期间可操作的阅读控制层">
            ${readerTopOverlay(data, appState)}
            <div class="fd-reader-sheet fd-source-control-sheet">
              ${readerBottomSheetHtml(data, readerRouteState("reader"), "reader", false, appState)}
            </div>
            <nav class="fd-reader-module-nav fd-source-control-nav">
              ${readerModuleNavHtml(data, "")}
            </nav>
          </section>
        </section>`,
      comparisonHtml: `
        <section class="fd-source-switch-window" data-source-switch-window aria-label="换源窗口">
          <button class="fd-source-window-close" type="button" data-route="reader" data-route-replace aria-label="关闭换源窗口">${icon("close", "fd-small-icon")}</button>
          <header>
            <h2>换源</h2>
            <p>${esc(data.reader.title)} · ${esc(flow.chapter || "第 32 章")} · 当前 ${esc(current.source || "优书网")}</p>
          </header>
          <div class="fd-source-candidate-list">
            ${candidates.map((item, index) => sourceCandidateRow(item, index)).join("")}
          </div>
        </section>`,
      resultHtml: "",
      stateHostHtml: ""
    });
  }

  function sourceStrip(data) {
    return `
      <section class="fd-source-strip" data-slot="states">
        <h2>本地 UI 图参考层（Local UI Screenshot References）</h2>
        <div>
          ${data.screenshots.map((item) => `
            <article>
              <img src="${esc(item.src)}" alt="${esc(item.title)}">
              <strong>${esc(item.title)}</strong>
              <span>${esc(item.shell)}</span>
            </article>
          `).join("")}
        </div>
      </section>`;
  }

  function shellOverview(data) {
    return `
      <section class="fd-shell-overview" data-slot="appShell">
        <h2>页面框架总览（Page Shell Overview）</h2>
        <div>
          ${data.shells.map((shell) => `
            <article>
              <h3>${esc(shell.name)}</h3>
              <p>${esc(shell.pages)}</p>
              <code>${esc(shell.slots)}</code>
              <span>${esc(shell.status)}</span>
            </article>
          `).join("")}
        </div>
      </section>`;
  }

  function renderRoute(route, data, options, appState) {
    switch (route) {
      case "bookshelf":
        return mainTabBookshelf(data, appState);
      case "discover":
        return mainTabDiscover(data, appState);
      case "rss":
        return mainTabRss(data, appState);
      case "settings":
        return mainTabSettings(data, appState);
      case "book-search":
        return bookSearchScreen(data, appState);
      case "book-detail":
        return libraryScreen(data);
      case "book-directory":
        return bookDirectoryScreen(data);
      case "bookshelf-empty":
        return bookshelfEmptyScreen(data);
      case "sort-filter":
        return sortFilterScreen(data);
      case "group-management":
        return groupManagementScreen(data);
      case "local-import":
        return localImportScreen(data);
      case "immersive-reading":
      case "reader":
      case "toc-bookmarks":
      case "reader-appearance":
      case "tts":
      case "reader-settings":
      case "auto-page":
      case "content-search":
      case "content-replacement":
        return readerStateScreen(data, route, options, appState);
      case "source-switch":
        return flowScreen(data, appState);
      case "source-management":
      case "settings-general":
      case "bookshelf-search-settings":
      case "privacy-permissions":
      case "cache-management":
      case "about-feedback":
      case "sync-backup":
        return settingsScreen(data, route, appState);
      default:
        return mainTabBookshelf(data, appState);
    }
  }

  function renderStack(stack) {
    return stack.map((route, index) => {
      const meta = routes[route] || routes.bookshelf;
      return `<li${index === stack.length - 1 ? ' aria-current="step"' : ""}>${esc(meta.title)}</li>`;
    }).join("");
  }

  function initialAppState(data) {
    return {
      bookshelfView: "cover",
      bookSearchPhase: "before",
      readerChapterIndex: initialReaderChapterIndex(data),
      readerChapterProgress: readerChapterProgressValue(data, {}),
      readerTypography: normalizeReaderTypography(data),
      readerPages: [],
      readerPaginationKey: "",
      readerPageIndex: 0,
      readerTurnDirection: "",
      readerMoreOpen: false,
      readerTocMode: "directory",
      readerTheme: readerDefaultThemeValue(data),
      readerBrightness: readerBrightnessConfig(data).defaultValue,
      readerBrightnessAuto: false,
      readerTts: Object.assign({}, readerTtsConfig(data).defaults),
      readerSettings: Object.assign({}, readerControlSettingsConfig(data).defaults),
      settingsOverlay: "",
      settingsExpandedOption: "",
      settingsValues: {},
      mainTabFeedback: ""
    };
  }

  function renderCaptureBoard(target, data) {
    const routeList = Object.keys(routes);
    target.innerHTML = `
      <main class="fd-capture-board" data-capture-mode="all" aria-label="Figma 多页面捕获板">
        <header class="fd-capture-board-header">
          <p>Reader Android</p>
          <h1>Frontend Demo Draft - All Routes</h1>
          <span>由当前 demo renderer 输出，每个画布对应一个应用路由。</span>
        </header>
        <section class="fd-capture-grid">
          ${routeList.map((route) => {
            const meta = routes[route] || routes.bookshelf;
            const routeState = initialAppState(data);
            return `
              <article class="fd-capture-card" data-capture-route="${esc(route)}">
                <div class="fd-capture-card-head">
                  <strong>${esc(meta.title)}</strong>
                  <span>${esc(meta.shell)} · ${esc(route)}</span>
                </div>
                <div class="fd-capture-screen">
                  ${renderRoute(route, data, {}, routeState)}
                </div>
              </article>
            `;
          }).join("")}
        </section>
      </main>`;
  }

  function render(target, data) {
    try {
      if (new URLSearchParams(window.location.search).get("captureMode") === "all") {
        renderCaptureBoard(target, data);
        return;
      }
    } catch (error) {
      // Fall back to the interactive demo when URLSearchParams is unavailable.
    }
    target.innerHTML = `
      <main class="fd-demo" data-shell="ComponentLibraryShell" data-current-route="bookshelf" data-demo-mode="regular" aria-label="前端 Demo 设计稿">
        <nav class="fd-demo-mode-switch" aria-label="显示模式">
          <button class="is-active" type="button" data-demo-mode-option="regular" aria-pressed="true">常规显示</button>
          <button type="button" data-demo-mode-option="developer" aria-pressed="false">开发者模式</button>
        </nav>
        <header class="fd-demo-header" data-slot="foundations">
          <div>
            <p>Reader Android</p>
            <h1>${esc(data.meta.title)}</h1>
            <span>${esc(data.meta.subtitle)}</span>
          </div>
          <dl>
            <div><dt>UI 图</dt><dd>${esc(data.meta.screenCount)}</dd></div>
            <div><dt>页面框架</dt><dd>${esc(data.meta.shellCount)}</dd></div>
            <div><dt>交互模式</dt><dd>应用路由</dd></div>
          </dl>
        </header>
        <section class="fd-app-demo-board" data-slot="basicControls">
          <section class="fd-active-stage" aria-label="当前应用页面">
            <div class="fd-screen-board-head">
              <div>
                <h2>可交互应用 Demo（Interactive App Demo）</h2>
                <p class="fd-route-status">当前路由：书架（Bookshelf）</p>
              </div>
              <button class="fd-demo-back" type="button" data-demo-back disabled>返回上一页</button>
            </div>
            <div class="fd-active-screen" data-screen-host></div>
          </section>
          <aside class="fd-route-panel" aria-label="路由状态">
            <h2>路由状态</h2>
            <dl>
              <div><dt>当前 Shell</dt><dd data-current-shell>MainTabShell</dd></div>
              <div><dt>当前页面</dt><dd data-current-page>书架（Bookshelf）</dd></div>
              <div><dt>返回栈</dt><dd data-stack-size>1</dd></div>
            </dl>
            <ol data-route-stack>${renderStack(["bookshelf"])}</ol>
            <p>此面板只显示当前状态；页面切换必须从手机画布里的按钮、列表项、底部导航或返回动作触发。</p>
            <div class="fd-dev-range-legend" aria-label="开发者模式渲染范围图例">
              <span><i></i>Shell slot 渲染范围</span>
              <span><i></i>Reader 内部模块范围</span>
            </div>
          </aside>
        </section>
      </main>`;
    attachInteractions(target, data);
  }

  function attachInteractions(target, data) {
    const root = target.querySelector(".fd-demo");
    const screenHost = target.querySelector("[data-screen-host]");
    const routeStatus = target.querySelector(".fd-route-status");
    const backButton = target.querySelector("[data-demo-back]");
    const routeStackHost = target.querySelector("[data-route-stack]");
    const currentShell = target.querySelector("[data-current-shell]");
    const currentPage = target.querySelector("[data-current-page]");
    const stackSize = target.querySelector("[data-stack-size]");
    const routeStack = ["bookshelf"];
    const appState = initialAppState(data);
    let pendingRouteTimer = null;

    const setDemoMode = (mode) => {
      const normalizedMode = mode === "developer" ? "developer" : "regular";
      root.setAttribute("data-demo-mode", normalizedMode);
      target.querySelectorAll("[data-demo-mode-option]").forEach((button) => {
        const active = button.getAttribute("data-demo-mode-option") === normalizedMode;
        button.classList.toggle("is-active", active);
        button.setAttribute("aria-pressed", active ? "true" : "false");
      });
      try {
        window.localStorage.setItem("readerFrontendDemoMode", normalizedMode);
      } catch (error) {
        // Demo mode should remain usable even when storage is unavailable.
      }
    };

    target.querySelectorAll("[data-demo-mode-option]").forEach((button) => {
      button.addEventListener("click", () => setDemoMode(button.getAttribute("data-demo-mode-option")));
    });

    const updateRouteInfo = (route) => {
      const meta = routes[route] || routes.bookshelf;
      root.setAttribute("data-current-route", route);
      if (routeStatus) {
        routeStatus.textContent = `当前路由：${meta.title} · ${meta.shell}`;
      }
      if (currentShell) {
        currentShell.textContent = meta.shell;
      }
      if (currentPage) {
        currentPage.textContent = meta.title;
      }
      if (stackSize) {
        stackSize.textContent = String(routeStack.length);
      }
      if (routeStackHost) {
        routeStackHost.innerHTML = renderStack(routeStack);
      }
      if (backButton) {
        backButton.disabled = routeStack.length <= 1;
      }
    };

    const renderActiveRoute = (route, options) => {
      const renderedTurnDirection = appState.readerTurnDirection;
      screenHost.innerHTML = renderRoute(route, data, options, appState);
      updateRouteInfo(route);
      if (!options?.loading && updateReaderPagination(screenHost, data, appState)) {
        screenHost.innerHTML = renderRoute(route, data, options, appState);
        updateRouteInfo(route);
      }
      attachScreenInteractions(screenHost, goTo, goBack, goTab, replaceTopRoute, exitReader, appState, data, renderCurrentRoute);
      if (renderedTurnDirection) {
        const readingLayer = screenHost.querySelector(".fd-ir-reading-layer");
        const clearTurnClass = () => {
          if (readingLayer) {
            readingLayer.classList.remove("fd-reader-page-turn-next", "fd-reader-page-turn-prev");
          }
        };
        if (readingLayer) {
          readingLayer.addEventListener("animationend", clearTurnClass, { once: true });
          window.setTimeout(clearTurnClass, 260);
        }
      }
      appState.readerTurnDirection = "";
    };

    const renderCurrentRoute = () => {
      renderActiveRoute(routeStack[routeStack.length - 1]);
    };

    const goTo = (route, shouldPush) => {
      if (!routes[route]) {
        return;
      }
      if (pendingRouteTimer) {
        window.clearTimeout(pendingRouteTimer);
        pendingRouteTimer = null;
      }
      const previous = routeStack[routeStack.length - 1];
      if (shouldPush && previous !== route) {
        routeStack.push(route);
      }
      appState.settingsOverlay = "";
      appState.settingsExpandedOption = "";
      appState.mainTabFeedback = "";
      appState.readerMoreOpen = false;
      if (shouldLoadReaderTransition(previous, route)) {
        renderActiveRoute(route, { loading: true });
        pendingRouteTimer = window.setTimeout(() => {
          pendingRouteTimer = null;
          renderActiveRoute(route);
        }, 360);
        return;
      }
      renderActiveRoute(route);
    };

    const goTab = (route) => {
      if (!routes[route]) {
        return;
      }
      if (pendingRouteTimer) {
        window.clearTimeout(pendingRouteTimer);
        pendingRouteTimer = null;
      }
      appState.settingsOverlay = "";
      appState.settingsExpandedOption = "";
      appState.mainTabFeedback = "";
      appState.readerMoreOpen = false;
      routeStack.splice(0, routeStack.length, route);
      renderActiveRoute(route);
    };

    const replaceTopRoute = (route) => {
      if (!routes[route]) {
        return;
      }
      if (pendingRouteTimer) {
        window.clearTimeout(pendingRouteTimer);
        pendingRouteTimer = null;
      }
      if (routeStack.length === 0) {
        routeStack.push(route);
      } else {
        routeStack[routeStack.length - 1] = route;
      }
      appState.settingsOverlay = "";
      appState.settingsExpandedOption = "";
      appState.mainTabFeedback = "";
      appState.readerMoreOpen = false;
      renderActiveRoute(route);
    };

    const exitReader = () => {
      if (pendingRouteTimer) {
        window.clearTimeout(pendingRouteTimer);
        pendingRouteTimer = null;
      }
      while (routeStack.length > 1 && isReaderStateRoute(routeStack[routeStack.length - 1])) {
        routeStack.pop();
      }
      const targetRoute = routeStack[routeStack.length - 1];
      if (targetRoute && !isReaderStateRoute(targetRoute)) {
        renderActiveRoute(targetRoute);
        return;
      }
      routeStack.splice(0, routeStack.length, "bookshelf");
      renderActiveRoute("bookshelf");
    };

    function goBack() {
      if (routeStack.length <= 1) {
        return;
      }
      if (pendingRouteTimer) {
        window.clearTimeout(pendingRouteTimer);
        pendingRouteTimer = null;
      }
      routeStack.pop();
      appState.settingsOverlay = "";
      appState.settingsExpandedOption = "";
      appState.mainTabFeedback = "";
      appState.readerMoreOpen = false;
      goTo(routeStack[routeStack.length - 1], false);
    }

    if (backButton) {
      backButton.addEventListener("click", goBack);
    }

    let initialMode = "regular";
    try {
      initialMode = window.localStorage.getItem("readerFrontendDemoMode") || "regular";
    } catch (error) {
      initialMode = "regular";
    }
    const initialRoute = (() => {
      try {
        const route = new URLSearchParams(window.location.search).get("captureRoute") || "bookshelf";
        return routes[route] ? route : "bookshelf";
      } catch (error) {
        return "bookshelf";
      }
    })();
    setDemoMode(initialMode);
    routeStack.splice(0, routeStack.length, ...initialRouteStackFor(initialRoute));
    goTo(initialRoute, false);
  }

  function attachScreenInteractions(screenHost, goTo, goBack, goTab, replaceTopRoute, exitReader, appState, data, renderCurrentRoute) {
    const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
    const roundTo = (value, digits) => Number(value.toFixed(digits));
    const applyReaderPageAction = (action) => {
      const pageCount = readerPages(data, appState).length;
      const currentIndex = Number.isFinite(Number(appState.readerPageIndex)) ? Number(appState.readerPageIndex) : 0;
      const nextIndex = action === "next"
        ? clamp(currentIndex + 1, 0, pageCount - 1)
        : action === "prev"
          ? clamp(currentIndex - 1, 0, pageCount - 1)
          : currentIndex;
      if (nextIndex === currentIndex) {
        return;
      }
      appState.readerPageIndex = nextIndex;
      appState.readerTurnDirection = action === "next" ? "next" : "prev";
      renderCurrentRoute();
    };
    const applyReaderChapterAction = (action) => {
      const chapters = readerChapters(data);
      const currentIndex = Number.isFinite(Number(appState.readerChapterIndex))
        ? Number(appState.readerChapterIndex)
        : initialReaderChapterIndex(data);
      const nextIndex = action === "next"
        ? clamp(currentIndex + 1, 0, chapters.length - 1)
        : action === "prev"
          ? clamp(currentIndex - 1, 0, chapters.length - 1)
          : currentIndex;
      if (nextIndex === currentIndex) {
        return;
      }
      const progressDelta = nextIndex > currentIndex ? 1 : -1;
      appState.readerChapterIndex = nextIndex;
      const chapterProgressConfig = readerChapterProgressConfig(data);
      appState.readerChapterProgress = clamp(readerChapterProgressValue(data, appState) + progressDelta, chapterProgressConfig.min, chapterProgressConfig.max);
      appState.readerPageIndex = 0;
      appState.readerTurnDirection = action === "next" ? "next" : "prev";
      renderCurrentRoute();
    };
    const applyReaderChapterProgress = (target, clientX, shouldRender) => {
      const chapterProgressConfig = readerChapterProgressConfig(data);
      const rect = target.getBoundingClientRect();
      const raw = rect.width > 0 ? chapterProgressConfig.min + ((clientX - rect.left) / rect.width) * (chapterProgressConfig.max - chapterProgressConfig.min) : chapterProgressConfig.min;
      const value = Math.round(clamp(raw, chapterProgressConfig.min, chapterProgressConfig.max));
      appState.readerChapterProgress = value;
      const pageCount = readerPages(data, appState).length;
      appState.readerPageIndex = clamp(Math.round(((value - chapterProgressConfig.min) / Math.max(1, chapterProgressConfig.max - chapterProgressConfig.min)) * Math.max(0, pageCount - 1)), 0, Math.max(0, pageCount - 1));
      target.style.setProperty("--progress", `${value}%`);
      target.setAttribute("aria-valuenow", String(value));
      if (shouldRender) {
        renderCurrentRoute();
      }
    };
    const readerBrightnessDim = (value) => {
      const brightnessConfig = readerBrightnessConfig(data);
      return Math.max(0, Math.min(0.32, (brightnessConfig.max - value) / 280));
    };
    const syncReaderBrightnessDom = (value, isAuto) => {
      const brightnessConfig = readerBrightnessConfig(data);
      const parsedBrightness = Number(value);
      const brightnessValue = Math.round(clamp(Number.isFinite(parsedBrightness) ? parsedBrightness : brightnessConfig.defaultValue, brightnessConfig.min, brightnessConfig.max));
      appState.readerBrightness = brightnessValue;
      appState.readerBrightnessAuto = Boolean(isAuto);
      screenHost.querySelectorAll(".fd-brightness-rail").forEach((rail) => {
        rail.style.setProperty("--brightness", `${brightnessValue}%`);
        const track = rail.querySelector("[data-reader-brightness-track]");
        if (track) {
          track.setAttribute("aria-valuenow", String(brightnessValue));
        }
        const autoButton = rail.querySelector("[data-reader-brightness-auto]");
        if (autoButton) {
          autoButton.classList.toggle("is-active", Boolean(isAuto));
          autoButton.setAttribute("aria-pressed", isAuto ? "true" : "false");
        }
      });
      screenHost.querySelectorAll("[data-reader-brightness-dim]").forEach((layer) => {
        layer.style.setProperty("--reader-brightness", `${brightnessValue}%`);
        layer.style.setProperty("--reader-brightness-dim", readerBrightnessDim(brightnessValue).toFixed(3));
      });
    };
    const applyReaderBrightnessTrack = (track, clientY) => {
      const brightnessConfig = readerBrightnessConfig(data);
      const rect = track.getBoundingClientRect();
      const raw = rect.height > 0 ? brightnessConfig.min + ((rect.bottom - clientY) / rect.height) * (brightnessConfig.max - brightnessConfig.min) : brightnessConfig.min;
      syncReaderBrightnessDom(raw, false);
    };
    const applyTypographyAction = (action) => {
      const typographyConfig = readerTypographyConfig(data);
      const typography = appState.readerTypography;
      const updateTypographyParam = (key, direction) => {
        const config = typographyConfig[key];
        const nextValue = clamp(Number(typography[key]) + direction * Number(config.step), Number(config.min), Number(config.max));
        typography[key] = Number(config.precision) > 0 ? roundTo(nextValue, Number(config.precision)) : Math.round(nextValue);
      };
      if (action === "font-size-decrease") updateTypographyParam("fontSize", -1);
      if (action === "font-size-increase") updateTypographyParam("fontSize", 1);
      if (action === "line-height-decrease") updateTypographyParam("lineHeight", -1);
      if (action === "line-height-increase") updateTypographyParam("lineHeight", 1);
      if (action === "paragraph-gap-decrease") updateTypographyParam("paragraphGap", -1);
      if (action === "paragraph-gap-increase") updateTypographyParam("paragraphGap", 1);
      if (action === "letter-spacing-decrease") updateTypographyParam("letterSpacing", -1);
      if (action === "letter-spacing-increase") updateTypographyParam("letterSpacing", 1);
      if (action === "reset") appState.readerTypography = normalizeReaderTypography(data);
      renderCurrentRoute();
    };
    const cycleValue = (current, values) => {
      const index = values.indexOf(current);
      return values[(index + 1) % values.length] || values[0];
    };
    const applyReaderDirectoryIndex = (rawIndex) => {
      const chapters = readerChapters(data);
      const parsedIndex = Number(rawIndex);
      const index = clamp(Number.isFinite(parsedIndex) ? parsedIndex : 0, 0, Math.max(0, chapters.length - 1));
      const chapterProgressConfig = readerChapterProgressConfig(data);
      appState.readerChapterIndex = index;
      appState.readerChapterProgress = clamp(Math.round(chapterProgressConfig.min + ((index + 1) / Math.max(1, chapters.length)) * (chapterProgressConfig.max - chapterProgressConfig.min)), chapterProgressConfig.min, chapterProgressConfig.max);
      appState.readerPageIndex = 0;
      appState.readerTurnDirection = "";
      replaceTopRoute("immersive-reading");
    };
    const applyReaderTtsAction = (action) => {
      const ttsConfig = readerTtsConfig(data);
      const tts = appState.readerTts;
      if (action === "toggle") tts.playing = !tts.playing;
      if (action === "prev") tts.sentenceIndex = clamp((tts.sentenceIndex || ttsConfig.defaults.sentenceIndex) - 1, ttsConfig.sentenceMin, ttsConfig.sentenceMax);
      if (action === "next") tts.sentenceIndex = clamp((tts.sentenceIndex || ttsConfig.defaults.sentenceIndex) + 1, ttsConfig.sentenceMin, ttsConfig.sentenceMax);
      renderCurrentRoute();
    };
    const applyReaderTtsCycle = (key) => {
      const options = readerTtsConfig(data).options;
      if (!options[key]) return;
      appState.readerTts[key] = cycleValue(appState.readerTts[key], options[key]);
      renderCurrentRoute();
    };
    const applyReaderTheme = (value) => {
      const options = readerThemeOptions(data);
      appState.readerTheme = options.some((item) => item.value === value) ? value : readerDefaultThemeValue(data);
      renderCurrentRoute();
    };
    const applyReaderSettingToggle = (key) => {
      if (!Object.prototype.hasOwnProperty.call(appState.readerSettings, key)) return;
      appState.readerSettings[key] = !appState.readerSettings[key];
      renderCurrentRoute();
    };
    const applyReaderSettingCycle = (key) => {
      const options = readerControlSettingsConfig(data).options;
      if (!options[key]) return;
      appState.readerSettings[key] = cycleValue(appState.readerSettings[key], options[key]);
      renderCurrentRoute();
    };

    const closeBookFocus = (phone) => {
      if (!phone) {
        return;
      }
      phone.classList.remove("has-book-focus");
      phone.querySelectorAll(".is-cover-focused").forEach((item) => item.classList.remove("is-cover-focused"));
      const layer = phone.querySelector("[data-book-focus-layer]");
      if (layer) {
        layer.setAttribute("aria-hidden", "true");
      }
    };

    const openBookFocus = (button) => {
      const phone = button.closest(".fd-phone");
      const layer = phone?.querySelector("[data-book-focus-layer]");
      if (!phone || !layer) {
        return;
      }
      closeBookFocus(phone);
      phone.classList.add("has-book-focus");
      const focusTarget = button.closest("[data-book-card]") || button;
      focusTarget.classList.add("is-cover-focused");
      const title = button.getAttribute("data-book-title") || "长夜余火";
      const author = button.getAttribute("data-book-author") || "爱潜水的乌贼";
      const chapter = button.getAttribute("data-book-chapter") || "第 32 章 雨夜";
      const coverSrc = button.getAttribute("data-cover-src") || "";
      const titleHost = layer.querySelector("[data-focus-title]");
      const metaHost = layer.querySelector("[data-focus-meta]");
      const coverHost = layer.querySelector("[data-focus-cover]");
      if (titleHost) {
        titleHost.textContent = title;
      }
      if (metaHost) {
        metaHost.textContent = `${author} · ${chapter}`;
      }
      if (coverHost) {
        coverHost.style.setProperty("--focus-cover", `url("${coverSrc}")`);
      }
      layer.setAttribute("aria-hidden", "false");
      layer.querySelector(".fd-book-focus-menu button")?.focus({ preventScroll: true });
    };

    const applyBookshelfView = (mode) => {
      const view = mode === "list" ? "list" : "cover";
      appState.bookshelfView = view;
      const grid = screenHost.querySelector("[data-book-grid]");
      if (grid) {
        grid.setAttribute("data-bookshelf-view", view);
        grid.setAttribute("aria-label", view === "list" ? "书籍列表" : "书籍封面网格");
        grid.classList.toggle("is-list-view", view === "list");
        grid.classList.toggle("is-cover-view", view === "cover");
      }
      screenHost.querySelectorAll("[data-bookshelf-view-button]").forEach((button) => {
        const active = button.getAttribute("data-bookshelf-view-button") === view;
        button.classList.toggle("is-active", active);
        button.setAttribute("aria-pressed", active ? "true" : "false");
      });
    };

    screenHost.querySelectorAll("[data-bookshelf-view-button]").forEach((button) => {
      button.addEventListener("click", () => applyBookshelfView(button.getAttribute("data-bookshelf-view-button")));
    });

    const closeBookshelfMore = (phone) => {
      const layer = phone?.querySelector("[data-bookshelf-more-layer]");
      if (layer) {
        layer.setAttribute("aria-hidden", "true");
      }
    };

    const currentRoute = () => screenHost.closest(".fd-demo")?.getAttribute("data-current-route") || "";
    const setMainTabFeedback = (message) => {
      appState.mainTabFeedback = message;
      renderCurrentRoute();
    };
    const handleTopAction = (button) => {
      const action = button.getAttribute("data-top-action") || button.getAttribute("aria-label") || "";
      const route = currentRoute();
      if (action === "search") {
        if (route === "bookshelf" || route === "discover") {
          appState.bookSearchPhase = "before";
          goTo("book-search", true);
          return;
        }
        if (route === "rss") {
          setMainTabFeedback("RSS 搜索入口已保留，完整 RSS 搜索页后续设计。");
          return;
        }
        if (route === "settings") {
          setMainTabFeedback("设置内搜索入口已保留，后续进入设置搜索页。");
          return;
        }
      }
      if (action === "more") {
        if (route === "bookshelf" || route === "bookshelf-empty") {
          const phone = button.closest(".fd-phone");
          const layer = phone?.querySelector("[data-bookshelf-more-layer]");
          if (layer) {
            layer.setAttribute("aria-hidden", "false");
            layer.querySelector(".fd-bookshelf-more-menu button")?.focus({ preventScroll: true });
          }
          return;
        }
        const messages = {
          discover: "发现更多入口已保留，来源选择、分类管理和发现设置后续设计。",
          rss: "RSS 更多入口已保留，订阅管理、添加订阅源和条目菜单后续设计。",
          settings: "设置更多入口已保留，导入导出和恢复默认必须进入后续二级流程。"
        };
        setMainTabFeedback(messages[route] || "更多入口已保留，当前页面暂不展开完整次级流程。");
        return;
      }
      if (action === "refresh") {
        setMainTabFeedback("刷新应发生在当前内容区，不替换 MainTabShell 顶部结构。");
      }
    };

    screenHost.querySelectorAll(".fd-main-tab-phone .fd-top-actions [data-top-action]").forEach((button) => {
      button.addEventListener("click", () => handleTopAction(button));
    });

    screenHost.querySelectorAll("[data-close-bookshelf-more]").forEach((button) => {
      button.addEventListener("click", () => closeBookshelfMore(button.closest(".fd-phone")));
    });

    screenHost.querySelectorAll("[data-search-submit]").forEach((button) => {
      button.addEventListener("click", () => {
        appState.bookSearchPhase = "after";
        renderCurrentRoute();
      });
    });

    screenHost.querySelectorAll("[data-search-reset]").forEach((button) => {
      button.addEventListener("click", () => {
        appState.bookSearchPhase = "before";
        renderCurrentRoute();
      });
    });

    screenHost.querySelectorAll("[data-add-search-shelf]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        const row = button.closest(".fd-search-result-row");
        const state = row?.querySelector(".fd-search-result-state");
        if (state) {
          state.textContent = "已在书架";
          state.classList.add("is-in-shelf");
        }
        button.textContent = "阅读";
        button.removeAttribute("data-add-search-shelf");
        button.setAttribute("data-route", "immersive-reading");
      });
    });

    const openSettingsOverlay = (trigger) => {
      const overlay = trigger.getAttribute("data-settings-overlay") || "";
      if (overlay === "edit" || overlay === "log") {
        const panel = screenHost.querySelector(`.fd-settings-subpanel.is-${overlay}`);
        if (panel) {
          panel.classList.add("is-focused");
          panel.scrollIntoView({ block: "center", behavior: "smooth" });
          window.setTimeout(() => panel.classList.remove("is-focused"), 720);
        }
        return;
      }
      if (overlay === "sheet" || overlay === "dialog") {
        appState.settingsOverlay = overlay;
        renderCurrentRoute();
      }
    };

    screenHost.querySelectorAll("[data-settings-overlay]").forEach((targetEl) => {
      targetEl.addEventListener("click", (event) => {
        event.preventDefault();
        openSettingsOverlay(targetEl);
      });
      targetEl.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          openSettingsOverlay(targetEl);
        }
      });
    });

    screenHost.querySelectorAll("[data-close-settings-overlay]").forEach((button) => {
      button.addEventListener("click", () => {
        appState.settingsOverlay = "";
        renderCurrentRoute();
      });
    });

    screenHost.querySelectorAll("[data-settings-option-key]").forEach((targetEl) => {
      const toggleOption = () => {
        const key = targetEl.getAttribute("data-settings-option-key") || "";
        appState.settingsOverlay = "";
        appState.settingsExpandedOption = appState.settingsExpandedOption === key ? "" : key;
        renderCurrentRoute();
      };
      targetEl.addEventListener("click", (event) => {
        event.preventDefault();
        toggleOption();
      });
      targetEl.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          toggleOption();
        }
      });
    });

    screenHost.querySelectorAll("[data-settings-option-choice]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        const key = button.getAttribute("data-settings-option-choice") || "";
        const value = button.getAttribute("data-settings-option-value") || "";
        appState.settingsValues[key] = value;
        appState.settingsExpandedOption = "";
        appState.settingsOverlay = "";
        renderCurrentRoute();
      });
    });

    screenHost.querySelectorAll("[data-reader-more-toggle]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        appState.readerMoreOpen = !appState.readerMoreOpen;
        renderCurrentRoute();
      });
    });

    screenHost.querySelectorAll("[data-reader-more-close], [data-reader-more-action]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        appState.readerMoreOpen = false;
        renderCurrentRoute();
      });
    });

    screenHost.querySelectorAll("[data-route]").forEach((targetEl) => {
      if (targetEl.hasAttribute("data-book-cover")) {
        return;
      }
      const navigate = () => {
        const route = targetEl.getAttribute("data-route");
        const shouldReplaceRoute = targetEl.hasAttribute("data-route-replace") || Boolean(targetEl.closest(".fd-source-control-continuity"));
        if (targetEl.classList.contains("fd-reader-module") && route === currentRoute()) {
          replaceTopRoute("reader");
          return;
        }
        if (route === "book-search") {
          appState.bookSearchPhase = "before";
        }
        closeBookshelfMore(targetEl.closest(".fd-phone"));
        if (shouldReplaceRoute) {
          replaceTopRoute(route);
          return;
        }
        goTo(route, true);
      };
      targetEl.addEventListener("click", navigate);
      targetEl.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          navigate();
        }
      });
    });

    screenHost.querySelectorAll("[data-book-cover]").forEach((button) => {
      let longPressTimer = null;
      let longPressTriggered = false;
      const clearLongPress = () => {
        if (longPressTimer) {
          window.clearTimeout(longPressTimer);
          longPressTimer = null;
        }
      };
      button.addEventListener("pointerdown", (event) => {
        if (event.button && event.button !== 0) {
          return;
        }
        longPressTriggered = false;
        clearLongPress();
        longPressTimer = window.setTimeout(() => {
          longPressTriggered = true;
          openBookFocus(button);
        }, 560);
      });
      button.addEventListener("pointerup", clearLongPress);
      button.addEventListener("pointercancel", clearLongPress);
      button.addEventListener("pointerleave", clearLongPress);
      button.addEventListener("contextmenu", (event) => {
        event.preventDefault();
        longPressTriggered = true;
        openBookFocus(button);
      });
      button.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        clearLongPress();
        if (longPressTriggered) {
          longPressTriggered = false;
          return;
        }
        closeBookFocus(button.closest(".fd-phone"));
        goTo(button.getAttribute("data-route") || "immersive-reading", true);
      });
      button.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          closeBookFocus(button.closest(".fd-phone"));
          goTo(button.getAttribute("data-route") || "immersive-reading", true);
        }
        if (event.key === " ") {
          event.preventDefault();
          openBookFocus(button);
        }
      });
    });

    screenHost.querySelectorAll("[data-close-book-focus]").forEach((button) => {
      button.addEventListener("click", () => closeBookFocus(button.closest(".fd-phone")));
    });

    screenHost.querySelectorAll("[data-book-focus-layer]").forEach((layer) => {
      layer.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
          closeBookFocus(layer.closest(".fd-phone"));
        }
      });
    });

    screenHost.querySelectorAll("[data-route-back], .fd-back-bar button[aria-label='返回']").forEach((button) => {
      button.addEventListener("click", goBack);
    });

    screenHost.querySelectorAll("[data-reader-dismiss]").forEach((button) => {
      button.addEventListener("click", () => replaceTopRoute(button.getAttribute("data-reader-dismiss") || "immersive-reading"));
    });

    screenHost.querySelectorAll("[data-reader-exit]").forEach((button) => {
      button.addEventListener("click", exitReader);
    });

    screenHost.querySelectorAll("[data-reader-toc-mode]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        appState.readerTocMode = button.getAttribute("data-reader-toc-mode") === "bookmark" ? "bookmark" : "directory";
        renderCurrentRoute();
      });
    });

    screenHost.querySelectorAll("[data-reader-directory-index]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        applyReaderDirectoryIndex(button.getAttribute("data-reader-directory-index"));
      });
    });

    screenHost.querySelectorAll("[data-reader-tts-action]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        applyReaderTtsAction(button.getAttribute("data-reader-tts-action"));
      });
    });

    screenHost.querySelectorAll("[data-reader-tts-cycle]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        applyReaderTtsCycle(button.getAttribute("data-reader-tts-cycle"));
      });
    });

    screenHost.querySelectorAll("[data-reader-theme]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        applyReaderTheme(button.getAttribute("data-reader-theme"));
      });
    });

    screenHost.querySelectorAll("[data-reader-typography-action]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        applyTypographyAction(button.getAttribute("data-reader-typography-action"));
      });
    });

    screenHost.querySelectorAll("[data-reader-typography-set]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        const key = button.getAttribute("data-reader-typography-set");
        if (key === "fontFamily") {
          appState.readerTypography.fontFamily = button.getAttribute("data-reader-typography-value") || readerDefaultFontValue(data);
          renderCurrentRoute();
        }
      });
    });

    screenHost.querySelectorAll("[data-reader-brightness-auto]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        appState.readerBrightnessAuto = !appState.readerBrightnessAuto;
        syncReaderBrightnessDom(appState.readerBrightness, appState.readerBrightnessAuto);
      });
    });

    screenHost.querySelectorAll("[data-reader-brightness-track]").forEach((track) => {
      track.addEventListener("pointerdown", (event) => {
        event.preventDefault();
        event.stopPropagation();
        track.setPointerCapture?.(event.pointerId);
        applyReaderBrightnessTrack(track, event.clientY);
        const move = (moveEvent) => applyReaderBrightnessTrack(track, moveEvent.clientY);
        const done = (doneEvent) => {
          track.releasePointerCapture?.(doneEvent.pointerId);
          track.removeEventListener("pointermove", move);
          track.removeEventListener("pointerup", done);
          track.removeEventListener("pointercancel", done);
        };
        track.addEventListener("pointermove", move);
        track.addEventListener("pointerup", done);
        track.addEventListener("pointercancel", done);
      });
      track.addEventListener("keydown", (event) => {
        const brightnessConfig = readerBrightnessConfig(data);
        const current = readerBrightnessValue(data, appState);
        if (event.key === "ArrowDown" || event.key === "ArrowLeft") {
          event.preventDefault();
          syncReaderBrightnessDom(current - brightnessConfig.step, false);
        }
        if (event.key === "ArrowUp" || event.key === "ArrowRight") {
          event.preventDefault();
          syncReaderBrightnessDom(current + brightnessConfig.step, false);
        }
      });
    });

    screenHost.querySelectorAll("[data-reader-setting-toggle]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        applyReaderSettingToggle(button.getAttribute("data-reader-setting-toggle"));
      });
    });

    screenHost.querySelectorAll("[data-reader-setting-cycle]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        applyReaderSettingCycle(button.getAttribute("data-reader-setting-cycle"));
      });
    });

    screenHost.querySelectorAll("[data-reader-page-action]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        applyReaderPageAction(button.getAttribute("data-reader-page-action"));
      });
    });

    screenHost.querySelectorAll("[data-reader-chapter-action]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        if (button.getAttribute("aria-disabled") === "true") {
          return;
        }
        applyReaderChapterAction(button.getAttribute("data-reader-chapter-action"));
      });
    });

    screenHost.querySelectorAll("[data-reader-chapter-progress]").forEach((progress) => {
      progress.addEventListener("pointerdown", (event) => {
        event.preventDefault();
        progress.setPointerCapture?.(event.pointerId);
        applyReaderChapterProgress(progress, event.clientX, false);
        const move = (moveEvent) => applyReaderChapterProgress(progress, moveEvent.clientX, false);
        const done = (doneEvent) => {
          progress.releasePointerCapture?.(doneEvent.pointerId);
          progress.removeEventListener("pointermove", move);
          progress.removeEventListener("pointerup", done);
          progress.removeEventListener("pointercancel", done);
          renderCurrentRoute();
        };
        progress.addEventListener("pointermove", move);
        progress.addEventListener("pointerup", done);
        progress.addEventListener("pointercancel", done);
      });
      progress.addEventListener("keydown", (event) => {
        const chapterProgressConfig = readerChapterProgressConfig(data);
        const current = readerChapterProgressValue(data, appState);
        if (event.key === "ArrowLeft" || event.key === "ArrowDown") {
          event.preventDefault();
          appState.readerChapterProgress = clamp(current - chapterProgressConfig.step, chapterProgressConfig.min, chapterProgressConfig.max);
          renderCurrentRoute();
        }
        if (event.key === "ArrowRight" || event.key === "ArrowUp") {
          event.preventDefault();
          appState.readerChapterProgress = clamp(current + chapterProgressConfig.step, chapterProgressConfig.min, chapterProgressConfig.max);
          renderCurrentRoute();
        }
      });
    });

    screenHost.querySelectorAll(".fd-main-tab-phone .fd-main-nav-item").forEach((button) => {
      button.addEventListener("click", () => {
        const type = button.getAttribute("data-nav-type");
        const route = type === "settings" ? "settings" : type;
        goTab(route);
      });
    });

    screenHost.querySelectorAll("[data-open-keyboard]").forEach((button) => {
      button.addEventListener("click", () => {
        const phone = button.closest(".fd-phone");
        phone.classList.add("has-keyboard");
        const keyboard = phone.querySelector("[data-keyboard-host]");
        const input = phone.querySelector("[data-keyboard-input]");
        if (keyboard) {
          keyboard.setAttribute("aria-hidden", "false");
        }
        if (input) {
          const focusInput = () => {
            input.focus({ preventScroll: true });
            input.setSelectionRange(input.value.length, input.value.length);
          };
          focusInput();
          window.setTimeout(focusInput, 30);
          window.setTimeout(focusInput, 120);
        }
      });
    });

    screenHost.querySelectorAll("[data-close-keyboard]").forEach((button) => {
      button.addEventListener("click", () => {
        const phone = button.closest(".fd-phone");
        phone.classList.remove("has-keyboard");
        const keyboard = phone.querySelector("[data-keyboard-host]");
        if (keyboard) {
          keyboard.setAttribute("aria-hidden", "true");
        }
      });
    });

    screenHost.querySelectorAll("[data-open-sheet]").forEach((button) => {
      button.addEventListener("click", () => {
        const phone = button.closest(".fd-phone");
        phone.classList.add("has-sheet");
        const sheet = phone.querySelector("[data-demo-sheet]");
        if (sheet) {
          sheet.setAttribute("aria-hidden", "false");
        }
      });
    });

    screenHost.querySelectorAll("[data-close-sheet]").forEach((button) => {
      button.addEventListener("click", () => {
        const phone = button.closest(".fd-phone");
        phone.classList.remove("has-sheet");
        const sheet = phone.querySelector("[data-demo-sheet]");
        if (sheet) {
          sheet.setAttribute("aria-hidden", "true");
        }
      });
    });

    screenHost.querySelectorAll("[data-open-dialog]").forEach((button) => {
      button.addEventListener("click", () => {
        const phone = button.closest(".fd-phone");
        phone.classList.add("has-dialog");
        const dialog = phone.querySelector("[data-demo-dialog]");
        if (dialog) {
          dialog.setAttribute("aria-hidden", "false");
        }
      });
    });

    screenHost.querySelectorAll("[data-close-dialog]").forEach((button) => {
      button.addEventListener("click", () => {
        const phone = button.closest(".fd-phone");
        phone.classList.remove("has-dialog");
        const dialog = phone.querySelector("[data-demo-dialog]");
        if (dialog) {
          dialog.setAttribute("aria-hidden", "true");
        }
      });
    });

    screenHost.querySelectorAll(".fd-flow-comparison article").forEach((card) => {
      const selectSource = () => {
        const flow = card.closest(".fd-flow-frame");
        const source = card.getAttribute("data-source-name") || card.querySelector("strong")?.textContent || "";
        flow.querySelectorAll(".fd-flow-comparison article").forEach((item) => {
          item.classList.toggle("is-selected", item === card);
        });
        const result = flow.querySelector(".fd-flow-result p");
        if (result) {
          result.textContent = `目标书源${source}章节一致，可保留 38% 阅读进度。`;
        }
      };
      card.addEventListener("click", selectSource);
      card.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          selectSource();
        }
      });
    });
  }

  window.ReaderFrontendDemoDraft = {
    render
  };
})(window);
