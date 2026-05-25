package com.reader.android.ui.discover

/**
 * Fake/real boundary for Discover / RSS module.
 */
object DiscoverAdapterShell {

    enum class Mode { FAKE, REAL }
    var mode: Mode = Mode.FAKE
        private set

    private val rssAdapter = RssLocalAdapter()

    init {
        rssAdapter.addSubscription(RssSubscriptionLocal("s1", "科幻世界 RSS", "https://example.com/rss/sf"))
        rssAdapter.addSubscription(RssSubscriptionLocal("s2", "网文周报 RSS", "https://example.com/rss/weekly"))
        rssAdapter.setArticles(RssLocalAdapter.fixtureArticles())
    }

    fun subscriptions(): List<RssSubscriptionLocal> = rssAdapter.listSubscriptions()
    fun articles(): List<RssArticleLocal> = rssAdapter.listArticles()
    fun subscriptionCount(): Int = rssAdapter.subscriptionCount()

    val isFakeMode: Boolean get() = mode == Mode.FAKE
    fun enableRealMode() { mode = Mode.REAL }
    fun resetToFakeMode() { mode = Mode.FAKE }
}
