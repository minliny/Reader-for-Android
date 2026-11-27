// GENERATED FILE — DO NOT EDIT.
// Generator: scripts/generate-protocol-bindings.mjs
// Inputs and SHA-256 fingerprints:
//   protocol/reader-command.schema.json  ee756bfe7985af16beb3712fd9449ce5a690374285ec300b853a1a7c4d5177bb
//   protocol/reader-event.schema.json  42a2f08a71f67f30e55bda18ca129651e02ed841769255c284822f5ad08ea56b
//   protocol/reader-runtime-config.schema.json  7f80c3a548f71ab2b5d4415af83579f03c725cb0bec0fc72a1e7dc6e91368afa
// Package: com.reader.core.protocol (new namespace; no conflict with the
// hand-written com.reader.core JNI wrapper).
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.reader.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Every command method declared by reader-command.schema.json (242). */
@Serializable
public enum class ReaderCommandMethod(public val wireValue: String) {
    @SerialName("book-highlight.create") BOOK_HIGHLIGHT_CREATE("book-highlight.create"),
    @SerialName("book-highlight.update") BOOK_HIGHLIGHT_UPDATE("book-highlight.update"),
    @SerialName("book.chapterReview") BOOK_CHAPTER_REVIEW("book.chapterReview"),
    @SerialName("book.detail") BOOK_DETAIL("book.detail"),
    @SerialName("book.search") BOOK_SEARCH("book.search"),
    @SerialName("book.toc") BOOK_TOC("book.toc"),
    @SerialName("bookmark.create") BOOKMARK_CREATE("bookmark.create"),
    @SerialName("bookmark.delete") BOOKMARK_DELETE("bookmark.delete"),
    @SerialName("bookmark.list") BOOKMARK_LIST("bookmark.list"),
    @SerialName("bookmark.operation.query") BOOKMARK_OPERATION_QUERY("bookmark.operation.query"),
    @SerialName("bookmark.operation.settle") BOOKMARK_OPERATION_SETTLE("bookmark.operation.settle"),
    @SerialName("bookmark.update") BOOKMARK_UPDATE("bookmark.update"),
    @SerialName("bookshelf.add") BOOKSHELF_ADD("bookshelf.add"),
    @SerialName("bookshelf.book.update") BOOKSHELF_BOOK_UPDATE("bookshelf.book.update"),
    @SerialName("bookshelf.get") BOOKSHELF_GET("bookshelf.get"),
    @SerialName("bookshelf.group.assign") BOOKSHELF_GROUP_ASSIGN("bookshelf.group.assign"),
    @SerialName("bookshelf.list") BOOKSHELF_LIST("bookshelf.list"),
    @SerialName("bookshelf.management.mutation.apply") BOOKSHELF_MANAGEMENT_MUTATION_APPLY("bookshelf.management.mutation.apply"),
    @SerialName("bookshelf.management.mutation.query") BOOKSHELF_MANAGEMENT_MUTATION_QUERY("bookshelf.management.mutation.query"),
    @SerialName("bookshelf.remove") BOOKSHELF_REMOVE("bookshelf.remove"),
    @SerialName("bookshelf.removeBatch") BOOKSHELF_REMOVE_BATCH("bookshelf.removeBatch"),
    @SerialName("cache.book.prefetch") CACHE_BOOK_PREFETCH("cache.book.prefetch"),
    @SerialName("cache.book.status") CACHE_BOOK_STATUS("cache.book.status"),
    @SerialName("cache.chapter.materialization.report") CACHE_CHAPTER_MATERIALIZATION_REPORT("cache.chapter.materialization.report"),
    @SerialName("cache.clear") CACHE_CLEAR("cache.clear"),
    @SerialName("cache.clear.operation.query") CACHE_CLEAR_OPERATION_QUERY("cache.clear.operation.query"),
    @SerialName("cache.clear.operation.settle") CACHE_CLEAR_OPERATION_SETTLE("cache.clear.operation.settle"),
    @SerialName("cache.download.cancel") CACHE_DOWNLOAD_CANCEL("cache.download.cancel"),
    @SerialName("cache.download.operation.query") CACHE_DOWNLOAD_OPERATION_QUERY("cache.download.operation.query"),
    @SerialName("cache.download.operation.settle") CACHE_DOWNLOAD_OPERATION_SETTLE("cache.download.operation.settle"),
    @SerialName("cache.download.pause") CACHE_DOWNLOAD_PAUSE("cache.download.pause"),
    @SerialName("cache.download.resume") CACHE_DOWNLOAD_RESUME("cache.download.resume"),
    @SerialName("cache.storage.cleanup") CACHE_STORAGE_CLEANUP("cache.storage.cleanup"),
    @SerialName("cache.storage.summary") CACHE_STORAGE_SUMMARY("cache.storage.summary"),
    @SerialName("change.bookSource") CHANGE_BOOK_SOURCE("change.bookSource"),
    @SerialName("change.cover") CHANGE_COVER("change.cover"),
    @SerialName("chapter.content") CHAPTER_CONTENT("chapter.content"),
    @SerialName("content-edit.delete") CONTENT_EDIT_DELETE("content-edit.delete"),
    @SerialName("content-edit.get") CONTENT_EDIT_GET("content-edit.get"),
    @SerialName("content-edit.list") CONTENT_EDIT_LIST("content-edit.list"),
    @SerialName("content-edit.put") CONTENT_EDIT_PUT("content-edit.put"),
    @SerialName("core.info") CORE_INFO("core.info"),
    @SerialName("dict-rule.delete") DICT_RULE_DELETE("dict-rule.delete"),
    @SerialName("dict-rule.list") DICT_RULE_LIST("dict-rule.list"),
    @SerialName("dict-rule.put") DICT_RULE_PUT("dict-rule.put"),
    @SerialName("dict-rule.query") DICT_RULE_QUERY("dict-rule.query"),
    @SerialName("host.complete") HOST_COMPLETE("host.complete"),
    @SerialName("host.error") HOST_ERROR("host.error"),
    @SerialName("import.finalize") IMPORT_FINALIZE("import.finalize"),
    @SerialName("import.parse") IMPORT_PARSE("import.parse"),
    @SerialName("import.persist") IMPORT_PERSIST("import.persist"),
    @SerialName("import.rollback") IMPORT_ROLLBACK("import.rollback"),
    @SerialName("local.book.positionArchive.list") LOCAL_BOOK_POSITION_ARCHIVE_LIST("local.book.positionArchive.list"),
    @SerialName("local_book.catalog") LOCAL_BOOK_CATALOG("local_book.catalog"),
    @SerialName("local_book.chapter.content") LOCAL_BOOK_CHAPTER_CONTENT("local_book.chapter.content"),
    @SerialName("local_book.chapter.presentation.v1") LOCAL_BOOK_CHAPTER_PRESENTATION_V1("local_book.chapter.presentation.v1"),
    @SerialName("local_book.content.metrics") LOCAL_BOOK_CONTENT_METRICS("local_book.content.metrics"),
    @SerialName("local_book.import") LOCAL_BOOK_IMPORT("local_book.import"),
    @SerialName("local_book.navigation.backfill.v1") LOCAL_BOOK_NAVIGATION_BACKFILL_V1("local_book.navigation.backfill.v1"),
    @SerialName("local_book.parse") LOCAL_BOOK_PARSE("local_book.parse"),
    @SerialName("local_book.presentation.kind.v1") LOCAL_BOOK_PRESENTATION_KIND_V1("local_book.presentation.kind.v1"),
    @SerialName("local_book.toc") LOCAL_BOOK_TOC("local_book.toc"),
    @SerialName("manga.cache.classify") MANGA_CACHE_CLASSIFY("manga.cache.classify"),
    @SerialName("manga.cache.classifyBatch") MANGA_CACHE_CLASSIFY_BATCH("manga.cache.classifyBatch"),
    @SerialName("manga.cache.evict") MANGA_CACHE_EVICT("manga.cache.evict"),
    @SerialName("manga.cache.eviction.claim") MANGA_CACHE_EVICTION_CLAIM("manga.cache.eviction.claim"),
    @SerialName("manga.cache.eviction.complete") MANGA_CACHE_EVICTION_COMPLETE("manga.cache.eviction.complete"),
    @SerialName("manga.cache.eviction.pending") MANGA_CACHE_EVICTION_PENDING("manga.cache.eviction.pending"),
    @SerialName("manga.cache.materialization.abort") MANGA_CACHE_MATERIALIZATION_ABORT("manga.cache.materialization.abort"),
    @SerialName("manga.cache.materialization.complete") MANGA_CACHE_MATERIALIZATION_COMPLETE("manga.cache.materialization.complete"),
    @SerialName("manga.cache.materialization.prepare") MANGA_CACHE_MATERIALIZATION_PREPARE("manga.cache.materialization.prepare"),
    @SerialName("manga.cache.recordRead") MANGA_CACHE_RECORD_READ("manga.cache.recordRead"),
    @SerialName("manga.chapter.get") MANGA_CHAPTER_GET("manga.chapter.get"),
    @SerialName("manga.entry.get") MANGA_ENTRY_GET("manga.entry.get"),
    @SerialName("manga.pages.extract") MANGA_PAGES_EXTRACT("manga.pages.extract"),
    @SerialName("manga.pages.window") MANGA_PAGES_WINDOW("manga.pages.window"),
    @SerialName("manga.resource.inspect") MANGA_RESOURCE_INSPECT("manga.resource.inspect"),
    @SerialName("manga.resource.prepare") MANGA_RESOURCE_PREPARE("manga.resource.prepare"),
    @SerialName("manga.retention.get") MANGA_RETENTION_GET("manga.retention.get"),
    @SerialName("manga.retention.list") MANGA_RETENTION_LIST("manga.retention.list"),
    @SerialName("manga.retention.set") MANGA_RETENTION_SET("manga.retention.set"),
    @SerialName("manga.session.protect") MANGA_SESSION_PROTECT("manga.session.protect"),
    @SerialName("manga.session.release") MANGA_SESSION_RELEASE("manga.session.release"),
    @SerialName("manga.session.replace") MANGA_SESSION_REPLACE("manga.session.replace"),
    @SerialName("manga.session.runtime") MANGA_SESSION_RUNTIME("manga.session.runtime"),
    @SerialName("manga.sourceSwitch.commit") MANGA_SOURCE_SWITCH_COMMIT("manga.sourceSwitch.commit"),
    @SerialName("manga.sourceSwitch.commit.v2") MANGA_SOURCE_SWITCH_COMMIT_V2("manga.sourceSwitch.commit.v2"),
    @SerialName("manga.sourceSwitch.operation.query.v1") MANGA_SOURCE_SWITCH_OPERATION_QUERY_V1("manga.sourceSwitch.operation.query.v1"),
    @SerialName("read-record.accumulate") READ_RECORD_ACCUMULATE("read-record.accumulate"),
    @SerialName("read-record.accumulate-receipt") READ_RECORD_ACCUMULATE_RECEIPT("read-record.accumulate-receipt"),
    @SerialName("read-record.clear") READ_RECORD_CLEAR("read-record.clear"),
    @SerialName("read-record.create") READ_RECORD_CREATE("read-record.create"),
    @SerialName("read-record.delete") READ_RECORD_DELETE("read-record.delete"),
    @SerialName("read-record.deleteByBook") READ_RECORD_DELETE_BY_BOOK("read-record.deleteByBook"),
    @SerialName("read-record.list") READ_RECORD_LIST("read-record.list"),
    @SerialName("read-record.summary") READ_RECORD_SUMMARY("read-record.summary"),
    @SerialName("read-record.update") READ_RECORD_UPDATE("read-record.update"),
    @SerialName("reader.chinese-conversion.get") READER_CHINESE_CONVERSION_GET("reader.chinese-conversion.get"),
    @SerialName("reader.chinese-conversion.put") READER_CHINESE_CONVERSION_PUT("reader.chinese-conversion.put"),
    @SerialName("reader.location.resolve") READER_LOCATION_RESOLVE("reader.location.resolve"),
    @SerialName("reading.catalog.page") READING_CATALOG_PAGE("reading.catalog.page"),
    @SerialName("reading.directory.rules.apply.v2") READING_DIRECTORY_RULES_APPLY_V2("reading.directory.rules.apply.v2"),
    @SerialName("reading.directory.rules.get.v2") READING_DIRECTORY_RULES_GET_V2("reading.directory.rules.get.v2"),
    @SerialName("reading.directory.rules.prepare.v2") READING_DIRECTORY_RULES_PREPARE_V2("reading.directory.rules.prepare.v2"),
    @SerialName("reading.directory.rules.preview.v2") READING_DIRECTORY_RULES_PREVIEW_V2("reading.directory.rules.preview.v2"),
    @SerialName("reading.directory.target.resolve.v2") READING_DIRECTORY_TARGET_RESOLVE_V2("reading.directory.target.resolve.v2"),
    @SerialName("reading.directory.view.open.v1") READING_DIRECTORY_VIEW_OPEN_V1("reading.directory.view.open.v1"),
    @SerialName("reading.directory.view.open.v2") READING_DIRECTORY_VIEW_OPEN_V2("reading.directory.view.open.v2"),
    @SerialName("reading.directory.view.page.v1") READING_DIRECTORY_VIEW_PAGE_V1("reading.directory.view.page.v1"),
    @SerialName("reading.directory.view.page.v2") READING_DIRECTORY_VIEW_PAGE_V2("reading.directory.view.page.v2"),
    @SerialName("reading.directory.view.target.resolve.v1") READING_DIRECTORY_VIEW_TARGET_RESOLVE_V1("reading.directory.view.target.resolve.v1"),
    @SerialName("reading.document.window") READING_DOCUMENT_WINDOW("reading.document.window"),
    @SerialName("reading.entry.prepare") READING_ENTRY_PREPARE("reading.entry.prepare"),
    @SerialName("reading.entry.snapshot") READING_ENTRY_SNAPSHOT("reading.entry.snapshot"),
    @SerialName("reading.preparation") READING_PREPARATION("reading.preparation"),
    @SerialName("reading.progress.get") READING_PROGRESS_GET("reading.progress.get"),
    @SerialName("reading.progress.update") READING_PROGRESS_UPDATE("reading.progress.update"),
    @SerialName("replace-rule.create") REPLACE_RULE_CREATE("replace-rule.create"),
    @SerialName("replace-rule.delete") REPLACE_RULE_DELETE("replace-rule.delete"),
    @SerialName("replace-rule.list") REPLACE_RULE_LIST("replace-rule.list"),
    @SerialName("replace-rule.update") REPLACE_RULE_UPDATE("replace-rule.update"),
    @SerialName("replace.apply") REPLACE_APPLY("replace.apply"),
    @SerialName("replace.persist") REPLACE_PERSIST("replace.persist"),
    @SerialName("replace.preview") REPLACE_PREVIEW("replace.preview"),
    @SerialName("replace.undo") REPLACE_UNDO("replace.undo"),
    @SerialName("replace.validate") REPLACE_VALIDATE("replace.validate"),
    @SerialName("rss-source.export") RSS_SOURCE_EXPORT("rss-source.export"),
    @SerialName("rss-source.import") RSS_SOURCE_IMPORT("rss-source.import"),
    @SerialName("rss-source.list") RSS_SOURCE_LIST("rss-source.list"),
    @SerialName("rss-source.put") RSS_SOURCE_PUT("rss-source.put"),
    @SerialName("rss.favorite.list") RSS_FAVORITE_LIST("rss.favorite.list"),
    @SerialName("rss.item.read") RSS_ITEM_READ("rss.item.read"),
    @SerialName("rss.list") RSS_LIST("rss.list"),
    @SerialName("rss.parse") RSS_PARSE("rss.parse"),
    @SerialName("rss.refresh") RSS_REFRESH("rss.refresh"),
    @SerialName("rss.subscription.add") RSS_SUBSCRIPTION_ADD("rss.subscription.add"),
    @SerialName("rss.subscription.create") RSS_SUBSCRIPTION_CREATE("rss.subscription.create"),
    @SerialName("rss.subscription.delete") RSS_SUBSCRIPTION_DELETE("rss.subscription.delete"),
    @SerialName("rss.subscription.items") RSS_SUBSCRIPTION_ITEMS("rss.subscription.items"),
    @SerialName("rss.subscription.list") RSS_SUBSCRIPTION_LIST("rss.subscription.list"),
    @SerialName("rss.subscription.refresh") RSS_SUBSCRIPTION_REFRESH("rss.subscription.refresh"),
    @SerialName("rss.subscription.update") RSS_SUBSCRIPTION_UPDATE("rss.subscription.update"),
    @SerialName("rule-bundle.export") RULE_BUNDLE_EXPORT("rule-bundle.export"),
    @SerialName("rule-bundle.import") RULE_BUNDLE_IMPORT("rule-bundle.import"),
    @SerialName("rule-sub.delete") RULE_SUB_DELETE("rule-sub.delete"),
    @SerialName("rule-sub.get") RULE_SUB_GET("rule-sub.get"),
    @SerialName("rule-sub.list") RULE_SUB_LIST("rule-sub.list"),
    @SerialName("rule-sub.put") RULE_SUB_PUT("rule-sub.put"),
    @SerialName("rule-sub.update.preview.v1") RULE_SUB_UPDATE_PREVIEW_V1("rule-sub.update.preview.v1"),
    @SerialName("rules.mutation.apply") RULES_MUTATION_APPLY("rules.mutation.apply"),
    @SerialName("rules.mutation.query") RULES_MUTATION_QUERY("rules.mutation.query"),
    @SerialName("runtime.cancel") RUNTIME_CANCEL("runtime.cancel"),
    @SerialName("runtime.hostSmoke") RUNTIME_HOST_SMOKE("runtime.hostSmoke"),
    @SerialName("runtime.ping") RUNTIME_PING("runtime.ping"),
    @SerialName("runtime.setHostCapabilities") RUNTIME_SET_HOST_CAPABILITIES("runtime.setHostCapabilities"),
    @SerialName("runtime.shutdown") RUNTIME_SHUTDOWN("runtime.shutdown"),
    @SerialName("runtime.status") RUNTIME_STATUS("runtime.status"),
    @SerialName("runtime.storage.apply") RUNTIME_STORAGE_APPLY("runtime.storage.apply"),
    @SerialName("runtime.storage.backup.create") RUNTIME_STORAGE_BACKUP_CREATE("runtime.storage.backup.create"),
    @SerialName("runtime.storage.backup.restore") RUNTIME_STORAGE_BACKUP_RESTORE("runtime.storage.backup.restore"),
    @SerialName("runtime.storage.export") RUNTIME_STORAGE_EXPORT("runtime.storage.export"),
    @SerialName("runtime.storage.flush") RUNTIME_STORAGE_FLUSH("runtime.storage.flush"),
    @SerialName("runtime.storage.restore") RUNTIME_STORAGE_RESTORE("runtime.storage.restore"),
    @SerialName("search-book.batch.get") SEARCH_BOOK_BATCH_GET("search-book.batch.get"),
    @SerialName("search-book.delete") SEARCH_BOOK_DELETE("search-book.delete"),
    @SerialName("search-book.get") SEARCH_BOOK_GET("search-book.get"),
    @SerialName("search-book.list") SEARCH_BOOK_LIST("search-book.list"),
    @SerialName("search-book.put") SEARCH_BOOK_PUT("search-book.put"),
    @SerialName("search-book.related") SEARCH_BOOK_RELATED("search-book.related"),
    @SerialName("search-keyword.delete") SEARCH_KEYWORD_DELETE("search-keyword.delete"),
    @SerialName("search-keyword.get") SEARCH_KEYWORD_GET("search-keyword.get"),
    @SerialName("search-keyword.list") SEARCH_KEYWORD_LIST("search-keyword.list"),
    @SerialName("search-keyword.put") SEARCH_KEYWORD_PUT("search-keyword.put"),
    @SerialName("search.content") SEARCH_CONTENT("search.content"),
    @SerialName("search.cover") SEARCH_COVER("search.cover"),
    @SerialName("search.history.add") SEARCH_HISTORY_ADD("search.history.add"),
    @SerialName("search.history.clear") SEARCH_HISTORY_CLEAR("search.history.clear"),
    @SerialName("search.history.list") SEARCH_HISTORY_LIST("search.history.list"),
    @SerialName("source.check") SOURCE_CHECK("source.check"),
    @SerialName("source.check.ephemeral.run") SOURCE_CHECK_EPHEMERAL_RUN("source.check.ephemeral.run"),
    @SerialName("source.check.report.clear") SOURCE_CHECK_REPORT_CLEAR("source.check.report.clear"),
    @SerialName("source.check.run") SOURCE_CHECK_RUN("source.check.run"),
    @SerialName("source.debug") SOURCE_DEBUG("source.debug"),
    @SerialName("source.delete") SOURCE_DELETE("source.delete"),
    @SerialName("source.explore") SOURCE_EXPLORE("source.explore"),
    @SerialName("source.exploreKinds") SOURCE_EXPLORE_KINDS("source.exploreKinds"),
    @SerialName("source.export") SOURCE_EXPORT("source.export"),
    @SerialName("source.group.catalog") SOURCE_GROUP_CATALOG("source.group.catalog"),
    @SerialName("source.imageRequest") SOURCE_IMAGE_REQUEST("source.imageRequest"),
    @SerialName("source.import") SOURCE_IMPORT("source.import"),
    @SerialName("source.list") SOURCE_LIST("source.list"),
    @SerialName("source.supply") SOURCE_SUPPLY("source.supply"),
    @SerialName("source.switch.commit") SOURCE_SWITCH_COMMIT("source.switch.commit"),
    @SerialName("source.switch.finalize") SOURCE_SWITCH_FINALIZE("source.switch.finalize"),
    @SerialName("source.switch.pending.list") SOURCE_SWITCH_PENDING_LIST("source.switch.pending.list"),
    @SerialName("source.switch.recover") SOURCE_SWITCH_RECOVER("source.switch.recover"),
    @SerialName("source.switch.rollback") SOURCE_SWITCH_ROLLBACK("source.switch.rollback"),
    @SerialName("source.switch.transaction.query") SOURCE_SWITCH_TRANSACTION_QUERY("source.switch.transaction.query"),
    @SerialName("source.update") SOURCE_UPDATE("source.update"),
    @SerialName("sync.backup") SYNC_BACKUP("sync.backup"),
    @SerialName("sync.backup.auto.decision") SYNC_BACKUP_AUTO_DECISION("sync.backup.auto.decision"),
    @SerialName("sync.backup.content.plan") SYNC_BACKUP_CONTENT_PLAN("sync.backup.content.plan"),
    @SerialName("sync.backup.restore.scope.plan") SYNC_BACKUP_RESTORE_SCOPE_PLAN("sync.backup.restore.scope.plan"),
    @SerialName("sync.backup.retention") SYNC_BACKUP_RETENTION("sync.backup.retention"),
    @SerialName("sync.conflict.resolve") SYNC_CONFLICT_RESOLVE("sync.conflict.resolve"),
    @SerialName("sync.merge") SYNC_MERGE("sync.merge"),
    @SerialName("sync.webdav.backup.receipt.query") SYNC_WEBDAV_BACKUP_RECEIPT_QUERY("sync.webdav.backup.receipt.query"),
    @SerialName("sync.webdav.backup.retention.skip") SYNC_WEBDAV_BACKUP_RETENTION_SKIP("sync.webdav.backup.retention.skip"),
    @SerialName("sync.webdav.directory.list.plan") SYNC_WEBDAV_DIRECTORY_LIST_PLAN("sync.webdav.directory.list.plan"),
    @SerialName("sync.webdav.multistatus.parse") SYNC_WEBDAV_MULTISTATUS_PARSE("sync.webdav.multistatus.parse"),
    @SerialName("sync.webdav.plan") SYNC_WEBDAV_PLAN("sync.webdav.plan"),
    @SerialName("sync.webdav.progress.merge") SYNC_WEBDAV_PROGRESS_MERGE("sync.webdav.progress.merge"),
    @SerialName("sync.webdav.progress.push.plan") SYNC_WEBDAV_PROGRESS_PUSH_PLAN("sync.webdav.progress.push.plan"),
    @SerialName("sync.webdav.remote-book.local-import.plan") SYNC_WEBDAV_REMOTE_BOOK_LOCAL_IMPORT_PLAN("sync.webdav.remote-book.local-import.plan"),
    @SerialName("sync.webdav.remote-book.plan") SYNC_WEBDAV_REMOTE_BOOK_PLAN("sync.webdav.remote-book.plan"),
    @SerialName("sync.webdav.remote-book.result") SYNC_WEBDAV_REMOTE_BOOK_RESULT("sync.webdav.remote-book.result"),
    @SerialName("sync.webdav.transaction.abort") SYNC_WEBDAV_TRANSACTION_ABORT("sync.webdav.transaction.abort"),
    @SerialName("sync.webdav.transaction.advance") SYNC_WEBDAV_TRANSACTION_ADVANCE("sync.webdav.transaction.advance"),
    @SerialName("sync.webdav.transaction.commit") SYNC_WEBDAV_TRANSACTION_COMMIT("sync.webdav.transaction.commit"),
    @SerialName("sync.webdav.transaction.resolve") SYNC_WEBDAV_TRANSACTION_RESOLVE("sync.webdav.transaction.resolve"),
    @SerialName("sync.webdav.transaction.start") SYNC_WEBDAV_TRANSACTION_START("sync.webdav.transaction.start"),
    @SerialName("tts.chapter.plan") TTS_CHAPTER_PLAN("tts.chapter.plan"),
    @SerialName("tts.config.get") TTS_CONFIG_GET("tts.config.get"),
    @SerialName("tts.config.put") TTS_CONFIG_PUT("tts.config.put"),
    @SerialName("tts.queue.next") TTS_QUEUE_NEXT("tts.queue.next"),
    @SerialName("tts.queue.pause") TTS_QUEUE_PAUSE("tts.queue.pause"),
    @SerialName("tts.queue.play") TTS_QUEUE_PLAY("tts.queue.play"),
    @SerialName("tts.queue.prev") TTS_QUEUE_PREV("tts.queue.prev"),
    @SerialName("tts.queue.report-callback") TTS_QUEUE_REPORT_CALLBACK("tts.queue.report-callback"),
    @SerialName("tts.queue.report-status") TTS_QUEUE_REPORT_STATUS("tts.queue.report-status"),
    @SerialName("tts.queue.resume") TTS_QUEUE_RESUME("tts.queue.resume"),
    @SerialName("tts.queue.seek") TTS_QUEUE_SEEK("tts.queue.seek"),
    @SerialName("tts.queue.set-rate") TTS_QUEUE_SET_RATE("tts.queue.set-rate"),
    @SerialName("tts.queue.skip") TTS_QUEUE_SKIP("tts.queue.skip"),
    @SerialName("tts.queue.status") TTS_QUEUE_STATUS("tts.queue.status"),
    @SerialName("tts.queue.stop") TTS_QUEUE_STOP("tts.queue.stop"),
    @SerialName("tts.slice") TTS_SLICE("tts.slice"),
    @SerialName("txt-toc-rule.create") TXT_TOC_RULE_CREATE("txt-toc-rule.create"),
    @SerialName("txt-toc-rule.delete") TXT_TOC_RULE_DELETE("txt-toc-rule.delete"),
    @SerialName("txt-toc-rule.list") TXT_TOC_RULE_LIST("txt-toc-rule.list"),
    @SerialName("txt-toc-rule.preview") TXT_TOC_RULE_PREVIEW("txt-toc-rule.preview"),
    @SerialName("txt-toc-rule.update") TXT_TOC_RULE_UPDATE("txt-toc-rule.update"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ReaderCommandMethod> =
            values().associateBy(ReaderCommandMethod::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ReaderCommandMethod? = BY_WIRE[value]
    }
}

/** Host capabilities declared by reader-command.schema.json (16). */
@Serializable
public enum class ReaderHostCapability(public val wireValue: String) {
    @SerialName("cache.get") CACHE_GET("cache.get"),
    @SerialName("cache.put") CACHE_PUT("cache.put"),
    @SerialName("cookie.get") COOKIE_GET("cookie.get"),
    @SerialName("cookie.set") COOKIE_SET("cookie.set"),
    @SerialName("file.read") FILE_READ("file.read"),
    @SerialName("file.write") FILE_WRITE("file.write"),
    @SerialName("host.smoke.echo") HOST_SMOKE_ECHO("host.smoke.echo"),
    @SerialName("http.execute") HTTP_EXECUTE("http.execute"),
    @SerialName("log.emit") LOG_EMIT("log.emit"),
    @SerialName("manga.resource.transfer") MANGA_RESOURCE_TRANSFER("manga.resource.transfer"),
    @SerialName("media.download") MEDIA_DOWNLOAD("media.download"),
    @SerialName("persistence.get") PERSISTENCE_GET("persistence.get"),
    @SerialName("persistence.put") PERSISTENCE_PUT("persistence.put"),
    @SerialName("system.info") SYSTEM_INFO("system.info"),
    @SerialName("time.now") TIME_NOW("time.now"),
    @SerialName("webview.evaluateJavaScript") WEBVIEW_EVALUATE_JAVA_SCRIPT("webview.evaluateJavaScript"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ReaderHostCapability> =
            values().associateBy(ReaderHostCapability::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ReaderHostCapability? = BY_WIRE[value]
    }
}

/** ErrorCode vocabulary from reader-event.schema.json (11). */
@Serializable
public enum class ReaderErrorCode(public val wireValue: String) {
    @SerialName("BOOK_ALREADY_ON_SHELF") BOOK_ALREADY_ON_SHELF("BOOK_ALREADY_ON_SHELF"),
    @SerialName("BUSY") BUSY("BUSY"),
    @SerialName("CANCELLED") CANCELLED("CANCELLED"),
    @SerialName("INTERNAL") INTERNAL("INTERNAL"),
    @SerialName("INVALID_MESSAGE") INVALID_MESSAGE("INVALID_MESSAGE"),
    @SerialName("INVALID_PARAMS") INVALID_PARAMS("INVALID_PARAMS"),
    @SerialName("INVALID_PROTOCOL_VERSION") INVALID_PROTOCOL_VERSION("INVALID_PROTOCOL_VERSION"),
    @SerialName("RESOURCE_EXHAUSTED") RESOURCE_EXHAUSTED("RESOURCE_EXHAUSTED"),
    @SerialName("TIMEOUT") TIMEOUT("TIMEOUT"),
    @SerialName("TRANSACTION_PENDING") TRANSACTION_PENDING("TRANSACTION_PENDING"),
    @SerialName("UNKNOWN_METHOD") UNKNOWN_METHOD("UNKNOWN_METHOD"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ReaderErrorCode> =
            values().associateBy(ReaderErrorCode::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ReaderErrorCode? = BY_WIRE[value]
    }
}

/** Advertised feature-gate names (x-reader-core-v1-capabilities).
 * These are capability gates, not 1:1 method names; keep them as raw wire strings. */
public object ReaderAdvertisedCapabilities {
    public val names: List<String> = listOf(
        "core.info",
        "runtime.ping",
        "runtime.hostSmoke",
        "runtime.cancel",
        "runtime.status",
        "runtime.storage.restore",
        "runtime.storage.flush",
        "runtime.storage.export",
        "runtime.storage.apply",
        "runtime.shutdown",
        "runtime.setHostCapabilities",
        "host.complete",
        "host.error",
        "manga.chapter.v1",
        "manga.resource.prepare.v1",
        "manga.offline.v1",
        "manga.entry.v1",
        "manga.sourceSwitch.receipt.v1",
        "host.bus.v1",
        "http.execute",
        "runtime.config.v1",
        "remote.reading.v1",
        "rss.v1",
        "sync.v1",
        "local_book.v1",
        "bookshelf.v1",
        "search.content.cursor.v1",
        "chapter.content.cacheOnly.v1",
        "reading.document.window.v1",
        "reading.progress.compareAndSet.v1",
        "reading.progress.sourceFinalizeReceipt.v1",
        "reading.catalog.page.v1",
        "reading.directory.view.v1",
        "reading.directory.view.v2",
        "local_book.navigation.v1",
        "bookshelf.pageProjection.v1",
        "bookshelf.anchorPage.v1",
        "bookshelf.contentKindFilter.v1",
        "reading.preparation.v1",
        "reading.entry.prepare.v1",
        "reading.entry.localImageStyle.v1",
        "reading.entry.snapshot.v1",
        "reading.entry.firstFrame.v1",
        "reading.entry.snapshot.window.v1",
        "source.supply.v1",
        "source.list.byId.v1",
        "source.check.mode.v1",
        "host.http.timeout.v1",
        "source.check.report.v1",
        "source.check.report.v2",
        "rules.mutation.exact.v1",
        "rules.mutation.subscription.v1",
        "rules.mutation.conversion.v1",
        "bookshelf.managementMutation.v1",
        "rules.mutation.source-management.v1",
        "source.group.metadata.v1",
        "rules.mutation.source-groups.v1",
        "bookshelf.removalMutation.v1",
        "rules.mutation.rss-management.v1",
        "rules.mutation.directory-rules.v1",
        "library.metadataRestore.status.v1",
        "library.metadataRestore.apply.v1",
        "library.metadataExport.v1",
        "library.metadataInspect.v1",
        "sync.webdav.restoreSelection.v1",
        "local_book.originalExpectation.v1",
        "library.resourceBundle.v1",
        "library.resourceRestore.v1",
        "runtime.storage.backup.create",
        "runtime.storage.backup.restore",
        "source.check.keyword_override.v1",
        "source.check.ephemeral.v1",
        "source.check.report.clear.v1",
    )
}

/** Method -> result data $ref name, from x-reader-core-v1-result-data-defs. */
public object ReaderResultDataDefs {
    public val defByMethod: Map<String, String> = mapOf(
        "book-highlight.create" to "BookHighlightCreateData",
        "book-highlight.list" to "BookHighlightListData",
        "book-highlight.update" to "BookHighlightUpdateData",
        "bookmark.create" to "BookmarkCreateData",
        "bookmark.delete" to "BookmarkDeleteData",
        "bookmark.list" to "BookmarkListData",
        "bookmark.operation.query" to "BookmarkOperationQueryData",
        "bookmark.operation.settle" to "BookmarkOperationReceipt",
        "bookmark.update" to "BookmarkUpdateData",
        "bookshelf.add" to "BookshelfAddData",
        "bookshelf.book.update" to "BookshelfBookUpdateData",
        "bookshelf.get" to "BookshelfGetData",
        "bookshelf.list" to "BookshelfListData",
        "bookshelf.management.mutation.apply" to "BookshelfManagementReceipt",
        "bookshelf.management.mutation.query" to "BookshelfManagementQueryData",
        "bookshelf.remove" to "BookshelfRemoveData",
        "bookshelf.removeBatch" to "BookshelfRemoveBatchData",
        "cache.book.prefetch" to "CacheBookPrefetchData",
        "cache.book.status" to "CacheBookStatusData",
        "cache.chapter.materialization.report" to "CacheChapterMaterializationReportData",
        "cache.clear" to "CacheClearData",
        "cache.clear.operation.query" to "CacheClearOperationQueryData",
        "cache.clear.operation.settle" to "CacheClearOperationReceipt",
        "cache.download.cancel" to "CacheDownloadTaskResult",
        "cache.download.operation.query" to "DownloadOperationQueryData",
        "cache.download.operation.settle" to "DownloadOperationReceipt",
        "cache.download.pause" to "CacheDownloadTaskResult",
        "cache.download.resume" to "CacheDownloadTaskResult",
        "cache.storage.cleanup" to "CacheStorageData",
        "cache.storage.summary" to "CacheStorageData",
        "chapter.content" to "ChapterContentData",
        "core.info" to "CoreInfoData",
        "local.book.positionArchive.list" to "LocalBookPositionArchiveListData",
        "local_book.chapter.content" to "LocalBookChapterContentData",
        "local_book.chapter.presentation.v1" to "LocalBookChapterPresentationData",
        "local_book.content.metrics" to "LocalBookContentMetricsData",
        "local_book.navigation.backfill.v1" to "LocalBookNavigationBackfillData",
        "local_book.presentation.kind.v1" to "LocalBookPresentationKindData",
        "local_book.toc" to "LocalBookTocData",
        "manga.cache.classify" to "MangaCacheClassifyResult",
        "manga.cache.classifyBatch" to "MangaCacheClassifyBatchResult",
        "manga.cache.evict" to "MangaCacheEvictResult",
        "manga.cache.eviction.claim" to "MangaCacheEvictionClaimResult",
        "manga.cache.eviction.complete" to "MangaCacheEvictionCompleteResult",
        "manga.cache.eviction.pending" to "MangaCacheEvictionPendingResult",
        "manga.cache.materialization.abort" to "MangaCacheMaterializationAbortResult",
        "manga.cache.materialization.complete" to "MangaCacheMaterializationCompleteResult",
        "manga.cache.materialization.prepare" to "MangaCacheMaterializationPrepareResult",
        "manga.cache.recordRead" to "MangaCacheRecordReadResult",
        "manga.chapter.get" to "MangaChapterData",
        "manga.entry.get" to "MangaEntryData",
        "manga.pages.window" to "MangaChapterWindow",
        "manga.resource.inspect" to "MangaResourceInspectData",
        "manga.resource.prepare" to "MangaResourcePrepareData",
        "manga.retention.get" to "MangaRetentionSetResult",
        "manga.retention.list" to "MangaRetentionListResult",
        "manga.retention.set" to "MangaRetentionSetResult",
        "manga.session.protect" to "MangaSessionProtectResult",
        "manga.session.release" to "MangaSessionReleaseResult",
        "manga.session.replace" to "MangaSessionReplaceResult",
        "manga.session.runtime" to "MangaSessionRuntimeResult",
        "manga.sourceSwitch.commit" to "MangaSourceSwitchData",
        "manga.sourceSwitch.commit.v2" to "MangaSourceSwitchOperationReceipt",
        "manga.sourceSwitch.operation.query.v1" to "MangaSourceSwitchOperationQueryData",
        "read-record.accumulate" to "ReadRecordAccumulateData",
        "read-record.accumulate-receipt" to "ReadRecordAccumulateReceiptData",
        "read-record.clear" to "ReadRecordClearData",
        "read-record.create" to "ReadRecordCreateData",
        "read-record.delete" to "ReadRecordDeleteData",
        "read-record.deleteByBook" to "ReadRecordDeleteByBookData",
        "read-record.list" to "ReadRecordListData",
        "read-record.summary" to "ReadRecordSummaryData",
        "read-record.update" to "ReadRecordUpdateData",
        "reading.directory.rules.apply.v2" to "DirectoryRulesDataV2",
        "reading.directory.rules.get.v2" to "DirectoryRulesDataV2",
        "reading.directory.rules.prepare.v2" to "DirectoryRulesDataV2",
        "reading.directory.rules.preview.v2" to "DirectoryRulesDataV2",
        "reading.directory.target.resolve.v2" to "DirectoryTargetResolveDataV2",
        "reading.directory.view.open.v1" to "DirectoryViewOpenData",
        "reading.directory.view.open.v2" to "DirectoryViewOpenDataV2",
        "reading.directory.view.page.v1" to "DirectoryViewPageData",
        "reading.directory.view.page.v2" to "DirectoryViewPageDataV2",
        "reading.directory.view.target.resolve.v1" to "DirectoryViewTargetResolveData",
        "reading.progress.get" to "ReadingProgressGetData",
        "reading.progress.update" to "ReadingProgressUpdateData",
        "replace.apply" to "ReplaceApplyData",
        "replace.persist" to "ReplacePersistData",
        "replace.preview" to "ReplacePreviewData",
        "replace.undo" to "ReplaceUndoData",
        "replace.validate" to "ReplaceValidateData",
        "rules.mutation.apply" to "RulesMutationReceipt",
        "rules.mutation.query" to "RulesMutationQueryData",
        "runtime.setHostCapabilities" to "RuntimeSetHostCapabilitiesData",
        "runtime.status" to "RuntimeStatusData",
        "runtime.storage.backup.create" to "RuntimeStorageBackupCreateData",
        "runtime.storage.backup.restore" to "RuntimeStorageBackupRestoreData",
        "search-book.batch.get" to "SearchBookBatchGetData",
        "search-book.related" to "SearchBookRelatedData",
        "search.content" to "SearchContentData",
        "source.check.ephemeral.run" to "SourceCheckResultData",
        "source.check.report.clear" to "SourceCheckReportClearData",
        "source.check.run" to "SourceCheckResultData",
        "source.group.catalog" to "SourceGroupCatalogData",
        "source.switch.commit" to "SourceSwitchCommitData",
        "source.switch.finalize" to "SourceSwitchFinalizeData",
        "source.switch.pending.list" to "SourceSwitchPendingListData",
        "source.switch.recover" to "SourceSwitchRecoverData",
        "source.switch.rollback" to "SourceSwitchRollbackData",
        "source.switch.transaction.query" to "SourceSwitchTransactionQueryData",
        "sync.backup.auto.decision" to "SyncBackupAutoDecisionData",
        "sync.backup.content.plan" to "SyncBackupContentPlanData",
        "sync.backup.restore.scope.plan" to "SyncBackupRestoreScopePlanData",
        "sync.webdav.backup.receipt.query" to "SyncWebDavBackupReceiptData",
        "sync.webdav.backup.retention.skip" to "SyncWebDavBackupReceiptData",
        "sync.webdav.directory.list.plan" to "SyncWebDavDirectoryListPlanData",
        "sync.webdav.multistatus.parse" to "SyncWebDavMultistatusParseData",
        "sync.webdav.progress.merge" to "SyncWebDavProgressMergeData",
        "sync.webdav.progress.push.plan" to "SyncWebDavProgressPushPlanData",
        "sync.webdav.remote-book.local-import.plan" to "SyncWebDavRemoteBookLocalImportPlanData",
        "sync.webdav.remote-book.plan" to "SyncWebDavRemoteBookPlanData",
        "sync.webdav.remote-book.result" to "SyncWebDavRemoteBookResultData",
        "sync.webdav.transaction.abort" to "SyncWebDavTransactionData",
        "sync.webdav.transaction.advance" to "SyncWebDavTransactionData",
        "sync.webdav.transaction.commit" to "SyncWebDavTransactionData",
        "sync.webdav.transaction.resolve" to "SyncWebDavTransactionData",
        "sync.webdav.transaction.start" to "SyncWebDavTransactionData",
    )
}

/** Structured Core error carried by `error` events. */
@Serializable
public data class ReaderCoreError(
    public val code: ReaderErrorCode,
    public val message: String,
    public val retryable: Boolean,
    public val details: JsonObject? = null,
)

/** Command envelope. `params` stays a JsonObject because v1 is method-polymorphic;
 * typed bodies are published below and selected by ReaderCommandMethod. */
@Serializable
public data class ReaderCommandEnvelope(
    public val protocolVersion: Int = 1,
    public val requestId: Long,
    public val method: ReaderCommandMethod,
    public val params: JsonObject? = null,
)

/** Event envelopes from reader-event.schema.json (`oneOf` over 3 shapes). */
@Serializable
public data class ReaderResultEvent(
    public val deliveryId: Long? = null,
    public val protocolVersion: Int,
    public val requestId: Long,
    public val type: String = "result",
    public val data: JsonObject,
)

@Serializable
public data class ReaderErrorEvent(
    public val deliveryId: Long? = null,
    public val protocolVersion: Int,
    public val requestId: Long,
    public val type: String = "error",
    public val error: ReaderCoreError,
)

@Serializable
public data class ReaderHostRequestEvent(
    public val deliveryId: Long? = null,
    public val protocolVersion: Int,
    public val requestId: Long,
    public val type: String = "host.request",
    public val operationId: Long,
    public val capability: ReaderHostCapability,
    public val params: JsonObject,
)

/** Event kinds declared by the schema: result, error, host.request. */
public object ReaderEventKinds {
    public val wires: List<String> = listOf("result", "error", "host.request")
}

/** Storage format selection for a fresh runtime directory. */
@Serializable
public enum class ReaderFreshStorageFormat(public val wireValue: String) {
    @SerialName("library47") LIBRARY47("library47"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ReaderFreshStorageFormat> =
            values().associateBy(ReaderFreshStorageFormat::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ReaderFreshStorageFormat? = BY_WIRE[value]
    }
}

/** Runtime config (reader-runtime-config.schema.json). */
@Serializable
public data class ReaderRuntimeConfig(
    public val dataDirectory: String? = null,
    public val cacheDirectory: String? = null,
    public val storageMigration: ReaderStorageMigration? = null,
    public val freshStorageFormat: ReaderFreshStorageFormat? = null,
)

@Serializable
public data class ReaderStorageMigration(
    public val sourceSchemaVersion: Long,
    public val targetSchemaVersion: Long,
)

// Command params models: 230 definitions.
/** Params schema `BookChapterReviewParams` (7 declared properties). */
@Serializable
public data class BookChapterReviewParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterUrl: String? = null,
    public val reviewResponse: String? = null,
    public val reviewRequest: JsonElement? = null,
    public val source: JsonElement? = null,
    public val reviewUrl: String? = null,
)

/** Params schema `BookDetailParams` (6 declared properties). */
@Serializable
public data class BookDetailParams(
    public val sourceId: String,
    public val book: JsonElement,
    public val preparationRevision: Long? = null,
    public val detailResponse: String? = null,
    public val detailRequest: JsonElement? = null,
    public val source: JsonElement? = null,
)

/** Params schema `BookHighlightCreateParams` (14 declared properties). */
@Serializable
public data class BookHighlightCreateParams(
    public val time: JsonElement? = null,
    public val bookUrl: String? = null,
    public val chapterUrl: String? = null,
    public val bookName: String? = null,
    public val bookAuthor: String? = null,
    public val chapterIndex: Long? = null,
    public val chapterPos: Long? = null,
    public val chapterPosEnd: Long? = null,
    public val layoutTitleLength: Long? = null,
    public val chapterName: String? = null,
    public val bookText: String? = null,
    public val style: String? = null,
    public val note: String? = null,
    public val positionScope: JsonElement? = null,
)

/** Params schema `BookHighlightUpdateParams` (14 declared properties). */
@Serializable
public data class BookHighlightUpdateParams(
    public val time: Long,
    public val bookUrl: JsonElement? = null,
    public val chapterUrl: JsonElement? = null,
    public val bookName: JsonElement? = null,
    public val bookAuthor: JsonElement? = null,
    public val chapterIndex: JsonElement? = null,
    public val chapterPos: JsonElement? = null,
    public val chapterPosEnd: JsonElement? = null,
    public val layoutTitleLength: JsonElement? = null,
    public val chapterName: JsonElement? = null,
    public val bookText: JsonElement? = null,
    public val style: JsonElement? = null,
    public val note: JsonElement? = null,
    public val positionScope: JsonElement? = null,
)

/** Params schema `BookSearchParams` (6 declared properties). */
@Serializable
public data class BookSearchParams(
    public val sourceId: String,
    public val searchResponse: String? = null,
    public val searchRequest: JsonElement? = null,
    public val source: JsonElement? = null,
    public val keyword: String? = null,
    public val page: Long? = null,
)

/** Params schema `BookTocParams` (8 declared properties). */
@Serializable
public data class BookTocParams(
    public val sourceId: String,
    public val bookId: String,
    public val preparationRevision: Long? = null,
    public val tocResponse: String? = null,
    public val tocRequest: JsonElement? = null,
    public val source: JsonElement? = null,
    public val tocUrl: String? = null,
    public val variables: JsonElement? = null,
)

/** Params schema `BookshelfGroupAssignParams` (3 declared properties). */
@Serializable
public data class BookshelfGroupAssignParams(
    public val sourceId: String,
    public val bookId: String,
    public val group: String,
)

/** Params schema `BookshelfManagementApplyParams` (3 declared properties). */
@Serializable
public data class BookshelfManagementApplyParams(
    public val operationId: String,
    public val sequence: Long,
    public val mutation: JsonElement,
)

/** Params schema `BookshelfManagementQueryParams` (2 declared properties). */
@Serializable
public data class BookshelfManagementQueryParams(
    public val operationId: JsonElement? = null,
    public val expected: JsonElement? = null,
)

/** Params schema `CacheBookPrefetchParams` (9 declared properties). */
@Serializable
public data class CacheBookPrefetchParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterRange: List<JsonElement>,
    public val preparationRevision: Long? = null,
    public val priority: Long? = null,
    public val requestedAt: Long? = null,
    public val contentKind: String? = null,
    public val operationId: String? = null,
    public val sequence: Long? = null,
)

/** Params schema `CacheBookStatusParams` (2 declared properties). */
@Serializable
public data class CacheBookStatusParams(
    public val sourceId: String,
    public val bookId: String,
)

/** Params schema `CacheChapterMaterializationReportParams` (10 declared properties). */
@Serializable
public data class CacheChapterMaterializationReportParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val token: String,
    public val outcome: String,
    public val errorCode: String? = null,
    public val reportedAt: Long? = null,
    public val manga: JsonElement? = null,
    public val operationId: String? = null,
    public val sequence: Long? = null,
)

/** Params schema `CacheClearOperationQueryParams` (2 declared properties). */
@Serializable
public data class CacheClearOperationQueryParams(
    public val operationId: JsonElement? = null,
    public val expected: JsonElement? = null,
)

/** Params schema `CacheClearParams` has no declared properties. */
@Serializable
public object CacheClearParams

/** Params schema `CacheDownloadTaskParams` (6 declared properties). */
@Serializable
public data class CacheDownloadTaskParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val now: Long? = null,
    public val operationId: String? = null,
    public val sequence: Long? = null,
)

/** Params schema `CacheStorageParams` (10 declared properties). */
@Serializable
public data class CacheStorageParams(
    public val retainCompletedQueue: Boolean? = null,
    public val retainedChapters: List<JsonElement>? = null,
    public val protectedChapters: List<JsonElement>? = null,
    public val reason: String? = null,
    public val nowSeconds: JsonElement? = null,
    public val ttlSeconds: JsonElement? = null,
    public val capacityBytes: JsonElement? = null,
    public val hostImageChapters: List<JsonElement>? = null,
    public val completedQueueAfter: JsonElement? = null,
    public val contentScope: String? = null,
)

/** Params schema `ChangeBookSourceParams` (4 declared properties). */
@Serializable
public data class ChangeBookSourceParams(
    public val sourceId: String,
    public val bookId: String,
    public val sourceIds: List<String>,
    public val keyword: String? = null,
)

/** Params schema `ChangeCoverParams` (3 declared properties). */
@Serializable
public data class ChangeCoverParams(
    public val sourceId: String,
    public val bookId: String,
    public val sourceIds: List<String>,
)

/** Params schema `ChapterContentParams` (16 declared properties). */
@Serializable
public data class ChapterContentParams(
    public val sourceId: String,
    public val bookId: String,
    public val directoryTargetProof: JsonElement? = null,
    public val preparationRevision: Long? = null,
    public val cacheOnly: Boolean? = null,
    public val forceRefresh: Boolean? = null,
    public val chapterTitle: String? = null,
    public val chapterIndex: Long? = null,
    public val chapterResponse: String? = null,
    public val chapterRequest: JsonElement? = null,
    public val jsRule: String? = null,
    public val source: JsonElement? = null,
    public val chapterUrl: String? = null,
    public val variables: JsonElement? = null,
    public val upgradeCachedContent: Boolean? = null,
    public val positionContext: JsonElement? = null,
)

/** Params schema `ChineseConversionPutParams` (1 declared properties). */
@Serializable
public data class ChineseConversionPutParams(
    public val mode: JsonElement,
)

/** Params schema `ContentEditDeleteParams` (3 declared properties). */
@Serializable
public data class ContentEditDeleteParams(
    public val editId: Long? = null,
    public val sourceId: String? = null,
    public val bookId: String? = null,
)

/** Params schema `ContentEditGetParams` (3 declared properties). */
@Serializable
public data class ContentEditGetParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
)

/** Params schema `ContentEditListParams` (2 declared properties). */
@Serializable
public data class ContentEditListParams(
    public val sourceId: String,
    public val bookId: String,
)

/** Params schema `ContentEditPutParams` (5 declared properties). */
@Serializable
public data class ContentEditPutParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val editedContent: String? = null,
    public val editedAt: Long? = null,
)

/** Params schema `DictRuleDeleteParams` (1 declared properties). */
@Serializable
public data class DictRuleDeleteParams(
    public val name: String,
)

/** Params schema `DictRulePutParams` (5 declared properties). */
@Serializable
public data class DictRulePutParams(
    public val name: String,
    public val urlRule: String? = null,
    public val showRule: String? = null,
    public val enabled: Boolean? = null,
    public val sortNumber: Long? = null,
)

/** Params schema `DictRuleQueryParams` (4 declared properties). */
@Serializable
public data class DictRuleQueryParams(
    public val ruleName: String,
    public val word: String,
    public val dictResponse: String? = null,
    public val dictRequest: JsonElement? = null,
)

/** Params schema `DirectoryRulesParamsV2` (3 declared properties). */
@Serializable
public data class DirectoryRulesParamsV2(
    public val sourceId: JsonElement,
    public val bookId: String,
    public val rules: List<JsonElement>,
)

/** Params schema `DirectoryRulesPrepareParamsV2` (3 declared properties). */
@Serializable
public data class DirectoryRulesPrepareParamsV2(
    public val sourceId: JsonElement,
    public val bookId: String,
    public val rules: List<JsonElement>? = null,
)

/** Params schema `DirectoryTargetResolveParamsV2` (6 declared properties). */
@Serializable
public data class DirectoryTargetResolveParamsV2(
    public val bookId: String,
    public val viewId: String,
    public val nodeId: String,
    public val sourceId: String,
    public val catalogRevision: String,
    public val structureRevision: String,
)

/** Params schema `DirectoryViewOpenParams` (8 declared properties). */
@Serializable
public data class DirectoryViewOpenParams(
    public val bookId: String,
    public val collapsedNodeIds: List<String>? = null,
    public val query: String? = null,
    public val descending: Boolean? = null,
    public val chapterIndex: Long? = null,
    public val chapterOffsetScalar: Long? = null,
    public val anchorNodeId: String? = null,
    public val limit: Long? = null,
)

/** Params schema `DirectoryViewOpenParamsV2` (9 declared properties). */
@Serializable
public data class DirectoryViewOpenParamsV2(
    public val bookId: String,
    public val sourceId: String,
    public val collapsedNodeIds: List<String>? = null,
    public val query: String? = null,
    public val descending: Boolean? = null,
    public val chapterIndex: Long? = null,
    public val chapterOffsetScalar: Long? = null,
    public val anchorNodeId: String? = null,
    public val limit: Long? = null,
)

/** Params schema `DirectoryViewPageParams` (4 declared properties). */
@Serializable
public data class DirectoryViewPageParams(
    public val bookId: String,
    public val viewId: String,
    public val offset: Long,
    public val limit: Long,
)

/** Params schema `DirectoryViewPageParamsV2` (7 declared properties). */
@Serializable
public data class DirectoryViewPageParamsV2(
    public val bookId: String,
    public val viewId: String,
    public val offset: Long,
    public val limit: Long,
    public val sourceId: String,
    public val catalogRevision: String,
    public val structureRevision: String,
)

/** Params schema `DirectoryViewTargetResolveParams` (3 declared properties). */
@Serializable
public data class DirectoryViewTargetResolveParams(
    public val bookId: String,
    public val viewId: String,
    public val nodeId: String,
)

/** Params schema `DownloadOperationQueryParams` (6 declared properties). */
@Serializable
public data class DownloadOperationQueryParams(
    public val sourceId: String,
    public val bookId: String,
    public val action: String,
    public val operationId: String? = null,
    public val expected: JsonElement? = null,
    public val chapterIndex: Long? = null,
)

/** Params schema `DownloadOperationSettleParams` (7 declared properties). */
@Serializable
public data class DownloadOperationSettleParams(
    public val operationId: String,
    public val sequence: Long,
    public val payloadFingerprint: String,
    public val sourceId: String,
    public val bookId: String,
    public val action: String,
    public val chapterIndex: Long? = null,
)

/** Params schema `HostCacheGetRequest` (2 declared properties). */
@Serializable
public data class HostCacheGetRequest(
    public val namespace: String,
    public val key: String,
)

/** Params schema `HostCachePutRequest` (5 declared properties). */
@Serializable
public data class HostCachePutRequest(
    public val namespace: String,
    public val key: String,
    public val `value`: String? = null,
    public val valueBase64: String? = null,
    public val ttlMillis: Long? = null,
)

/** Params schema `HostCompleteParams` (2 declared properties). */
@Serializable
public data class HostCompleteParams(
    public val operationId: Long,
    public val result: JsonElement,
)

/** Params schema `HostCookieSetRequest` (3 declared properties). */
@Serializable
public data class HostCookieSetRequest(
    public val cookie: JsonElement,
    public val url: String? = null,
    public val sessionId: String? = null,
)

/** Params schema `HostFileReadRequest` (4 declared properties). */
@Serializable
public data class HostFileReadRequest(
    public val path: String,
    public val encoding: String? = null,
    public val byteOffset: Long? = null,
    public val maxBytes: Long? = null,
)

/** Params schema `HostFileWriteRequest` (6 declared properties). */
@Serializable
public data class HostFileWriteRequest(
    public val path: String,
    public val content: String? = null,
    public val contentBase64: String? = null,
    public val encoding: String? = null,
    public val createDirectories: Boolean? = null,
    public val append: Boolean? = null,
)

/** Params schema `HostLogEmitRequest` (4 declared properties). */
@Serializable
public data class HostLogEmitRequest(
    public val level: JsonElement,
    public val message: String,
    public val target: String? = null,
    public val fields: JsonElement? = null,
)

/** Params schema `HostPersistenceGetRequest` (2 declared properties). */
@Serializable
public data class HostPersistenceGetRequest(
    public val namespace: String,
    public val key: String,
)

/** Params schema `HostPersistencePutRequest` (5 declared properties). */
@Serializable
public data class HostPersistencePutRequest(
    public val namespace: String,
    public val key: String,
    public val `value`: String? = null,
    public val valueBase64: String? = null,
    public val expectedRevision: String? = null,
)

/** Params schema `HostSmokeParams` (2 declared properties). */
@Serializable
public data class HostSmokeParams(
    public val capability: JsonElement? = null,
    public val params: JsonElement? = null,
)

/** Params schema `HostSystemInfoRequest` (1 declared properties). */
@Serializable
public data class HostSystemInfoRequest(
    public val keys: List<String>? = null,
)

/** Params schema `HostWebViewEvaluateJavaScriptRequest` (7 declared properties). */
@Serializable
public data class HostWebViewEvaluateJavaScriptRequest(
    public val document: JsonElement,
    public val javaScript: String,
    public val resourceUrlMatcherJavaScript: String? = null,
    public val headers: JsonElement? = null,
    public val settleDelayMillis: Long? = null,
    public val timeoutMillis: Long? = null,
    public val profileId: String? = null,
)

/** Params schema `ImportFinalizeParams` has no declared properties. */
@Serializable
public object ImportFinalizeParams

/** Params schema `ImportParseParams` has no declared properties. */
@Serializable
public object ImportParseParams

/** Params schema `ImportPersistParams` has no declared properties. */
@Serializable
public object ImportPersistParams

/** Params schema `ImportRollbackParams` has no declared properties. */
@Serializable
public object ImportRollbackParams

/** Params schema `LocalBookCatalogParams` (4 declared properties). */
@Serializable
public data class LocalBookCatalogParams(
    public val catalog: JsonElement,
    public val entry: JsonElement,
    public val chapters: List<JsonElement>? = null,
    public val resources: List<JsonElement>? = null,
)

/** Params schema `LocalBookContentMetricsParams` (1 declared properties). */
@Serializable
public data class LocalBookContentMetricsParams(
    public val bookId: String,
)

/** Params schema `LocalBookNavigationBackfillParams` (2 declared properties). */
@Serializable
public data class LocalBookNavigationBackfillParams(
    public val bookId: String,
    public val filePath: String,
)

/** Params schema `LocalBookPositionArchiveListParams` (3 declared properties). */
@Serializable
public data class LocalBookPositionArchiveListParams(
    public val bookId: String,
    public val offset: Long? = null,
    public val limit: Long? = null,
)

/** Params schema `MangaCacheClassifyBatchParams` (1 declared properties). */
@Serializable
public data class MangaCacheClassifyBatchParams(
    public val versions: List<JsonElement>,
)

/** Params schema `MangaCacheEvictParams` (2 declared properties). */
@Serializable
public data class MangaCacheEvictParams(
    public val nowSeconds: Long,
    public val ttlSeconds: Long,
)

/** Params schema `MangaCacheEvictionClaimParams` (4 declared properties). */
@Serializable
public data class MangaCacheEvictionClaimParams(
    public val chapter: JsonElement,
    public val manifestVersion: String,
    public val nowSeconds: Long? = null,
    public val ttlSeconds: Long? = null,
)

/** Params schema `MangaCacheEvictionCompleteParams` (2 declared properties). */
@Serializable
public data class MangaCacheEvictionCompleteParams(
    public val token: String,
    public val removed: Boolean,
)

/** Params schema `MangaCacheEvictionPendingParams` has no declared properties. */
@Serializable
public object MangaCacheEvictionPendingParams

/** Params schema `MangaCacheMaterializationAbortParams` (1 declared properties). */
@Serializable
public data class MangaCacheMaterializationAbortParams(
    public val token: String,
)

/** Params schema `MangaCacheMaterializationCompleteParams` (4 declared properties). */
@Serializable
public data class MangaCacheMaterializationCompleteParams(
    public val chapter: JsonElement,
    public val manifestVersion: String,
    public val token: String,
    public val resourceRefs: List<String>,
)

/** Params schema `MangaCacheMaterializationPrepareParams` (3 declared properties). */
@Serializable
public data class MangaCacheMaterializationPrepareParams(
    public val chapter: JsonElement,
    public val manifestVersion: String,
    public val intent: String,
)

/** Params schema `MangaCacheRecordReadParams` (3 declared properties). */
@Serializable
public data class MangaCacheRecordReadParams(
    public val chapter: JsonElement,
    public val manifestVersion: String,
    public val readAt: Long,
)

/** Params schema `MangaCacheVersionParams` (2 declared properties). */
@Serializable
public data class MangaCacheVersionParams(
    public val chapter: JsonElement,
    public val manifestVersion: String,
)

/** Params schema `MangaChapterGetParams` (8 declared properties). */
@Serializable
public data class MangaChapterGetParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val directoryTargetProof: JsonElement? = null,
    public val policy: String? = null,
    public val expectedManifestVersion: String? = null,
    public val preparationRevision: Long? = null,
    public val anchor: JsonElement? = null,
)

/** Params schema `MangaEntryGetParams` (4 declared properties). */
@Serializable
public data class MangaEntryGetParams(
    public val sourceId: String,
    public val bookId: String,
    public val directoryTargetProof: JsonElement? = null,
    public val chapterIndex: Long? = null,
)

/** Params schema `MangaPagesExtractParams` (8 declared properties). */
@Serializable
public data class MangaPagesExtractParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterTitle: String? = null,
    public val chapterResponse: String? = null,
    public val chapterRequest: JsonElement? = null,
    public val source: JsonElement? = null,
    public val chapterUrl: String? = null,
    public val variables: JsonElement? = null,
)

/** Params schema `MangaPagesWindowParams` (6 declared properties). */
@Serializable
public data class MangaPagesWindowParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterId: String,
    public val manifestVersion: String,
    public val start: Long,
    public val limit: Long,
)

/** Params schema `MangaResourceInspectParams` (3 declared properties). */
@Serializable
public data class MangaResourceInspectParams(
    public val transferId: String,
    public val sha256: String,
    public val bytes: Long,
)

/** Params schema `MangaResourcePrepareParams` (5 declared properties). */
@Serializable
public data class MangaResourcePrepareParams(
    public val chapter: JsonElement,
    public val manifestVersion: String,
    public val resourceRef: String,
    public val sourceRuleVersion: String,
    public val transferId: String,
)

/** Params schema `MangaRetentionGetParams` (1 declared properties). */
@Serializable
public data class MangaRetentionGetParams(
    public val chapter: JsonElement,
)

/** Params schema `MangaRetentionListParams` (2 declared properties). */
@Serializable
public data class MangaRetentionListParams(
    public val sourceId: String,
    public val bookId: String,
)

/** Params schema `MangaRetentionSetParams` (3 declared properties). */
@Serializable
public data class MangaRetentionSetParams(
    public val chapter: JsonElement,
    public val retain: Boolean,
    public val retainedAt: Long? = null,
)

/** Params schema `MangaSessionProtectParams` (2 declared properties). */
@Serializable
public data class MangaSessionProtectParams(
    public val chapters: List<JsonElement>,
    public val sessionEpoch: Long,
)

/** Params schema `MangaSessionReleaseParams` (2 declared properties). */
@Serializable
public data class MangaSessionReleaseParams(
    public val sessionEpoch: Long,
    public val expectedRuntimeEpoch: Long? = null,
)

/** Params schema `MangaSessionReplaceParams` (3 declared properties). */
@Serializable
public data class MangaSessionReplaceParams(
    public val chapters: List<JsonElement>,
    public val sessionEpoch: Long,
    public val expectedRuntimeEpoch: Long,
)

/** Params schema `MangaSessionRuntimeParams` has no declared properties. */
@Serializable
public object MangaSessionRuntimeParams

/** Params schema `MangaSourceSwitchOperationParams` (9 declared properties). */
@Serializable
public data class MangaSourceSwitchOperationParams(
    public val operationId: String,
    public val sequence: Long,
    public val fromSourceId: String,
    public val fromBookId: String,
    public val expectedFrom: JsonElement,
    public val expectedTarget: JsonElement,
    public val firstImage: JsonElement,
    public val targetPageOrdinal: Long,
    public val acceptApproximate: JsonElement,
)

/** Params schema `MangaSourceSwitchOperationQueryParams` (2 declared properties). */
@Serializable
public data class MangaSourceSwitchOperationQueryParams(
    public val operationId: String? = null,
    public val expected: JsonElement? = null,
)

/** Params schema `MangaSourceSwitchParams` (7 declared properties). */
@Serializable
public data class MangaSourceSwitchParams(
    public val fromSourceId: String,
    public val fromBookId: String,
    public val expectedFrom: JsonElement,
    public val expectedTarget: JsonElement,
    public val firstImage: JsonElement,
    public val targetPageOrdinal: Long,
    public val acceptApproximate: JsonElement,
)

/** Params schema `ReadRecordAccumulateParams` (7 declared properties). */
@Serializable
public data class ReadRecordAccumulateParams(
    public val deviceId: String,
    public val bookName: String,
    public val elapsedMillis: Long,
    public val readAt: Long,
    public val author: String? = null,
    public val operationId: String? = null,
    public val sequence: Long? = null,
)

/** Params schema `ReadRecordClearParams` has no declared properties. */
@Serializable
public object ReadRecordClearParams

/** Params schema `ReadRecordCreateParams` (5 declared properties). */
@Serializable
public data class ReadRecordCreateParams(
    public val bookName: String,
    public val deviceId: String? = null,
    public val author: String? = null,
    public val readTime: Long? = null,
    public val lastRead: Long? = null,
)

/** Params schema `ReadRecordDeleteByBookParams` (1 declared properties). */
@Serializable
public data class ReadRecordDeleteByBookParams(
    public val bookName: String,
)

/** Params schema `ReadRecordDeleteParams` (2 declared properties). */
@Serializable
public data class ReadRecordDeleteParams(
    public val deviceId: String,
    public val bookName: String,
)

/** Params schema `ReadRecordListParams` (1 declared properties). */
@Serializable
public data class ReadRecordListParams(
    public val deviceId: String? = null,
)

/** Params schema `ReadRecordSummaryParams` (2 declared properties). */
@Serializable
public data class ReadRecordSummaryParams(
    public val query: String? = null,
    public val sortBy: String? = null,
)

/** Params schema `ReadRecordUpdateParams` (5 declared properties). */
@Serializable
public data class ReadRecordUpdateParams(
    public val deviceId: String,
    public val bookName: String,
    public val author: String? = null,
    public val readTime: Long? = null,
    public val lastRead: Long? = null,
)

/** Params schema `ReaderLocationResolveParams` (6 declared properties). */
@Serializable
public data class ReaderLocationResolveParams(
    public val bookId: String,
    public val chapterIndex: Long,
    public val anchor: JsonElement,
    public val layout: JsonElement,
    public val sourceId: String? = null,
    public val chapterTitle: String? = null,
)

/** Params schema `ReadingCatalogPageParams` (5 declared properties). */
@Serializable
public data class ReadingCatalogPageParams(
    public val sourceId: String,
    public val bookId: String,
    public val limit: Long,
    public val offset: Long? = null,
    public val revision: String? = null,
)

/** Params schema `ReadingDocumentWindowParams` (6 declared properties). */
@Serializable
public data class ReadingDocumentWindowParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val startScalar: Long,
    public val scalarLimit: Long,
    public val positionContext: JsonElement,
)

/** Params schema `ReadingEntryPrepareParams` (5 declared properties). */
@Serializable
public data class ReadingEntryPrepareParams(
    public val sourceId: String,
    public val bookId: String,
    public val neighborOffset: Long,
    public val localSourcePath: String? = null,
    public val chapterIndex: Long? = null,
)

/** Params schema `ReadingEntrySnapshotParams` (6 declared properties). */
@Serializable
public data class ReadingEntrySnapshotParams(
    public val sourceId: String,
    public val bookId: String,
    public val directoryTargetProof: JsonElement? = null,
    public val chapterIndex: Long? = null,
    public val windowScalarLimit: Long? = null,
    public val positionContext: JsonElement? = null,
)

/** Params schema `ReadingPreparationParams` (9 declared properties). */
@Serializable
public data class ReadingPreparationParams(
    public val action: String,
    public val sourceId: String? = null,
    public val bookId: String? = null,
    public val reason: String? = null,
    public val revision: Long? = null,
    public val blockReason: String? = null,
    public val pendingAdd: JsonElement? = null,
    public val offset: Long? = null,
    public val limit: Long? = null,
)

/** Params schema `ReplaceApplyParams` (4 declared properties). */
@Serializable
public data class ReplaceApplyParams(
    public val text: String,
    public val bookName: String,
    public val bookOrigin: String,
    public val target: String,
)

/** Params schema `ReplacePersistParams` has no declared properties. */
@Serializable
public object ReplacePersistParams

/** Params schema `ReplacePreviewParams` (4 declared properties). */
@Serializable
public data class ReplacePreviewParams(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val maxScalars: Long? = null,
)

/** Params schema `ReplaceRuleCreateParams` (14 declared properties). */
@Serializable
public data class ReplaceRuleCreateParams(
    public val id: Long? = null,
    public val name: String? = null,
    public val group: String? = null,
    public val pattern: String? = null,
    public val replacement: String? = null,
    public val scope: String? = null,
    public val scopeTitle: Boolean? = null,
    public val scopeSource: Boolean? = null,
    public val scopeContent: Boolean? = null,
    public val excludeScope: String? = null,
    public val isEnabled: Boolean? = null,
    public val isRegex: Boolean? = null,
    public val timeoutMillisecond: Long? = null,
    public val order: Long? = null,
)

/** Params schema `ReplaceRuleDeleteParams` (1 declared properties). */
@Serializable
public data class ReplaceRuleDeleteParams(
    public val id: Long,
)

/** Params schema `ReplaceRuleListParams` (1 declared properties). */
@Serializable
public data class ReplaceRuleListParams(
    public val enabledOnly: Boolean? = null,
)

/** Params schema `ReplaceRuleUpdateParams` (14 declared properties). */
@Serializable
public data class ReplaceRuleUpdateParams(
    public val id: Long,
    public val name: String? = null,
    public val group: String? = null,
    public val pattern: String? = null,
    public val replacement: String? = null,
    public val scope: String? = null,
    public val scopeTitle: Boolean? = null,
    public val scopeSource: Boolean? = null,
    public val scopeContent: Boolean? = null,
    public val excludeScope: String? = null,
    public val isEnabled: Boolean? = null,
    public val isRegex: Boolean? = null,
    public val timeoutMillisecond: Long? = null,
    public val order: Long? = null,
)

/** Params schema `ReplaceUndoParams` (1 declared properties). */
@Serializable
public data class ReplaceUndoParams(
    public val undoToken: JsonElement,
)

/** Params schema `ReplaceValidateParams` (3 declared properties). */
@Serializable
public data class ReplaceValidateParams(
    public val pattern: String,
    public val scope: List<String>,
    public val isRegex: Boolean? = null,
)

/** Params schema `RssFavoriteListParams` (2 declared properties). */
@Serializable
public data class RssFavoriteListParams(
    public val subscriptionId: String? = null,
    public val limit: Long? = null,
)

/** Params schema `RssItemReadParams` (3 declared properties). */
@Serializable
public data class RssItemReadParams(
    public val guid: String,
    public val read: Boolean,
    public val subscriptionId: String? = null,
)

/** Params schema `RssListParams` (4 declared properties). */
@Serializable
public data class RssListParams(
    public val xml: String,
    public val feedUrl: String? = null,
    public val unreadOnly: Boolean? = null,
    public val limit: Long? = null,
)

/** Params schema `RssParseParams` (2 declared properties). */
@Serializable
public data class RssParseParams(
    public val xml: String,
    public val feedUrl: String? = null,
)

/** Params schema `RssRefreshParams` (6 declared properties). */
@Serializable
public data class RssRefreshParams(
    public val subscriptionId: String,
    public val evaluatedAt: Long,
    public val enabled: Boolean? = null,
    public val updateIntervalMinutes: Long? = null,
    public val lastFetchedAt: Long? = null,
    public val forceRefresh: Boolean? = null,
)

/** Params schema `RssSourceExportParams` (1 declared properties). */
@Serializable
public data class RssSourceExportParams(
    public val exportedAt: Long? = null,
)

/** Params schema `RssSourceImportParams` (2 declared properties). */
@Serializable
public data class RssSourceImportParams(
    public val json: String,
    public val replaceExisting: Boolean? = null,
)

/** Params schema `RssSourcePutParams` (2 declared properties). */
@Serializable
public data class RssSourcePutParams(
    public val source: JsonElement,
    public val subscriptionId: String? = null,
)

/** Params schema `RssSubscriptionAddParams` (5 declared properties). */
@Serializable
public data class RssSubscriptionAddParams(
    public val subscriptionId: String,
    public val feedUrl: String,
    public val title: String? = null,
    public val siteUrl: String? = null,
    public val enabled: Boolean? = null,
)

/** Params schema `RssSubscriptionCreateParams` (3 declared properties). */
@Serializable
public data class RssSubscriptionCreateParams(
    public val feedUrl: String,
    public val title: String? = null,
    public val enabled: Boolean? = null,
)

/** Params schema `RssSubscriptionDeleteParams` (1 declared properties). */
@Serializable
public data class RssSubscriptionDeleteParams(
    public val subscriptionId: String,
)

/** Params schema `RssSubscriptionItemsParams` (3 declared properties). */
@Serializable
public data class RssSubscriptionItemsParams(
    public val subscriptionId: String,
    public val unreadOnly: Boolean? = null,
    public val limit: Long? = null,
)

/** Params schema `RssSubscriptionListParams` has no declared properties. */
@Serializable
public object RssSubscriptionListParams

/** Params schema `RssSubscriptionRefreshParams` (2 declared properties). */
@Serializable
public data class RssSubscriptionRefreshParams(
    public val subscriptionId: String,
    public val evaluatedAt: Long? = null,
)

/** Params schema `RssSubscriptionUpdateParams` (4 declared properties). */
@Serializable
public data class RssSubscriptionUpdateParams(
    public val subscriptionId: String,
    public val feedUrl: String? = null,
    public val title: String? = null,
    public val enabled: Boolean? = null,
)

/** Params schema `RuleBundleImportParams` (2 declared properties). */
@Serializable
public data class RuleBundleImportParams(
    public val json: String,
    public val replaceExisting: Boolean? = null,
)

/** Params schema `RuleSubKeyParams` (1 declared properties). */
@Serializable
public data class RuleSubKeyParams(
    public val id: Long,
)

/** Params schema `RuleSubPutParams` (7 declared properties). */
@Serializable
public data class RuleSubPutParams(
    public val id: Long,
    public val name: String? = null,
    public val url: String? = null,
    public val type: Long? = null,
    public val customOrder: Long? = null,
    public val autoUpdate: Boolean? = null,
    public val update: Long? = null,
)

/** Params schema `RuleSubUpdatePreviewParams` (3 declared properties). */
@Serializable
public data class RuleSubUpdatePreviewParams(
    public val subscriptionId: Long,
    public val remoteJson: String,
    public val expected: JsonElement? = null,
)

/** Params schema `RulesMutationApplyParams` (3 declared properties). */
@Serializable
public data class RulesMutationApplyParams(
    public val operationId: String,
    public val sequence: Long,
    public val mutation: JsonElement,
)

/** Params schema `RulesMutationQueryParams` (2 declared properties). */
@Serializable
public data class RulesMutationQueryParams(
    public val operationId: JsonElement? = null,
    public val expected: JsonElement? = null,
)

/** Params schema `RuntimeCancelParams` (1 declared properties). */
@Serializable
public data class RuntimeCancelParams(
    public val requestId: Long,
)

/** Params schema `RuntimeShutdownParams` has no declared properties. */
@Serializable
public object RuntimeShutdownParams

/** Params schema `RuntimeStorageApplyParams` (2 declared properties). */
@Serializable
public data class RuntimeStorageApplyParams(
    public val manifest: JsonElement,
    public val payload: String,
)

/** Params schema `RuntimeStorageBackupCreateParams` has no declared properties. */
@Serializable
public object RuntimeStorageBackupCreateParams

/** Params schema `RuntimeStorageBackupRestoreParams` (2 declared properties). */
@Serializable
public data class RuntimeStorageBackupRestoreParams(
    public val backupId: String,
    public val expectedSha256: String,
)

/** Params schema `RuntimeStorageExportParams` has no declared properties. */
@Serializable
public object RuntimeStorageExportParams

/** Params schema `RuntimeStorageFlushParams` has no declared properties. */
@Serializable
public object RuntimeStorageFlushParams

/** Params schema `RuntimeStorageRestoreParams` has no declared properties. */
@Serializable
public object RuntimeStorageRestoreParams

/** Params schema `SearchBookBatchGetParams` (1 declared properties). */
@Serializable
public data class SearchBookBatchGetParams(
    public val identities: List<JsonElement>,
)

/** Params schema `SearchBookKeyParams` (2 declared properties). */
@Serializable
public data class SearchBookKeyParams(
    public val bookUrl: String,
    public val origin: String? = null,
)

/** Params schema `SearchBookListParams` (1 declared properties). */
@Serializable
public data class SearchBookListParams(
    public val origin: String? = null,
)

/** Params schema `SearchBookPutParams` (19 declared properties). */
@Serializable
public data class SearchBookPutParams(
    public val bookUrl: String,
    public val origin: String? = null,
    public val originName: String? = null,
    public val type: Long? = null,
    public val name: String? = null,
    public val author: String? = null,
    public val kind: String? = null,
    public val coverUrl: String? = null,
    public val intro: String? = null,
    public val wordCount: String? = null,
    public val latestChapterTitle: String? = null,
    public val tocUrl: String? = null,
    public val time: Long? = null,
    public val variable: String? = null,
    public val originOrder: Long? = null,
    public val chapterWordCountText: String? = null,
    public val chapterWordCount: Long? = null,
    public val respondTime: Long? = null,
    public val acquisition: JsonElement? = null,
)

/** Params schema `SearchBookRelatedParams` (6 declared properties). */
@Serializable
public data class SearchBookRelatedParams(
    public val mode: String? = null,
    public val identity: JsonElement? = null,
    public val name: String? = null,
    public val author: String? = null,
    public val limit: Long? = null,
    public val cursor: String? = null,
)

/** Params schema `SearchContentParams` (8 declared properties). */
@Serializable
public data class SearchContentParams(
    public val keyword: String,
    public val sourceId: String? = null,
    public val bookId: String? = null,
    public val maxResults: Long? = null,
    public val offset: Long? = null,
    public val useCursor: Boolean? = null,
    public val cursor: String? = null,
    public val closeCursor: Boolean? = null,
)

/** Params schema `SearchCoverParams` (3 declared properties). */
@Serializable
public data class SearchCoverParams(
    public val bookName: String,
    public val sourceIds: List<String>,
    public val author: String? = null,
)

/** Params schema `SearchHistoryAddParams` (1 declared properties). */
@Serializable
public data class SearchHistoryAddParams(
    public val keyword: String,
)

/** Params schema `SearchHistoryClearParams` has no declared properties. */
@Serializable
public object SearchHistoryClearParams

/** Params schema `SearchHistoryListParams` (1 declared properties). */
@Serializable
public data class SearchHistoryListParams(
    public val limit: Long? = null,
)

/** Params schema `SearchKeywordKeyParams` (1 declared properties). */
@Serializable
public data class SearchKeywordKeyParams(
    public val word: String,
)

/** Params schema `SearchKeywordPutParams` (3 declared properties). */
@Serializable
public data class SearchKeywordPutParams(
    public val word: String,
    public val usage: Long? = null,
    public val lastUseTime: Long? = null,
)

/** Params schema `SourceCheckEphemeralRunParams` (7 declared properties). */
@Serializable
public data class SourceCheckEphemeralRunParams(
    public val sources: List<JsonElement>,
    public val reportVersion: Long? = null,
    public val mode: String? = null,
    public val keyword: JsonElement? = null,
    public val keywordOverride: JsonElement? = null,
    public val timeoutMs: JsonElement? = null,
    public val levels: JsonElement? = null,
)

/** Params schema `SourceCheckParams` (5 declared properties). */
@Serializable
public data class SourceCheckParams(
    public val sourceIds: List<String>,
    public val keyword: String? = null,
    public val timeoutMs: Long? = null,
    public val levels: List<String>? = null,
    public val responses: List<JsonElement>? = null,
)

/** Params schema `SourceCheckReportClearParams` (1 declared properties). */
@Serializable
public data class SourceCheckReportClearParams(
    public val sourceIds: List<String>,
)

/** Params schema `SourceCheckRunParams` (7 declared properties). */
@Serializable
public data class SourceCheckRunParams(
    public val sourceIds: List<String>,
    public val reportVersion: String? = null,
    public val mode: String? = null,
    public val keyword: String? = null,
    public val keywordOverride: Boolean? = null,
    public val timeoutMs: Long? = null,
    public val levels: List<String>? = null,
)

/** Params schema `SourceDebugParams` (3 declared properties). */
@Serializable
public data class SourceDebugParams(
    public val sourceId: String,
    public val key: String? = null,
    public val responses: JsonElement? = null,
)

/** Params schema `SourceDeleteParams` (1 declared properties). */
@Serializable
public data class SourceDeleteParams(
    public val sourceIds: List<String>,
)

/** Params schema `SourceExploreKindsParams` (3 declared properties). */
@Serializable
public data class SourceExploreKindsParams(
    public val source: JsonElement? = null,
    public val sourceId: String? = null,
    public val exploreUrl: String? = null,
)

/** Params schema `SourceExploreParams` (6 declared properties). */
@Serializable
public data class SourceExploreParams(
    public val source: JsonElement? = null,
    public val sourceId: String? = null,
    public val url: String? = null,
    public val page: Long? = null,
    public val exploreResponse: String? = null,
    public val exploreRequest: JsonElement? = null,
)

/** Params schema `SourceExportParams` (3 declared properties). */
@Serializable
public data class SourceExportParams(
    public val sourceIds: List<String>? = null,
    public val format: String? = null,
    public val encryptKey: String? = null,
)

/** Params schema `SourceGroupCatalogParams` (1 declared properties). */
@Serializable
public data class SourceGroupCatalogParams(
    public val reportVersion: JsonElement,
)

/** Params schema `SourceImageRequestParams` (5 declared properties). */
@Serializable
public data class SourceImageRequestParams(
    public val sourceId: String,
    public val imageUrl: String,
    public val baseUrl: String? = null,
    public val source: JsonElement? = null,
    public val expectedSourceVersion: String? = null,
)

/** Params schema `SourceImportParams` (6 declared properties). */
@Serializable
public data class SourceImportParams(
    public val sourceId: String? = null,
    public val name: String? = null,
    public val baseUrl: String? = null,
    public val rules: JsonElement? = null,
    public val bookSource: JsonElement? = null,
    public val useSourceReplacement: Boolean? = null,
)

/** Params schema `SourceListParams` (4 declared properties). */
@Serializable
public data class SourceListParams(
    public val includeCheckReports: Boolean? = null,
    public val checkReportVersion: String? = null,
    public val sourceId: String? = null,
    public val enabledOnly: Boolean? = null,
)

/** Params schema `SourceSupplyParams` has no declared properties. */
@Serializable
public object SourceSupplyParams

/** Params schema `SourceSwitchCommitParams` (8 declared properties). */
@Serializable
public data class SourceSwitchCommitParams(
    public val from: JsonElement,
    public val target: JsonElement,
    public val newToc: List<JsonElement>,
    public val currentChapterTitle: String,
    public val currentChapterIndex: Long,
    public val updatedAt: Long,
    public val operationId: String? = null,
    public val sequence: Long? = null,
)

/** Params schema `SourceSwitchTransactionParams` (3 declared properties). */
@Serializable
public data class SourceSwitchTransactionParams(
    public val transactionId: String,
    public val operationId: String? = null,
    public val sequence: Long? = null,
)

/** Params schema `SourceSwitchTransactionQueryParams` (4 declared properties). */
@Serializable
public data class SourceSwitchTransactionQueryParams(
    public val operationId: String,
    public val expected: JsonElement? = null,
    public val expectedStage: JsonElement? = null,
    public val expectedCanonical: JsonElement? = null,
)

/** Params schema `SourceUpdateParams` (2 declared properties). */
@Serializable
public data class SourceUpdateParams(
    public val sourceId: String,
    public val enabled: Boolean,
)

/** Params schema `SyncBackupAutoDecisionParams` (3 declared properties). */
@Serializable
public data class SyncBackupAutoDecisionParams(
    public val enabled: Boolean,
    public val evaluatedAt: Long,
    public val lastBackupAt: Long? = null,
)

/** Params schema `SyncBackupContentPlanParams` (1 declared properties). */
@Serializable
public data class SyncBackupContentPlanParams(
    public val request: JsonElement,
)

/** Params schema `SyncBackupParams` (2 declared properties). */
@Serializable
public data class SyncBackupParams(
    public val `package`: JsonElement,
    public val policy: JsonElement,
)

/** Params schema `SyncBackupRestoreScopePlanParams` (1 declared properties). */
@Serializable
public data class SyncBackupRestoreScopePlanParams(
    public val request: JsonElement,
)

/** Params schema `SyncBackupRetentionParams` (4 declared properties). */
@Serializable
public data class SyncBackupRetentionParams(
    public val config: JsonElement,
    public val preservingBackupId: String,
    public val evaluatedAt: Long,
    public val candidates: List<JsonElement>? = null,
)

/** Params schema `SyncConflictResolveParams` (6 declared properties). */
@Serializable
public data class SyncConflictResolveParams(
    public val local: JsonElement,
    public val remote: JsonElement,
    public val resolutions: List<JsonElement>,
    public val mergedSnapshotId: String? = null,
    public val mergedDeviceId: String? = null,
    public val mergedCreatedAt: Long? = null,
)

/** Params schema `SyncMergeParams` (5 declared properties). */
@Serializable
public data class SyncMergeParams(
    public val local: JsonElement,
    public val remote: JsonElement,
    public val mergedSnapshotId: String? = null,
    public val mergedDeviceId: String? = null,
    public val mergedCreatedAt: Long? = null,
)

/** Params schema `SyncWebDavBackupReceiptQueryParams` (3 declared properties). */
@Serializable
public data class SyncWebDavBackupReceiptQueryParams(
    public val transactionId: String,
    public val auth: String? = null,
    public val response: JsonElement? = null,
)

/** Params schema `SyncWebDavBackupRetentionSkipParams` (2 declared properties). */
@Serializable
public data class SyncWebDavBackupRetentionSkipParams(
    public val transactionId: String,
    public val response: JsonElement? = null,
)

/** Params schema `SyncWebDavDirectoryListPlanParams` (3 declared properties). */
@Serializable
public data class SyncWebDavDirectoryListPlanParams(
    public val baseUrl: String,
    public val directory: String,
    public val auth: String? = null,
)

/** Params schema `SyncWebDavMultistatusParseParams` (1 declared properties). */
@Serializable
public data class SyncWebDavMultistatusParseParams(
    public val response: JsonElement,
)

/** Params schema `SyncWebDavPlanParams` (3 declared properties). */
@Serializable
public data class SyncWebDavPlanParams(
    public val baseUrl: String,
    public val auth: String? = null,
    public val requests: List<JsonElement>? = null,
)

/** Params schema `SyncWebDavProgressMergeParams` (3 declared properties). */
@Serializable
public data class SyncWebDavProgressMergeParams(
    public val localRecords: List<JsonElement>,
    public val remoteRecords: List<JsonElement>,
    public val config: JsonElement,
)

/** Params schema `SyncWebDavProgressPushPlanParams` (3 declared properties). */
@Serializable
public data class SyncWebDavProgressPushPlanParams(
    public val localRecords: List<JsonElement>,
    public val remoteRecords: List<JsonElement>,
    public val conflictPolicy: String,
)

/** Params schema `SyncWebDavRemoteBookLocalImportPlanParams` (2 declared properties). */
@Serializable
public data class SyncWebDavRemoteBookLocalImportPlanParams(
    public val book: JsonElement,
    public val downloadedByteCount: Long,
)

/** Params schema `SyncWebDavRemoteBookPlanParams` (1 declared properties). */
@Serializable
public data class SyncWebDavRemoteBookPlanParams(
    public val request: JsonElement,
)

/** Params schema `SyncWebDavRemoteBookResultParams` (1 declared properties). */
@Serializable
public data class SyncWebDavRemoteBookResultParams(
    public val request: JsonElement,
)

/** Params schema `SyncWebDavTransactionAbortParams` (2 declared properties). */
@Serializable
public data class SyncWebDavTransactionAbortParams(
    public val transactionId: String,
    public val publisherUndispatched: Boolean? = null,
)

/** Params schema `SyncWebDavTransactionAdvanceParams` (2 declared properties). */
@Serializable
public data class SyncWebDavTransactionAdvanceParams(
    public val transactionId: String,
    public val responses: List<JsonElement>,
)

/** Params schema `SyncWebDavTransactionCommitParams` (4 declared properties). */
@Serializable
public data class SyncWebDavTransactionCommitParams(
    public val transactionId: String,
    public val appliedChecksum: String,
    public val hostConfigApplied: Boolean? = null,
    public val queryOnly: Boolean? = null,
)

/** Params schema `SyncWebDavTransactionResolveParams` (2 declared properties). */
@Serializable
public data class SyncWebDavTransactionResolveParams(
    public val transactionId: String,
    public val choice: String,
)

/** Params schema `SyncWebDavTransactionStartParams` (10 declared properties). */
@Serializable
public data class SyncWebDavTransactionStartParams(
    public val operation: String,
    public val baseUrl: String,
    public val directory: String,
    public val systemScheme: String? = null,
    public val hostConfig: JsonElement? = null,
    public val auth: String? = null,
    public val encryptionKey: String? = null,
    public val maxBackups: Long? = null,
    public val maxAgeDays: Long? = null,
    public val operationId: String? = null,
)

/** Params schema `TtsChapterPlanParams` (3 declared properties). */
@Serializable
public data class TtsChapterPlanParams(
    public val chapter: JsonElement,
    public val drainBehavior: JsonElement? = null,
    public val nextChapter: JsonElement? = null,
)

/** Params schema `TtsConfigPutParams` (5 declared properties). */
@Serializable
public data class TtsConfigPutParams(
    public val rate: Long,
    public val pitch: Long,
    public val followSys: Boolean,
    public val engine: String? = null,
    public val ratePercent: Long? = null,
)

/** Params schema `TtsQueueNextParams` (1 declared properties). */
@Serializable
public data class TtsQueueNextParams(
    public val chapter: JsonElement,
)

/** Params schema `TtsQueuePauseParams` (1 declared properties). */
@Serializable
public data class TtsQueuePauseParams(
    public val chapter: JsonElement,
)

/** Params schema `TtsQueuePlayParams` (2 declared properties). */
@Serializable
public data class TtsQueuePlayParams(
    public val plan: JsonElement,
    public val startSliceIndex: Long? = null,
)

/** Params schema `TtsQueuePrevParams` (1 declared properties). */
@Serializable
public data class TtsQueuePrevParams(
    public val chapter: JsonElement,
)

/** Params schema `TtsQueueReportCallbackParams` (6 declared properties). */
@Serializable
public data class TtsQueueReportCallbackParams(
    public val chapter: JsonElement,
    public val sliceIndex: Long,
    public val status: JsonElement,
    public val callbackId: String,
    public val failurePolicy: JsonElement? = null,
    public val failureLimit: Long? = null,
)

/** Params schema `TtsQueueReportStatusParams` (3 declared properties). */
@Serializable
public data class TtsQueueReportStatusParams(
    public val chapter: JsonElement,
    public val sliceIndex: Long,
    public val status: JsonElement,
)

/** Params schema `TtsQueueResumeParams` (1 declared properties). */
@Serializable
public data class TtsQueueResumeParams(
    public val chapter: JsonElement,
)

/** Params schema `TtsQueueSeekParams` (2 declared properties). */
@Serializable
public data class TtsQueueSeekParams(
    public val chapter: JsonElement,
    public val sliceIndex: Long,
)

/** Params schema `TtsQueueSetRateParams` (2 declared properties). */
@Serializable
public data class TtsQueueSetRateParams(
    public val chapter: JsonElement,
    public val rate: Long,
)

/** Params schema `TtsQueueSkipParams` (1 declared properties). */
@Serializable
public data class TtsQueueSkipParams(
    public val chapter: JsonElement,
)

/** Params schema `TtsQueueStatusParams` (1 declared properties). */
@Serializable
public data class TtsQueueStatusParams(
    public val chapter: JsonElement,
)

/** Params schema `TtsQueueStopParams` (1 declared properties). */
@Serializable
public data class TtsQueueStopParams(
    public val chapter: JsonElement,
)

/** Params schema `TtsSliceParams` (3 declared properties). */
@Serializable
public data class TtsSliceParams(
    public val chapter: JsonElement,
    public val content: String,
    public val strategy: JsonElement? = null,
)

/** Params schema `TxtTocRuleCreateParams` (6 declared properties). */
@Serializable
public data class TxtTocRuleCreateParams(
    public val name: String,
    public val rule: String,
    public val id: Long? = null,
    public val example: String? = null,
    public val serialNumber: Long? = null,
    public val enable: Boolean? = null,
)

/** Params schema `TxtTocRuleDeleteParams` (1 declared properties). */
@Serializable
public data class TxtTocRuleDeleteParams(
    public val id: Long,
)

/** Params schema `TxtTocRuleListParams` (1 declared properties). */
@Serializable
public data class TxtTocRuleListParams(
    public val enabledOnly: Boolean? = null,
)

/** Params schema `TxtTocRulePreviewParams` (5 declared properties). */
@Serializable
public data class TxtTocRulePreviewParams(
    public val bookId: String,
    public val filePath: String,
    public val draft: JsonElement,
    public val editingRuleId: Long? = null,
    public val limit: Long? = null,
)

/** Params schema `TxtTocRuleUpdateParams` (6 declared properties). */
@Serializable
public data class TxtTocRuleUpdateParams(
    public val id: Long,
    public val name: String? = null,
    public val rule: String? = null,
    public val example: String? = null,
    public val serialNumber: Long? = null,
    public val enable: Boolean? = null,
)

/** The shared JSON codec for the Reader Core protocol. */
public val ReaderProtocolJson: Json = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

/** An optional property whose explicit null is a business operation. */
@Serializable(with = PatchFieldSerializer::class)
public sealed interface PatchField<out T> {
    public data object Absent : PatchField<Nothing>
    public data object Null : PatchField<Nothing>
    public data class Value<T>(public val value: T) : PatchField<T>
}

public class PatchFieldSerializer<T>(
    private val valueSerializer: KSerializer<T>,
) : KSerializer<PatchField<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.reader.core.protocol.PatchField", valueSerializer.descriptor)

    override fun deserialize(decoder: Decoder): PatchField<T> {
        val json = decoder as JsonDecoder
        val element = json.decodeJsonElement()
        return if (element == JsonNull) PatchField.Null
        else PatchField.Value(json.json.decodeFromJsonElement(valueSerializer, element))
    }

    override fun serialize(encoder: Encoder, value: PatchField<T>) {
        val json = encoder as JsonEncoder
        val element = when (value) {
            PatchField.Absent -> throw SerializationException("Absent belongs in an omitted property")
            PatchField.Null -> JsonNull
            is PatchField.Value -> json.json.encodeToJsonElement(valueSerializer, value.value)
        }
        json.encodeJsonElement(element)
    }
}

@Serializable
public data class ReaderTypedCommand<Params>(
    public val requestId: Long,
    public val method: ReaderCommandMethod,
    public val params: Params,
    @Required public val protocolVersion: Int = 1,
)

@Serializable
public data class ReaderTypedResult<Data>(
    public val protocolVersion: Int,
    public val requestId: Long,
    public val type: String,
    public val data: Data,
    public val deliveryId: Long? = null,
)

@Serializable
public data class ReaderTypedHostRequest<Params>(
    public val protocolVersion: Int,
    public val requestId: Long,
    public val type: String,
    public val operationId: Long,
    public val capability: ReaderHostCapability,
    public val params: Params,
    public val deliveryId: Long? = null,
)

/** The method identity and codecs retained by the original pending request. */
public class ReaderRPC<Params, Data>(
    public val method: ReaderCommandMethod,
    public val paramsSerializer: KSerializer<Params>,
    public val resultSerializer: KSerializer<Data>,
) {
    public fun encode(requestId: Long, params: Params): String =
        encode(ReaderProtocolJson, requestId, params)

    public fun encode(json: Json, requestId: Long, params: Params): String =
        json.encodeToString(ReaderTypedCommand.serializer(paramsSerializer), ReaderTypedCommand(requestId, method, params))

    public fun decode(bytes: String): ReaderTypedResult<Data> =
        decode(ReaderProtocolJson, bytes)

    public fun decode(json: Json, bytes: String): ReaderTypedResult<Data> =
        json.decodeFromString(ReaderTypedResult.serializer(resultSerializer), bytes)
}

public object ReaderM1 {
    public val coreInfo: ReaderRPC<EmptyParams, CoreInfoData> = ReaderRPC(
        ReaderCommandMethod.CORE_INFO, EmptyParams.serializer(), CoreInfoData.serializer(),
    )
    public val runtimeStatus: ReaderRPC<RuntimeStatusParams, RuntimeStatusData> = ReaderRPC(
        ReaderCommandMethod.RUNTIME_STATUS, RuntimeStatusParams.serializer(), RuntimeStatusData.serializer(),
    )
    public val runtimeSetHostCapabilities: ReaderRPC<RuntimeSetHostCapabilitiesParams, RuntimeSetHostCapabilitiesData> = ReaderRPC(
        ReaderCommandMethod.RUNTIME_SET_HOST_CAPABILITIES, RuntimeSetHostCapabilitiesParams.serializer(), RuntimeSetHostCapabilitiesData.serializer(),
    )
    public val runtimeHostSmoke: ReaderRPC<M1HostSmokeParams, M1HostEchoData> = ReaderRPC(
        ReaderCommandMethod.RUNTIME_HOST_SMOKE, M1HostSmokeParams.serializer(), M1HostEchoData.serializer(),
    )
    public val hostComplete: ReaderRPC<M1HostCompleteParams, EmptyParams> = ReaderRPC(
        ReaderCommandMethod.HOST_COMPLETE, M1HostCompleteParams.serializer(), EmptyParams.serializer(),
    )
    public val hostError: ReaderRPC<HostErrorParams, EmptyParams> = ReaderRPC(
        ReaderCommandMethod.HOST_ERROR, HostErrorParams.serializer(), EmptyParams.serializer(),
    )
    public val importParse: ReaderRPC<ImportLocalBookParseParams, ImportLocalBookParseData> = ReaderRPC(
        ReaderCommandMethod.IMPORT_PARSE, ImportLocalBookParseParams.serializer(), ImportLocalBookParseData.serializer(),
    )
    public val importPersist: ReaderRPC<ImportLocalBookPersistParams, ImportLocalBookPersistData> = ReaderRPC(
        ReaderCommandMethod.IMPORT_PERSIST, ImportLocalBookPersistParams.serializer(), ImportLocalBookPersistData.serializer(),
    )
    public val importRollback: ReaderRPC<ImportLocalBookRollbackParams, ImportLocalBookRollbackData> = ReaderRPC(
        ReaderCommandMethod.IMPORT_ROLLBACK, ImportLocalBookRollbackParams.serializer(), ImportLocalBookRollbackData.serializer(),
    )
    public val importFinalize: ReaderRPC<ImportLocalBookFinalizeParams, ImportLocalBookFinalizeData> = ReaderRPC(
        ReaderCommandMethod.IMPORT_FINALIZE, ImportLocalBookFinalizeParams.serializer(), ImportLocalBookFinalizeData.serializer(),
    )
    public val bookshelfAdd: ReaderRPC<BookshelfAddParams, BookshelfAddData> = ReaderRPC(
        ReaderCommandMethod.BOOKSHELF_ADD, BookshelfAddParams.serializer(), BookshelfAddData.serializer(),
    )
    public val bookshelfList: ReaderRPC<BookshelfListParams, BookshelfListData> = ReaderRPC(
        ReaderCommandMethod.BOOKSHELF_LIST, BookshelfListParams.serializer(), BookshelfListData.serializer(),
    )
    public val bookshelfGet: ReaderRPC<BookshelfGetParams, BookshelfGetData> = ReaderRPC(
        ReaderCommandMethod.BOOKSHELF_GET, BookshelfGetParams.serializer(), BookshelfGetData.serializer(),
    )
    public val bookshelfBookUpdate: ReaderRPC<BookshelfBookUpdateParams, BookshelfBookUpdateData> = ReaderRPC(
        ReaderCommandMethod.BOOKSHELF_BOOK_UPDATE, BookshelfBookUpdateParams.serializer(), BookshelfBookUpdateData.serializer(),
    )
    public val localBookToc: ReaderRPC<LocalBookTocParams, LocalBookTocData> = ReaderRPC(
        ReaderCommandMethod.LOCAL_BOOK_TOC, LocalBookTocParams.serializer(), LocalBookTocData.serializer(),
    )
    public val localBookChapterContent: ReaderRPC<LocalBookChapterContentParams, LocalBookChapterContentData> = ReaderRPC(
        ReaderCommandMethod.LOCAL_BOOK_CHAPTER_CONTENT, LocalBookChapterContentParams.serializer(), LocalBookChapterContentData.serializer(),
    )
    public val localBookChapterPresentationV1: ReaderRPC<LocalBookChapterPresentationParams, LocalBookChapterPresentationData> = ReaderRPC(
        ReaderCommandMethod.LOCAL_BOOK_CHAPTER_PRESENTATION_V1, LocalBookChapterPresentationParams.serializer(), LocalBookChapterPresentationData.serializer(),
    )
    public val localBookPresentationKindV1: ReaderRPC<LocalBookTocParams, LocalBookPresentationKindData> = ReaderRPC(
        ReaderCommandMethod.LOCAL_BOOK_PRESENTATION_KIND_V1, LocalBookTocParams.serializer(), LocalBookPresentationKindData.serializer(),
    )
    public val readingProgressGet: ReaderRPC<TextReadingProgressGetParams, ReadingProgressGetData> = ReaderRPC(
        ReaderCommandMethod.READING_PROGRESS_GET, TextReadingProgressGetParams.serializer(), ReadingProgressGetData.serializer(),
    )
    public val readingProgressUpdate: ReaderRPC<TextReadingProgressUpdateParams, ReadingProgressUpdateData> = ReaderRPC(
        ReaderCommandMethod.READING_PROGRESS_UPDATE, TextReadingProgressUpdateParams.serializer(), ReadingProgressUpdateData.serializer(),
    )
    public val bookmarkCreate: ReaderRPC<BookmarkCreateParams, BookmarkCreateData> = ReaderRPC(
        ReaderCommandMethod.BOOKMARK_CREATE, BookmarkCreateParams.serializer(), BookmarkCreateData.serializer(),
    )
    public val bookmarkList: ReaderRPC<BookmarkListParams, BookmarkListData> = ReaderRPC(
        ReaderCommandMethod.BOOKMARK_LIST, BookmarkListParams.serializer(), BookmarkListData.serializer(),
    )
    public val bookmarkUpdate: ReaderRPC<BookmarkUpdateParams, BookmarkUpdateData> = ReaderRPC(
        ReaderCommandMethod.BOOKMARK_UPDATE, BookmarkUpdateParams.serializer(), BookmarkUpdateData.serializer(),
    )
    public val bookmarkDelete: ReaderRPC<BookmarkDeleteParams, BookmarkDeleteData> = ReaderRPC(
        ReaderCommandMethod.BOOKMARK_DELETE, BookmarkDeleteParams.serializer(), BookmarkDeleteData.serializer(),
    )
    public val bookmarkOperationQuery: ReaderRPC<BookmarkOperationQueryParams, BookmarkOperationQueryData> = ReaderRPC(
        ReaderCommandMethod.BOOKMARK_OPERATION_QUERY, BookmarkOperationQueryParams.serializer(), BookmarkOperationQueryData.serializer(),
    )
    public val runtimeHostSmokeTimeNow: ReaderRPC<M1TimeNowSmokeParams, HostTimeNowResponse> = ReaderRPC(
        ReaderCommandMethod.RUNTIME_HOST_SMOKE, M1TimeNowSmokeParams.serializer(), HostTimeNowResponse.serializer(),
    )
    public val runtimeHostSmokeCookieGet: ReaderRPC<M1CookieGetSmokeParams, HostCookieGetResponse> = ReaderRPC(
        ReaderCommandMethod.RUNTIME_HOST_SMOKE, M1CookieGetSmokeParams.serializer(), HostCookieGetResponse.serializer(),
    )
    public val hostCompleteTimeNow: ReaderRPC<M1HostTimeCompleteParams, EmptyParams> = ReaderRPC(
        ReaderCommandMethod.HOST_COMPLETE, M1HostTimeCompleteParams.serializer(), EmptyParams.serializer(),
    )
    public val bookshelfRemove: ReaderRPC<BookshelfRemoveParams, BookshelfRemoveData> = ReaderRPC(
        ReaderCommandMethod.BOOKSHELF_REMOVE, BookshelfRemoveParams.serializer(), BookshelfRemoveData.serializer(),
    )
    public val bookshelfRemoveBatch: ReaderRPC<BookshelfRemoveBatchParams, BookshelfRemoveBatchData> = ReaderRPC(
        ReaderCommandMethod.BOOKSHELF_REMOVE_BATCH, BookshelfRemoveBatchParams.serializer(), BookshelfRemoveBatchData.serializer(),
    )
    public val bookmarkOperationSettle: ReaderRPC<ReadingOperationBinding, BookmarkOperationReceipt> = ReaderRPC(
        ReaderCommandMethod.BOOKMARK_OPERATION_SETTLE, ReadingOperationBinding.serializer(), BookmarkOperationReceipt.serializer(),
    )
    public val readingProgressGetManga: ReaderRPC<MangaProgressGetParams, MangaProgressState> = ReaderRPC(
        ReaderCommandMethod.READING_PROGRESS_GET, MangaProgressGetParams.serializer(), MangaProgressState.serializer(),
    )
    public val readingProgressUpdateManga: ReaderRPC<MangaProgressUpdateParams, MangaProgressState> = ReaderRPC(
        ReaderCommandMethod.READING_PROGRESS_UPDATE, MangaProgressUpdateParams.serializer(), MangaProgressState.serializer(),
    )
}

@Serializable
public class EmptyParams

@Serializable
public data class CoreInfoData(
    public val abiVersion: Long,
    public val protocolVersion: Long,
    public val buildVersion: String,
    public val buildIdentity: CoreBuildIdentity,
    public val capabilities: List<String>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sqliteUserVersion: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val storageFormat: CoreStorageFormatInfo? = null,
)

@Serializable
public data class CoreBuildIdentity(
    public val schemaVersion: Long,
    public val buildId: String,
    public val gitCommit: String,
    public val gitDirty: Boolean,
    public val cargoLockSha256: String,
    public val protocolSha256: String,
    public val rustProfile: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val storageCompatibility: CoreBuildIdentityStorageCompatibility? = null,
)

@Serializable
public data class CoreBuildIdentityStorageCompatibility(
    public val sqliteMaxUserVersion: Long,
    public val sqliteStorageSourceSha256: String,
)

@Serializable
public data class CoreStorageFormatInfo(
    public val format: CoreStorageFormatInfoFormat,
    public val portableSnapshotSupported: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val capabilities: CoreStorageCapabilities? = null,
)

/** Wire values of CoreStorageFormatInfoFormat. */
@Serializable
public enum class CoreStorageFormatInfoFormat(public val wireValue: String) {
    @SerialName("legacy") LEGACY("legacy"),
    @SerialName("library47") LIBRARY47("library47"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, CoreStorageFormatInfoFormat> =
            values().associateBy(CoreStorageFormatInfoFormat::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): CoreStorageFormatInfoFormat? = BY_WIRE[value]
    }
}

@Serializable
public data class CoreStorageCapabilities(
    public val logicalExport: Boolean,
    public val logicalRestore: Boolean,
    public val legacyHostImport: Boolean,
    public val physicalBackup: Boolean,
    public val physicalRestore: Boolean,
    public val webdavConfigRestore: Boolean,
    public val activeLibrarySync: Boolean,
    public val logicalExportRequiresDataPreflight: Boolean,
    public val logicalRestoreRequiresCompleteCoverage: Boolean,
    public val resourceEvidenceOnly: Boolean,
    public val externalFilesIncluded: Boolean,
)

@Serializable
public data class RuntimeStatusParams(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val includeCapacity: Boolean? = null,
)

@Serializable
public data class RuntimeStatusData(
    public val activeRequestCount: Long,
    public val activeRequestIds: List<Long>,
    public val pendingHostOperationCount: Long,
    public val pendingHostOperations: List<PendingHostOperationStatus>,
    public val shuttingDown: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val capacity: RuntimeCapacityStatusData? = null,
)

@Serializable
public data class PendingHostOperationStatus(
    public val operationId: Long,
    public val requestId: Long,
    public val capability: ReaderHostCapability,
    public val state: String,
)

@Serializable
public data class RuntimeCapacityStatusData(
    public val current: RuntimeCapacityUsageData,
    public val peak: RuntimeCapacityUsageData,
)

@Serializable
public data class RuntimeCapacityUsageData(
    public val retainedRequestCount: Long,
    public val queuedCommandCount: Long,
    public val queuedInputBytes: Long,
    public val inputReservedBytes: Long,
    public val resultReservedBytes: Long,
    public val hostReservedBytes: Long,
    public val pendingDeliveryCount: Long,
    public val hostOperationCount: Long,
)

@Serializable
public data class RuntimeSetHostCapabilitiesParams(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val extensions: List<String>? = null,
    public val capabilities: List<ReaderHostCapability>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val lanes: List<HostLane>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val platform: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val platformVersion: String? = null,
)

/** Wire values of HostLane. */
@Serializable
public enum class HostLane(public val wireValue: String) {
    @SerialName("anti_bot") ANTI_BOT("anti_bot"),
    @SerialName("login_cookie") LOGIN_COOKIE("login_cookie"),
    @SerialName("webview_render") WEBVIEW_RENDER("webview_render"),
    @SerialName("media_download") MEDIA_DOWNLOAD("media_download"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, HostLane> =
            values().associateBy(HostLane::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): HostLane? = BY_WIRE[value]
    }
}

@Serializable
public data class RuntimeSetHostCapabilitiesData(
    public val applied: Boolean,
    public val capabilities: Long,
    public val lanes: Long,
)

@Serializable
public data class M1HostSmokeParams(
    @Required
    public val capability: M1HostSmokeParamsCapability = M1HostSmokeParamsCapability.HOST_SMOKE_ECHO,
    public val params: M1HostSmokePayload,
)

/** Wire values of M1HostSmokeParamsCapability. */
@Serializable
public enum class M1HostSmokeParamsCapability(public val wireValue: String) {
    @SerialName("host.smoke.echo") HOST_SMOKE_ECHO("host.smoke.echo"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, M1HostSmokeParamsCapability> =
            values().associateBy(M1HostSmokeParamsCapability::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): M1HostSmokeParamsCapability? = BY_WIRE[value]
    }
}

@Serializable
public data class M1HostSmokePayload(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val message: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val clock: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val timezone: String? = null,
)

@Serializable
public data class M1HostEchoData(
    public val message: String,
)

@Serializable
public data class M1HostCompleteParams(
    public val operationId: Long,
    public val result: M1HostEchoData,
)

@Serializable
public data class HostErrorParams(
    public val operationId: Long,
    public val error: ReaderCoreError,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val diagnostics: HostErrorDiagnostics? = null,
)

@Serializable
public data class HostErrorDiagnostics(
    public val code: HostErrorCode,
    public val phase: HostErrorPhase,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val message: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val details: JsonObject? = null,
)

/** Wire values of HostErrorCode. */
@Serializable
public enum class HostErrorCode(public val wireValue: String) {
    @SerialName("CAPABILITY_UNAVAILABLE") CAPABILITY_UNAVAILABLE("CAPABILITY_UNAVAILABLE"),
    @SerialName("PERMISSION_DENIED") PERMISSION_DENIED("PERMISSION_DENIED"),
    @SerialName("TIMEOUT") TIMEOUT("TIMEOUT"),
    @SerialName("NETWORK_ERROR") NETWORK_ERROR("NETWORK_ERROR"),
    @SerialName("TLS_ERROR") TLS_ERROR("TLS_ERROR"),
    @SerialName("HTTP_ERROR") HTTP_ERROR("HTTP_ERROR"),
    @SerialName("INVALID_RESPONSE") INVALID_RESPONSE("INVALID_RESPONSE"),
    @SerialName("CANCELLED") CANCELLED("CANCELLED"),
    @SerialName("CHALLENGE_REQUIRED") CHALLENGE_REQUIRED("CHALLENGE_REQUIRED"),
    @SerialName("INTERNAL") INTERNAL("INTERNAL"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, HostErrorCode> =
            values().associateBy(HostErrorCode::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): HostErrorCode? = BY_WIRE[value]
    }
}

/** Wire values of HostErrorPhase. */
@Serializable
public enum class HostErrorPhase(public val wireValue: String) {
    @SerialName("request") REQUEST("request"),
    @SerialName("transport") TRANSPORT("transport"),
    @SerialName("response") RESPONSE("response"),
    @SerialName("decode") DECODE("decode"),
    @SerialName("storage") STORAGE("storage"),
    @SerialName("runtime") RUNTIME("runtime"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, HostErrorPhase> =
            values().associateBy(HostErrorPhase::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): HostErrorPhase? = BY_WIRE[value]
    }
}

@Serializable
public data class ImportLocalBookParseParams(
    @Required
    public val kind: ImportLocalBookParseParamsKind = ImportLocalBookParseParamsKind.LOCAL_BOOK,
    public val input: LocalBookParseParams,
)

/** Wire values of ImportLocalBookParseParamsKind. */
@Serializable
public enum class ImportLocalBookParseParamsKind(public val wireValue: String) {
    @SerialName("localBook") LOCAL_BOOK("localBook"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ImportLocalBookParseParamsKind> =
            values().associateBy(ImportLocalBookParseParamsKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ImportLocalBookParseParamsKind? = BY_WIRE[value]
    }
}

@Serializable
public data class LocalBookParseParams(
    public val bookId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val author: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val fileName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val filePath: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val text: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bytesBase64: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val format: String? = null,
)

@Serializable
public data class ImportLocalBookParseData(
    @Required
    public val kind: ImportLocalBookParseDataKind = ImportLocalBookParseDataKind.LOCAL_BOOK,
    public val preview: ImportLocalBookPreview,
)

/** Wire values of ImportLocalBookParseDataKind. */
@Serializable
public enum class ImportLocalBookParseDataKind(public val wireValue: String) {
    @SerialName("localBook") LOCAL_BOOK("localBook"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ImportLocalBookParseDataKind> =
            values().associateBy(ImportLocalBookParseDataKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ImportLocalBookParseDataKind? = BY_WIRE[value]
    }
}

@Serializable
public data class ImportLocalBookPreview(
    public val input: LocalBookParseParams,
    public val summary: LocalBookImportData,
    public val contentSha256: String,
)

@Serializable
public data class LocalBookImportData(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val integrity: LocalBookIntegrity? = null,
    public val book: LocalBookImportBook,
    public val format: String,
    public val encoding: String,
    public val byteLen: Long,
    public val charLen: Long,
    public val chapterCount: Long,
    public val toc: List<BookTocEntryData>,
)

@Serializable
public data class LocalBookIntegrity(
    public val schemaVersion: Long,
    public val parserVersion: String,
    public val readability: LocalBookIntegrityReadability,
    public val sourceDocumentCount: Long,
    public val warnings: List<String>,
)

/** Wire values of LocalBookIntegrityReadability. */
@Serializable
public enum class LocalBookIntegrityReadability(public val wireValue: String) {
    @SerialName("complete") COMPLETE("complete"),
    @SerialName("recoverable") RECOVERABLE("recoverable"),
    @SerialName("metadataOnly") METADATA_ONLY("metadataOnly"),
    @SerialName("unreadable") UNREADABLE("unreadable"),
    @SerialName("unverified") UNVERIFIED("unverified"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, LocalBookIntegrityReadability> =
            values().associateBy(LocalBookIntegrityReadability::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): LocalBookIntegrityReadability? = BY_WIRE[value]
    }
}

@Serializable
public data class LocalBookImportBook(
    public val bookId: String,
    public val title: String,
    public val author: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val coverUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val intro: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val kind: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val lastChapter: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val variables: Map<String, String>? = null,
)

@Serializable
public data class BookTocEntryData(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val displayTitle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val contextTitle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val isVip: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val isPay: Boolean? = null,
    public val index: Long,
    public val title: String,
    public val url: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val level: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val variables: Map<String, String>? = null,
)

@Serializable
public data class ImportLocalBookPersistParams(
    public val transactionId: String,
    public val parsed: ImportLocalBookParseData,
)

@Serializable
public data class ImportLocalBookPersistData(
    public val persisted: ImportLocalBookPersistedData,
    public val rollbackToken: ImportLocalBookRollbackToken,
)

@Serializable
public data class ImportLocalBookPersistedData(
    @Required
    public val kind: ImportLocalBookPersistedDataKind = ImportLocalBookPersistedDataKind.LOCAL_BOOK,
    public val `data`: LocalBookImportData,
)

/** Wire values of ImportLocalBookPersistedDataKind. */
@Serializable
public enum class ImportLocalBookPersistedDataKind(public val wireValue: String) {
    @SerialName("localBook") LOCAL_BOOK("localBook"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ImportLocalBookPersistedDataKind> =
            values().associateBy(ImportLocalBookPersistedDataKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ImportLocalBookPersistedDataKind? = BY_WIRE[value]
    }
}

@Serializable
public data class ImportLocalBookRollbackToken(
    @Required
    public val kind: ImportLocalBookRollbackTokenKind = ImportLocalBookRollbackTokenKind.LOCAL_BOOK,
    public val token: ImportRollbackTokenBody,
)

/** Wire values of ImportLocalBookRollbackTokenKind. */
@Serializable
public enum class ImportLocalBookRollbackTokenKind(public val wireValue: String) {
    @SerialName("localBook") LOCAL_BOOK("localBook"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ImportLocalBookRollbackTokenKind> =
            values().associateBy(ImportLocalBookRollbackTokenKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ImportLocalBookRollbackTokenKind? = BY_WIRE[value]
    }
}

@Serializable
public data class ImportRollbackTokenBody(
    public val transactionId: String,
    public val journal: JsonElement,
)

@Serializable
public data class ImportLocalBookRollbackParams(
    public val rollbackToken: ImportLocalBookRollbackToken,
)

@Serializable
public data class ImportLocalBookRollbackData(
    @Required
    public val kind: ImportLocalBookRollbackDataKind = ImportLocalBookRollbackDataKind.LOCAL_BOOK,
    public val `data`: ImportLocalBookRollbackDataData,
)

/** Wire values of ImportLocalBookRollbackDataKind. */
@Serializable
public enum class ImportLocalBookRollbackDataKind(public val wireValue: String) {
    @SerialName("localBook") LOCAL_BOOK("localBook"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ImportLocalBookRollbackDataKind> =
            values().associateBy(ImportLocalBookRollbackDataKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ImportLocalBookRollbackDataKind? = BY_WIRE[value]
    }
}

@Serializable
public data class ImportLocalBookRollbackDataData(
    @SerialName("book_id")
    public val bookId: String,
    public val restored: Boolean,
)

@Serializable
public data class ImportLocalBookFinalizeParams(
    public val rollbackToken: ImportLocalBookRollbackToken,
)

@Serializable
public data class ImportLocalBookFinalizeData(
    @Required
    public val kind: ImportLocalBookFinalizeDataKind = ImportLocalBookFinalizeDataKind.LOCAL_BOOK,
    public val `data`: ImportLocalBookFinalizeDataData,
)

/** Wire values of ImportLocalBookFinalizeDataKind. */
@Serializable
public enum class ImportLocalBookFinalizeDataKind(public val wireValue: String) {
    @SerialName("localBook") LOCAL_BOOK("localBook"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ImportLocalBookFinalizeDataKind> =
            values().associateBy(ImportLocalBookFinalizeDataKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ImportLocalBookFinalizeDataKind? = BY_WIRE[value]
    }
}

@Serializable
public data class ImportLocalBookFinalizeDataData(
    public val bookId: String,
    public val finalized: Boolean,
)

@Serializable
public data class BookshelfAddParams(
    public val sourceId: String,
    public val bookId: String,
    public val title: String,
    public val author: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val coverUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val intro: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val kind: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val lastChapter: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val group: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sortIndex: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val mangaFirstImage: MangaFirstImageReceipt? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val preparationRevision: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val requireReadable: Boolean? = null,
)

@Serializable
public data class MangaFirstImageReceipt(
    public val chapter: MangaChapterIdentity,
    public val chapterIndex: Long,
    public val width: Long,
    public val height: Long,
    public val manifestVersion: String,
    public val sourceRuleVersion: String,
    public val decodeRevision: String,
    public val pageId: String,
    public val resourceRef: String,
    public val imageRevision: String,
)

@Serializable
public data class MangaChapterIdentity(
    public val sourceId: String,
    public val bookId: String,
    public val chapterId: String,
)

@Serializable
public data class BookshelfAddData(
    public val sourceId: String,
    public val bookId: String,
    public val created: Boolean,
    public val addedAt: Long,
)

@Serializable
public data class BookshelfListParams(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val anchor: BookshelfGetParams? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val pageProjection: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val membershipOnly: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val projectionRevision: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val readingState: BookshelfListParamsReadingState? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sourceKind: BookshelfListParamsSourceKind? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val contentKind: BookshelfListParamsContentKind? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sourceId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val group: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val keyword: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val hasReadingProgress: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sortBy: BookshelfListParamsSortBy? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sortDirection: BookshelfListParamsSortDirection? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val offset: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val limit: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val hasGroup: Boolean? = null,
)

@Serializable
public data class BookshelfGetParams(
    public val sourceId: String,
    public val bookId: String,
)

/** Wire values of BookshelfListParamsReadingState. */
@Serializable
public enum class BookshelfListParamsReadingState(public val wireValue: String) {
    @SerialName("all") ALL("all"),
    @SerialName("unread") UNREAD("unread"),
    @SerialName("reading") READING("reading"),
    @SerialName("finished") FINISHED("finished"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookshelfListParamsReadingState> =
            values().associateBy(BookshelfListParamsReadingState::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookshelfListParamsReadingState? = BY_WIRE[value]
    }
}

/** Wire values of BookshelfListParamsSourceKind. */
@Serializable
public enum class BookshelfListParamsSourceKind(public val wireValue: String) {
    @SerialName("all") ALL("all"),
    @SerialName("local") LOCAL("local"),
    @SerialName("online") ONLINE("online"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookshelfListParamsSourceKind> =
            values().associateBy(BookshelfListParamsSourceKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookshelfListParamsSourceKind? = BY_WIRE[value]
    }
}

/** Wire values of BookshelfListParamsContentKind. */
@Serializable
public enum class BookshelfListParamsContentKind(public val wireValue: String) {
    @SerialName("all") ALL("all"),
    @SerialName("text") TEXT("text"),
    @SerialName("manga") MANGA("manga"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookshelfListParamsContentKind> =
            values().associateBy(BookshelfListParamsContentKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookshelfListParamsContentKind? = BY_WIRE[value]
    }
}

/** Wire values of BookshelfListParamsSortBy. */
@Serializable
public enum class BookshelfListParamsSortBy(public val wireValue: String) {
    @SerialName("manual") MANUAL("manual"),
    @SerialName("addedAt") ADDED_AT("addedAt"),
    @SerialName("lastReadAt") LAST_READ_AT("lastReadAt"),
    @SerialName("title") TITLE("title"),
    @SerialName("author") AUTHOR("author"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookshelfListParamsSortBy> =
            values().associateBy(BookshelfListParamsSortBy::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookshelfListParamsSortBy? = BY_WIRE[value]
    }
}

/** Wire values of BookshelfListParamsSortDirection. */
@Serializable
public enum class BookshelfListParamsSortDirection(public val wireValue: String) {
    @SerialName("ascending") ASCENDING("ascending"),
    @SerialName("descending") DESCENDING("descending"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookshelfListParamsSortDirection> =
            values().associateBy(BookshelfListParamsSortDirection::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookshelfListParamsSortDirection? = BY_WIRE[value]
    }
}

@Serializable
public data class BookshelfListData(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val offset: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val anchorFound: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val unfilteredTotal: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val unfilteredOnlineTotal: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val projectionRevision: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val changed: Boolean? = null,
    public val books: List<BookshelfEntryData>,
    public val total: Long,
)

@Serializable
public data class BookshelfEntryData(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val readingPosition: BookshelfReadingPositionData? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sourceName: String? = null,
    public val sourceId: String,
    public val bookId: String,
    public val title: String,
    public val author: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val coverUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val intro: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val kind: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val lastChapter: String? = null,
    public val addedAt: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val lastReadAt: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val group: String? = null,
    public val sortIndex: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val contentKind: BookshelfEntryDataContentKind? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val unreadCount: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val lastCheckAt: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val currentChapterTitle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val currentChapterIndex: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterCount: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val readProgress: Long? = null,
)

@Serializable
public data class BookshelfReadingPositionData(
    public val chapterIndex: Long,
    public val chapterOffset: Long,
    public val updatedAt: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val locationRevision: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bodyVersion: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val processingVersion: String? = null,
)

/** Wire values of BookshelfEntryDataContentKind. */
@Serializable
public enum class BookshelfEntryDataContentKind(public val wireValue: String) {
    @SerialName("text") TEXT("text"),
    @SerialName("manga") MANGA("manga"),
    @SerialName("audio") AUDIO("audio"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookshelfEntryDataContentKind> =
            values().associateBy(BookshelfEntryDataContentKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookshelfEntryDataContentKind? = BY_WIRE[value]
    }
}

@Serializable
public data class BookshelfGetData(
    public val book: BookshelfEntryData?,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val original: LocalBookOriginalData? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val originalExpectation: LocalBookOriginalExpectationData? = null,
)

@Serializable
public data class LocalBookOriginalData(
    public val schemaVersion: Long,
    public val provenance: String,
    public val bookId: String,
    public val sha256: String,
    public val byteLength: Long,
    public val sourceFormat: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val fileName: String? = null,
)

@Serializable
public data class LocalBookOriginalExpectationData(
    public val schemaVersion: Long,
    public val original: LocalBookOriginalData,
    public val basis: String,
)

@Serializable
public data class BookshelfBookUpdateParams(
    public val sourceId: String,
    public val bookId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val author: PatchField<String> = PatchField.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val coverUrl: PatchField<String> = PatchField.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val intro: PatchField<String> = PatchField.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val kind: PatchField<String> = PatchField.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val lastChapter: PatchField<String> = PatchField.Absent,
)

@Serializable
public data class BookshelfBookUpdateData(
    public val book: BookshelfEntryData,
)

@Serializable
public data class LocalBookTocParams(
    public val bookId: String,
)

@Serializable
public data class LocalBookTocData(
    public val sourceId: String,
    public val bookId: String,
    public val toc: List<BookTocEntryData>,
)

@Serializable
public data class LocalBookChapterContentParams(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val directoryTargetProof: DirectoryTargetProof? = null,
    public val bookId: String,
    public val chapterIndex: Long,
)

@Serializable
public data class DirectoryTargetProof(
    public val sourceId: String,
    public val bookId: String,
    public val catalogRevision: String,
    public val structureRevision: String,
    public val nodeId: String,
    public val chapterIndex: Long,
    public val url: String,
)

@Serializable
public data class LocalBookChapterContentData(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bodyVersion: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val processingVersion: String? = null,
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val chapterTitle: String,
    public val content: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val blocks: List<ReadingDocumentBlockData>? = null,
)

@Serializable(with = ReadingDocumentBlockDataSerializer::class)
public sealed interface ReadingDocumentBlockData

public object ReadingDocumentBlockDataSerializer : JsonContentPolymorphicSerializer<ReadingDocumentBlockData>(ReadingDocumentBlockData::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ReadingDocumentBlockData> =
        when ((element as? JsonObject)?.get("kind")?.jsonPrimitive?.content) {
            "text" -> ReadingDocumentBlockDataText.serializer()
            "image" -> ReadingDocumentBlockDataImage.serializer()
            else -> throw SerializationException("Unknown ReadingDocumentBlockData kind")
        }
}

@Serializable
public data class ReadingDocumentBlockDataText(
    @Required
    public val kind: ReadingDocumentBlockDataTextKind = ReadingDocumentBlockDataTextKind.TEXT,
    public val text: String,
    public val startScalar: Long,
    public val endScalar: Long,
) : ReadingDocumentBlockData

/** Wire values of ReadingDocumentBlockDataTextKind. */
@Serializable
public enum class ReadingDocumentBlockDataTextKind(public val wireValue: String) {
    @SerialName("text") TEXT("text"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ReadingDocumentBlockDataTextKind> =
            values().associateBy(ReadingDocumentBlockDataTextKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ReadingDocumentBlockDataTextKind? = BY_WIRE[value]
    }
}

@Serializable
public data class ReadingDocumentBlockDataImage(
    @Required
    public val kind: ReadingDocumentBlockDataImageKind = ReadingDocumentBlockDataImageKind.IMAGE,
    public val source: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val imageWidthBasisPoints: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val imageIntrinsicWidth: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val imageIntrinsicHeight: Long? = null,
    public val startScalar: Long,
    public val endScalar: Long,
) : ReadingDocumentBlockData

/** Wire values of ReadingDocumentBlockDataImageKind. */
@Serializable
public enum class ReadingDocumentBlockDataImageKind(public val wireValue: String) {
    @SerialName("image") IMAGE("image"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, ReadingDocumentBlockDataImageKind> =
            values().associateBy(ReadingDocumentBlockDataImageKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): ReadingDocumentBlockDataImageKind? = BY_WIRE[value]
    }
}

@Serializable
public data class LocalBookChapterPresentationParams(
    public val bookId: String,
    public val chapterIndex: Long,
    public val localSourcePath: String,
)

@Serializable
public data class LocalBookChapterPresentationData(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val bodyVersion: String,
    public val processingVersion: String,
    public val entryPath: String,
    public val documentCanonicalContent: String,
    public val chapterStartScalar: Long,
    public val chapterEndScalar: Long,
)

@Serializable
public data class LocalBookPresentationKindData(
    @Required
    public val sourceId: LocalBookPresentationKindDataSourceId = LocalBookPresentationKindDataSourceId.LOCAL,
    public val bookId: String,
    public val kind: LocalBookPresentationKindDataKind,
    public val format: String,
    public val reimportRequired: Boolean,
)

/** Wire values of LocalBookPresentationKindDataSourceId. */
@Serializable
public enum class LocalBookPresentationKindDataSourceId(public val wireValue: String) {
    @SerialName("local") LOCAL("local"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, LocalBookPresentationKindDataSourceId> =
            values().associateBy(LocalBookPresentationKindDataSourceId::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): LocalBookPresentationKindDataSourceId? = BY_WIRE[value]
    }
}

/** Wire values of LocalBookPresentationKindDataKind. */
@Serializable
public enum class LocalBookPresentationKindDataKind(public val wireValue: String) {
    @SerialName("text") TEXT("text"),
    @SerialName("ebook") EBOOK("ebook"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, LocalBookPresentationKindDataKind> =
            values().associateBy(LocalBookPresentationKindDataKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): LocalBookPresentationKindDataKind? = BY_WIRE[value]
    }
}

@Serializable
public data class TextReadingProgressGetParams(
    public val bookId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sourceId: String? = null,
) : ReadingProgressGetParams

@Serializable
public data class ReadingProgressGetData(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val progressRevision: String? = null,
    public val found: Boolean,
    public val progress: ReadingProgressData?,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val pendingRestore: LocalPositionPendingRestoreData? = null,
)

@Serializable
public data class ReadingProgressData(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val chapterOffset: Long,
    public val chapterProgress: Double,
    public val updatedAt: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val deviceId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val locationRevision: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bodyVersion: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val processingVersion: String? = null,
)

@Serializable
public data class LocalPositionPendingRestoreData(
    public val archiveIds: List<String>,
    public val progressEntries: Long,
    public val legacyProgressEntries: Long,
    public val historyEntries: Long,
    public val bookmarkEntries: Long,
    public val highlightEntries: Long,
    public val currentProgressUnverified: Boolean,
)

@Serializable
public data class TextReadingProgressUpdateParams(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val directoryTargetProof: DirectoryTargetProof? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val expectedProgressRevision: String? = null,
    public val bookId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sourceId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val deviceId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val updatedAt: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterIndex: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterOffset: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterProgress: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val anchor: ReaderLocationAnchorData? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val layout: ReaderLocationLayoutData? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterTitle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val locationRevision: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val transactionId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val expectedBodyVersion: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val expectedProcessingVersion: String? = null,
) : ReadingProgressUpdateParams

@Serializable
public data class ReaderLocationAnchorData(
    public val chapterOffset: Long,
    public val chapterProgress: Double,
)

@Serializable
public data class ReaderLocationLayoutData(
    public val viewportWidth: Long,
    public val viewportHeight: Long,
    public val fontScale: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val lineHeight: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val pageIndex: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val pageCount: Long? = null,
)

@Serializable
public data class ReadingProgressUpdateData(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val progressRevision: String? = null,
    public val bookId: String,
    public val sourceId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val deviceId: String? = null,
    public val updatedAt: Long,
    public val chapterIndex: Long,
    public val chapterOffset: Long,
    public val chapterProgress: Double,
    public val stored: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val locationRevision: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val transactionId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sourceSwitchFinalized: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bodyVersion: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val processingVersion: String? = null,
)

@Serializable
public data class BookmarkCreateParams(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val time: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookAuthor: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterIndex: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterPos: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookText: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val content: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val positionScope: RemoteContentPositionScope? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val operationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sequence: Long? = null,
)

@Serializable
public data class RemoteContentPositionScope(
    public val sourceId: String,
    public val bookId: String,
    public val chapterIndex: Long,
    public val bodyVersion: String,
    public val processingVersion: String,
)

@Serializable
public data class BookmarkCreateData(
    public val bookmark: BookmarkData,
)

@Serializable
public data class BookmarkData(
    public val time: Long,
    public val bookName: String,
    public val bookAuthor: String,
    public val chapterIndex: Long,
    public val chapterPos: Long,
    public val chapterName: String,
    public val bookText: String,
    public val content: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val positionScope: RemoteContentPositionScope? = null,
)

@Serializable
public data class BookmarkListParams(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookAuthor: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val query: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sortBy: BookmarkListParamsSortBy? = null,
)

/** Wire values of BookmarkListParamsSortBy. */
@Serializable
public enum class BookmarkListParamsSortBy(public val wireValue: String) {
    @SerialName("time") TIME("time"),
    @SerialName("bookLocation") BOOK_LOCATION("bookLocation"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookmarkListParamsSortBy> =
            values().associateBy(BookmarkListParamsSortBy::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookmarkListParamsSortBy? = BY_WIRE[value]
    }
}

@Serializable
public data class BookmarkListData(
    public val bookmarks: List<BookmarkData>,
)

@Serializable
public data class BookmarkUpdateParams(
    public val time: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookAuthor: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterIndex: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterPos: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val chapterName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val bookText: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val content: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val positionScope: RemoteContentPositionScope? = null,
)

@Serializable
public data class BookmarkUpdateData(
    public val bookmark: BookmarkData,
)

@Serializable
public data class BookmarkDeleteParams(
    public val time: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val operationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sequence: Long? = null,
)

@Serializable
public data class BookmarkDeleteData(
    public val time: Long,
    public val deleted: Boolean,
)

@Serializable
public data class BookmarkOperationQueryParams(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val operationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val expected: BookmarkOperationExpected? = null,
)

@Serializable(with = BookmarkOperationExpectedSerializer::class)
public sealed interface BookmarkOperationExpected

public object BookmarkOperationExpectedSerializer : JsonContentPolymorphicSerializer<BookmarkOperationExpected>(BookmarkOperationExpected::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<BookmarkOperationExpected> =
        when ((element as? JsonObject)?.get("action")?.jsonPrimitive?.content) {
            "create" -> BookmarkOperationExpectedCreate.serializer()
            "delete" -> BookmarkOperationExpectedDelete.serializer()
            else -> throw SerializationException("Unknown BookmarkOperationExpected kind")
        }
}

@Serializable
public data class BookmarkOperationExpectedCreate(
    @Required
    public val action: BookmarkOperationExpectedCreateAction = BookmarkOperationExpectedCreateAction.CREATE,
    public val params: BookmarkCreateParams,
) : BookmarkOperationExpected

/** Wire values of BookmarkOperationExpectedCreateAction. */
@Serializable
public enum class BookmarkOperationExpectedCreateAction(public val wireValue: String) {
    @SerialName("create") CREATE("create"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookmarkOperationExpectedCreateAction> =
            values().associateBy(BookmarkOperationExpectedCreateAction::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookmarkOperationExpectedCreateAction? = BY_WIRE[value]
    }
}

@Serializable
public data class BookmarkOperationExpectedDelete(
    @Required
    public val action: BookmarkOperationExpectedDeleteAction = BookmarkOperationExpectedDeleteAction.DELETE,
    public val params: BookmarkDeleteParams,
) : BookmarkOperationExpected

/** Wire values of BookmarkOperationExpectedDeleteAction. */
@Serializable
public enum class BookmarkOperationExpectedDeleteAction(public val wireValue: String) {
    @SerialName("delete") DELETE("delete"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookmarkOperationExpectedDeleteAction> =
            values().associateBy(BookmarkOperationExpectedDeleteAction::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookmarkOperationExpectedDeleteAction? = BY_WIRE[value]
    }
}

@Serializable
public data class BookmarkOperationQueryData(
    public val head: ReadingOperationBinding?,
    public val receipt: BookmarkOperationReceipt?,
)

@Serializable
public data class ReadingOperationBinding(
    public val operationId: String,
    public val sequence: Long,
    public val payloadFingerprint: String,
)

@Serializable
public data class BookmarkOperationReceipt(
    public val binding: ReadingOperationBinding,
    public val phase: BookmarkOperationReceiptPhase,
    public val outcome: BookmarkOperationResult,
)

/** Wire values of BookmarkOperationReceiptPhase. */
@Serializable
public enum class BookmarkOperationReceiptPhase(public val wireValue: String) {
    @SerialName("committed") COMMITTED("committed"),
    @SerialName("settled") SETTLED("settled"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookmarkOperationReceiptPhase> =
            values().associateBy(BookmarkOperationReceiptPhase::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookmarkOperationReceiptPhase? = BY_WIRE[value]
    }
}

@Serializable(with = BookmarkOperationResultSerializer::class)
public sealed interface BookmarkOperationResult

public object BookmarkOperationResultSerializer : JsonContentPolymorphicSerializer<BookmarkOperationResult>(BookmarkOperationResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<BookmarkOperationResult> =
        when ((element as? JsonObject)?.get("action")?.jsonPrimitive?.content) {
            "create" -> BookmarkOperationResultCreate.serializer()
            "delete" -> BookmarkOperationResultDelete.serializer()
            else -> throw SerializationException("Unknown BookmarkOperationResult kind")
        }
}

@Serializable
public data class BookmarkOperationResultCreate(
    @Required
    public val action: BookmarkOperationResultCreateAction = BookmarkOperationResultCreateAction.CREATE,
    public val result: BookmarkCreateData,
) : BookmarkOperationResult

/** Wire values of BookmarkOperationResultCreateAction. */
@Serializable
public enum class BookmarkOperationResultCreateAction(public val wireValue: String) {
    @SerialName("create") CREATE("create"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookmarkOperationResultCreateAction> =
            values().associateBy(BookmarkOperationResultCreateAction::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookmarkOperationResultCreateAction? = BY_WIRE[value]
    }
}

@Serializable
public data class BookmarkOperationResultDelete(
    @Required
    public val action: BookmarkOperationResultDeleteAction = BookmarkOperationResultDeleteAction.DELETE,
    public val result: BookmarkDeleteData,
) : BookmarkOperationResult

/** Wire values of BookmarkOperationResultDeleteAction. */
@Serializable
public enum class BookmarkOperationResultDeleteAction(public val wireValue: String) {
    @SerialName("delete") DELETE("delete"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, BookmarkOperationResultDeleteAction> =
            values().associateBy(BookmarkOperationResultDeleteAction::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): BookmarkOperationResultDeleteAction? = BY_WIRE[value]
    }
}

@Serializable
public data class M1TimeNowSmokeParams(
    @Required
    public val capability: M1TimeNowSmokeParamsCapability = M1TimeNowSmokeParamsCapability.TIME_NOW,
    public val params: HostTimeNowRequest,
)

/** Wire values of M1TimeNowSmokeParamsCapability. */
@Serializable
public enum class M1TimeNowSmokeParamsCapability(public val wireValue: String) {
    @SerialName("time.now") TIME_NOW("time.now"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, M1TimeNowSmokeParamsCapability> =
            values().associateBy(M1TimeNowSmokeParamsCapability::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): M1TimeNowSmokeParamsCapability? = BY_WIRE[value]
    }
}

@Serializable
public data class HostTimeNowRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val clock: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val timezone: String? = null,
)

@Serializable
public data class HostTimeNowResponse(
    public val unixMillis: Long,
    public val iso8601: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val timezone: String? = null,
)

@Serializable
public data class M1CookieGetSmokeParams(
    @Required
    public val capability: M1CookieGetSmokeParamsCapability = M1CookieGetSmokeParamsCapability.COOKIE_GET,
    public val params: HostCookieGetRequest,
)

/** Wire values of M1CookieGetSmokeParamsCapability. */
@Serializable
public enum class M1CookieGetSmokeParamsCapability(public val wireValue: String) {
    @SerialName("cookie.get") COOKIE_GET("cookie.get"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, M1CookieGetSmokeParamsCapability> =
            values().associateBy(M1CookieGetSmokeParamsCapability::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): M1CookieGetSmokeParamsCapability? = BY_WIRE[value]
    }
}

@Serializable
public data class HostCookieGetRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val domain: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sessionId: String? = null,
)

@Serializable
public data class HostCookieGetResponse(
    public val cookies: List<HostCookieRecord>,
)

@Serializable
public data class HostCookieRecord(
    public val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val `value`: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val domain: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val path: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val expiresAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val httpOnly: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val secure: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val sameSite: String? = null,
)

@Serializable
public data class M1HostTimeCompleteParams(
    public val operationId: Long,
    public val result: HostTimeNowResponse,
)

@Serializable
public data class BookshelfRemoveParams(
    public val sourceId: String,
    public val bookId: String,
)

@Serializable
public data class BookshelfRemoveData(
    public val removed: Boolean,
)

@Serializable
public data class BookshelfRemoveBatchParams(
    public val targets: List<BookshelfRemoveBatchTarget>,
)

@Serializable
public data class BookshelfRemoveBatchTarget(
    public val sourceId: String,
    public val bookId: String,
)

@Serializable
public data class BookshelfRemoveBatchData(
    public val requestedCount: Long,
    public val uniqueCount: Long,
    public val removedTargets: List<BookshelfRemoveBatchTarget>,
    public val missingTargets: List<BookshelfRemoveBatchTarget>,
    public val duplicateTargets: List<BookshelfRemoveBatchTarget>,
)

@Serializable
public data class MangaProgressGetParams(
    @Required
    public val kind: MangaProgressGetParamsKind = MangaProgressGetParamsKind.MANGA,
    public val sourceId: String,
    public val bookId: String,
) : ReadingProgressGetParams

/** Wire values of MangaProgressGetParamsKind. */
@Serializable
public enum class MangaProgressGetParamsKind(public val wireValue: String) {
    @SerialName("manga") MANGA("manga"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, MangaProgressGetParamsKind> =
            values().associateBy(MangaProgressGetParamsKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): MangaProgressGetParamsKind? = BY_WIRE[value]
    }
}

@Serializable
public data class MangaProgressState(
    public val token: MangaProgressToken,
    public val location: MangaLocation?,
)

@Serializable
public data class MangaProgressToken(
    public val epoch: Long,
    public val revision: Long,
)

@Serializable
public data class MangaLocation(
    @Required
    public val kind: MangaLocationKind = MangaLocationKind.MANGA,
    public val chapter: MangaChapterIdentity,
    public val manifestVersion: String,
    public val pageId: String,
    public val pageOrdinalFallback: Long,
    public val x: Double,
    public val y: Double,
    public val progressRevision: Long,
)

/** Wire values of MangaLocationKind. */
@Serializable
public enum class MangaLocationKind(public val wireValue: String) {
    @SerialName("manga") MANGA("manga"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, MangaLocationKind> =
            values().associateBy(MangaLocationKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): MangaLocationKind? = BY_WIRE[value]
    }
}

@Serializable
public data class MangaProgressUpdateParams(
    @Required
    public val kind: MangaProgressUpdateParamsKind = MangaProgressUpdateParamsKind.MANGA,
    public val location: MangaLocation,
    public val expectedEpoch: Long,
    public val expectedRevision: Long,
) : ReadingProgressUpdateParams

/** Wire values of MangaProgressUpdateParamsKind. */
@Serializable
public enum class MangaProgressUpdateParamsKind(public val wireValue: String) {
    @SerialName("manga") MANGA("manga"),
    ;
    public companion object {
        private val BY_WIRE: Map<String, MangaProgressUpdateParamsKind> =
            values().associateBy(MangaProgressUpdateParamsKind::wireValue)
        /** Returns null for a wire value outside the frozen v1 schema. */
        public fun fromWire(value: String): MangaProgressUpdateParamsKind? = BY_WIRE[value]
    }
}

@Serializable(with = ReadingProgressGetParamsSerializer::class)
public sealed interface ReadingProgressGetParams

public object ReadingProgressGetParamsSerializer : JsonContentPolymorphicSerializer<ReadingProgressGetParams>(ReadingProgressGetParams::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ReadingProgressGetParams> =
        when ((element as? JsonObject)?.get("kind")?.jsonPrimitive?.content) {
            null -> TextReadingProgressGetParams.serializer()
            "manga" -> MangaProgressGetParams.serializer()
            else -> throw SerializationException("Unknown ReadingProgressGetParams kind")
        }
}

@Serializable(with = ReadingProgressUpdateParamsSerializer::class)
public sealed interface ReadingProgressUpdateParams

public object ReadingProgressUpdateParamsSerializer : JsonContentPolymorphicSerializer<ReadingProgressUpdateParams>(ReadingProgressUpdateParams::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ReadingProgressUpdateParams> =
        when ((element as? JsonObject)?.get("kind")?.jsonPrimitive?.content) {
            null -> TextReadingProgressUpdateParams.serializer()
            "manga" -> MangaProgressUpdateParams.serializer()
            else -> throw SerializationException("Unknown ReadingProgressUpdateParams kind")
        }
}

@Serializable
public data class ReaderM1ErrorEvent(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val deliveryId: Long? = null,
    public val protocolVersion: Long,
    public val requestId: Long,
    public val type: String,
    public val error: ReaderM1Error,
)

@Serializable
public data class ReaderM1Error(
    public val code: ReaderErrorCode,
    public val message: String,
    public val retryable: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val details: ReaderM1ErrorDetails? = null,
)

@Serializable
public data class ReaderM1ErrorDetails(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val reason: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val host: ReaderM1HostErrorDetails? = null,
)

@Serializable
public data class ReaderM1HostErrorDetails(
    public val capability: ReaderHostCapability,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val operationId: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val requestId: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val diagnostics: HostErrorDiagnostics? = null,
)
