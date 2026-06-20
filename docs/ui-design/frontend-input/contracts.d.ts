export type PercentValue = number;

export interface StatusBarInput {
  time: string;
  battery?: string;
}

export interface NavItemInput<TType extends string = string> {
  label: string;
  active: boolean;
  type?: TType;
}

export type MainNavType = "bookshelf" | "discover" | "rss" | "settings";
export type MainNavLabel = "书架" | "发现" | "RSS" | "设置";

export interface MainNavItemInput {
  label: MainNavLabel;
  active: boolean;
  type: MainNavType;
}

export interface BookshelfGroupInput {
  label: string;
  active: boolean;
}

export interface BookshelfBookInput {
  title: string;
  author: string;
  chapter: string;
  progress: PercentValue;
  cover: string;
}

export interface ContinueReadingInput extends BookshelfBookInput {
  progressLabel: string;
}

export interface RecentUpdateInput {
  title: string;
  chapter: string;
  cover: string;
  unread: boolean;
}

export interface BookshelfFixture {
  status: StatusBarInput;
  topBar: {
    title: string;
  };
  groups: BookshelfGroupInput[];
  continueReading: ContinueReadingInput | null;
  recentUpdates: RecentUpdateInput[];
  books: BookshelfBookInput[];
  bottomNav: MainNavItemInput[];
}

export type BookshelfState = "default" | "filtering" | "loading" | "empty";

export type BookshelfEvent =
  | { type: "search" }
  | { type: "more" }
  | { type: "groupChange"; label: string }
  | { type: "read"; book: BookshelfBookInput }
  | { type: "sortFilter" }
  | { type: "settings" }
  | { type: "openBook"; book: BookshelfBookInput }
  | { type: "navChange"; navType: "bookshelf" | "discover" | "rss" | "settings" };

export interface ReaderStatusInput {
  left: string;
  time: string;
}

export interface ReaderQuickActionInput {
  label: string;
  type: "search" | "auto-page" | "replace";
}

export interface ReaderChapterProgressInput {
  title: string;
  progressLabel: string;
  progress: PercentValue;
  previousLabel: string;
  nextLabel: string;
}

export interface ReaderModuleInput {
  label: string;
  type: "directory" | "tts" | "appearance" | "settings";
}

export interface ReaderBrightnessInput {
  title: string;
  modeLabel: string;
  autoLabel: string;
  value: PercentValue;
}

export interface ReaderControlFixture {
  status: ReaderStatusInput;
  topControl: {
    bookTitle: string;
    sourceLine: string;
    sourceActionLabel: string;
  };
  readingText: string[];
  quickActions: ReaderQuickActionInput[];
  chapterProgress: ReaderChapterProgressInput;
  moduleNav: ReaderModuleInput[];
  brightness: ReaderBrightnessInput;
  bottomReadout: {
    progress: string;
    chapter: string;
  };
}

export type ReaderControlState = "" | "directory" | "tts" | "appearance" | "settings";

export type ReaderControlEvent =
  | { type: "back" }
  | { type: "sourceChange" }
  | { type: "more" }
  | { type: "quickAction"; actionType: ReaderQuickActionInput["type"] }
  | { type: "chapterChange"; direction: "previous" | "next" }
  | { type: "progressChange"; value: PercentValue }
  | { type: "moduleChange"; moduleType: ReaderModuleInput["type"] }
  | { type: "brightnessChange"; value: PercentValue };

export type SettingsTone = "blue" | "orange" | "green" | "purple";

export interface SettingsOverviewItemInput {
  label: string;
  value: string;
  icon: string;
  tone: SettingsTone;
}

export interface SettingsQuickEntryInput {
  title: string;
  meta: string;
  icon: string;
  tone: SettingsTone;
  target: string;
}

export interface SettingsListItemInput {
  title: string;
  meta: string;
  icon: string;
  tone: SettingsTone;
  state: "" | "可用" | "未设置" | "待授权" | "需要处理";
}

export interface SettingsSectionInput {
  title: string;
  items: SettingsListItemInput[];
}

export interface SettingsHomeFixture {
  status: StatusBarInput;
  topBar: {
    title: string;
  };
  overview: {
    title: string;
    items: SettingsOverviewItemInput[];
  };
  quickEntries: SettingsQuickEntryInput[];
  sections: SettingsSectionInput[];
  bottomNav: MainNavItemInput[];
}

export type SettingsHomeState = "default" | "loading-overview" | "no-backup" | "permission-needed";

export type SettingsHomeEvent =
  | { type: "search" }
  | { type: "more" }
  | { type: "quickEntry"; target: string }
  | { type: "openSetting"; section: string; item: string }
  | { type: "navChange"; navType: "bookshelf" | "discover" | "rss" | "settings" };

export interface BookshelfEmptyGroupInput {
  label: string;
  active: boolean;
}

export interface BookshelfEmptyStateInput {
  title: string;
  copy: string;
  illustrationLabel?: string;
  primaryAction: string;
  secondaryAction: string;
  hint: string;
}

export interface BookshelfEmptyFixture {
  status: {
    time: string;
  };
  topBar: {
    title: string;
  };
  groups: BookshelfEmptyGroupInput[];
  emptyState: BookshelfEmptyStateInput;
  variants: {
    allEmpty: BookshelfEmptyStateInput;
    loading: BookshelfEmptyStateInput;
    error: BookshelfEmptyStateInput;
    offline: BookshelfEmptyStateInput;
    permission: BookshelfEmptyStateInput;
  };
  bottomNav: MainNavItemInput[];
}

export type BookshelfEmptyState = "default" | "all-empty" | "loading" | "error" | "offline" | "permission";

export type BookshelfEmptyEvent =
  | { type: "search" }
  | { type: "more" }
  | { type: "groupChange"; label: string }
  | { type: "primaryAction"; state: BookshelfEmptyState }
  | { type: "secondaryAction"; state: BookshelfEmptyState }
  | { type: "navChange"; navType: "bookshelf" | "discover" | "rss" | "settings" };

export type ReaderLibraryIconType =
  | "search"
  | "more"
  | "back"
  | "sort"
  | "settings"
  | "bookshelf"
  | "discover"
  | "rss"
  | "auto-page"
  | "replace"
  | "directory"
  | "tts"
  | "appearance"
  | "source"
  | "storage"
  | "sync";

export interface ReaderLibraryColorToken {
  name: string;
  value: string;
}

export interface ReaderLibraryRowInput {
  title: string;
  meta: string;
  value: string;
  icon: ReaderLibraryIconType;
}

export interface ReaderLibraryActionInput {
  label: string;
  type: ReaderLibraryIconType;
  active?: boolean;
}

export interface ReaderLibraryBadgeInput {
  label: string;
  tone: "info" | "warning" | "muted";
}

export interface ReaderLibraryStateInput {
  title: string;
  copy: string;
}

export interface ReaderComponentLibraryFixture {
  meta: {
    title: string;
    summary: string;
    version: string;
  };
  colors: ReaderLibraryColorToken[];
  chips: string[];
  bottomNav: MainNavItemInput[];
  book: BookshelfBookInput;
  rows: ReaderLibraryRowInput[];
  quickActions: ReaderLibraryActionInput[];
  moduleNav: ReaderLibraryActionInput[];
  states: ReaderLibraryStateInput[];
}

export interface FrontendInputValidationTarget {
  name: string;
  html: string;
  viewport: {
    width: number;
    height: number;
  };
  selector: string;
  requiredText: string[];
  rendererGlobal: string;
  expectedFrame?: {
    width: number;
    height: number;
  };
  expectedStateCards?: number;
  expectedImages?: {
    min: number;
    missing: number;
  };
  expectedDomCount?: {
    selector: string;
    min: number;
  };
}
