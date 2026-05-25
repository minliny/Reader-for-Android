package com.reader.android.ui.discover

/**
 * Local RSS subscription and article state. In-memory only.
 * TODO: connect to SubscriptionRepository when DI/runtime available.
 */
data class RssSubscriptionLocal(
    val id: String,
    val title: String,
    val url: String,
    val articleCount: Int = 0,
    val lastUpdated: String = ""
)

data class RssArticleLocal(
    val id: String,
    val title: String,
    val snippet: String,
    val source: String,
    val url: String,
    val publishedAt: String = ""
)

class RssLocalAdapter(
    private val subscriptions: MutableList<RssSubscriptionLocal> = mutableListOf(),
    private val articles: MutableList<RssArticleLocal> = mutableListOf()
) {
    fun addSubscription(sub: RssSubscriptionLocal): Boolean {
        if (sub.url.isBlank()) return false
        subscriptions.add(sub)
        return true
    }

    fun removeSubscription(id: String): Boolean =
        subscriptions.removeAll { it.id == id }

    fun listSubscriptions(): List<RssSubscriptionLocal> = subscriptions.toList()

    fun setArticles(newArticles: List<RssArticleLocal>) {
        articles.clear()
        articles.addAll(newArticles)
    }

    fun listArticles(): List<RssArticleLocal> = articles.toList()

    fun subscriptionCount(): Int = subscriptions.size
    fun articleCount(): Int = articles.size

    companion object {
        fun fixtureArticles(): List<RssArticleLocal> = listOf(
            RssArticleLocal("a1", "科幻世界最新短篇：星门之后", "本期科幻世界带来三篇新作...", "科幻世界 RSS", "fixture://rss/1"),
            RssArticleLocal("a2", "网络文学周报：年度榜单揭晓", "2026年度网络文学榜单正式发布...", "网文周报 RSS", "fixture://rss/2"),
            RssArticleLocal("a3", "读者投稿：深空信号读后感", "关于林间《深空信号》的读者来信精选...", "读者来信 RSS", "fixture://rss/3")
        )
    }
}
