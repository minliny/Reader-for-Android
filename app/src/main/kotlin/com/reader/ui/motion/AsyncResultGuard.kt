package com.reader.ui.motion

/**
 * 异步结果防覆盖守卫
 *
 * 契约来源：MOTION_CONTRACT.md 行 240 motion.async.resultGuard
 * 每个异步结果必须带 requestId、from/to、stack/context；
 * 只有仍匹配当前 route/context 的结果才能替换内容，
 * 过期结果写入 discarded/cancelled 状态且不得覆盖新页面。
 */
class AsyncResultGuard<T> {

    enum class AsyncState {
        PENDING,      // 请求中
        COMPLETED,    // 完成，匹配当前 route/context
        CANCELLED,    // 用户取消
        DISCARDED,    // 过期，不匹配当前 route/context
        SUPERSEDED    // 被新请求取代
    }

    /**
     * inner class 才能访问外层 AsyncResultGuard<T> 的类型参数 T
     */
    inner class AsyncResult(
        val requestId: String,
        val fromRoute: String,
        val toRoute: String,
        val state: AsyncState,
        val value: T? = null,
        val error: Throwable? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun copy(
            requestId: String = this.requestId,
            fromRoute: String = this.fromRoute,
            toRoute: String = this.toRoute,
            state: AsyncState = this.state,
            value: T? = this.value,
            error: Throwable? = this.error,
            timestamp: Long = this.timestamp
        ): AsyncResult = AsyncResult(requestId, fromRoute, toRoute, state, value, error, timestamp)
    }

    private val pendingResults = mutableMapOf<String, AsyncResult>()  // requestId -> result
    private var currentRequestId: String? = null
    private val listeners = mutableListOf<(AsyncResult) -> Unit>()

    /**
     * 发起新异步请求，返回 requestId
     * 之前的 pending 请求自动标记为 SUPERSEDED
     */
    fun startRequest(fromRoute: String, toRoute: String): String {
        val requestId = generateRequestId()
        // 旧 pending 标记 SUPERSEDED
        pendingResults.values
            .filter { it.state == AsyncState.PENDING }
            .forEach {
                pendingResults[it.requestId] = it.copy(state = AsyncState.SUPERSEDED)
                notify(it.copy(state = AsyncState.SUPERSEDED))
            }
        val result = AsyncResult(requestId, fromRoute, toRoute, AsyncState.PENDING)
        pendingResults[requestId] = result
        currentRequestId = requestId
        notify(result)
        return requestId
    }

    /**
     * 异步结果返回，校验是否仍匹配当前 route/context
     * 不匹配则标记 DISCARDED，不覆盖新页面
     */
    fun completeRequest(requestId: String, value: T, currentRoute: String): AsyncResult {
        val pending = pendingResults[requestId] ?: return AsyncResult(
            requestId = requestId,
            fromRoute = "",
            toRoute = currentRoute,
            state = AsyncState.DISCARDED,
            timestamp = System.currentTimeMillis()
        )

        // 校验：requestId 是否仍是当前活跃请求
        val isCurrent = requestId == currentRequestId
        // 校验：toRoute 是否匹配当前 route
        val routeMatches = pending.toRoute == currentRoute

        val finalState = when {
            !isCurrent -> AsyncState.DISCARDED
            !routeMatches -> AsyncState.DISCARDED
            else -> AsyncState.COMPLETED
        }

        val finalResult = pending.copy(state = finalState, value = value)
        pendingResults[requestId] = finalResult
        if (finalState == AsyncState.COMPLETED) {
            currentRequestId = null
        }
        notify(finalResult)
        return finalResult
    }

    /**
     * 用户取消
     */
    fun cancelRequest(requestId: String) {
        pendingResults[requestId]?.let {
            val cancelled = it.copy(state = AsyncState.CANCELLED)
            pendingResults[requestId] = cancelled
            if (requestId == currentRequestId) currentRequestId = null
            notify(cancelled)
        }
    }

    /**
     * 用户切 route 时调用，所有 pending 标记 DISCARDED
     */
    fun onRouteChanged(newRoute: String) {
        pendingResults.values
            .filter { it.state == AsyncState.PENDING && it.toRoute != newRoute }
            .forEach {
                val discarded = it.copy(state = AsyncState.DISCARDED)
                pendingResults[it.requestId] = discarded
                notify(discarded)
            }
    }

    fun currentState(): AsyncState? = currentRequestId?.let {
        pendingResults[it]?.state
    }

    fun addListener(listener: (AsyncResult) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (AsyncResult) -> Unit) {
        listeners.remove(listener)
    }

    private fun notify(result: AsyncResult) {
        listeners.forEach { it(result) }
    }

    private fun generateRequestId(): String {
        return "req-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }

    fun snapshot(): List<AsyncResult> = pendingResults.values.toList()
}
