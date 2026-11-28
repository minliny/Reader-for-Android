package io.reader.rebuild.features.source

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.reader.core.protocol.M2HostErrorCode
import com.reader.core.protocol.M2SourceListEntry
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.host.network.LoginNavigation
import io.reader.rebuild.host.network.NetworkHostFailure
import io.reader.rebuild.host.network.SourceLoginSession
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SourceLoginScreen(
    source: M2SourceListEntry,
    coordinator: SourceManagementCoordinator,
    onBack: () -> Unit,
    onFailure: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var attempt by remember(source.sourceId) { mutableIntStateOf(0) }
    var session by remember(source.sourceId) { mutableStateOf<SourceLoginSession?>(null) }
    var openingFailure by remember(source.sourceId) { mutableStateOf<PageState.Failure?>(null) }
    var finishing by remember(source.sourceId) { mutableStateOf(false) }
    LaunchedEffect(source.sourceId, attempt) {
        openingFailure = null
        val opened = try {
            coordinator.beginLogin(source.sourceId)
        } catch (_: TimeoutCancellationException) {
            openingFailure = PageState.Failure("登录页加载超时", attempt.toLong())
            return@LaunchedEffect
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            openingFailure = if ((failure as? NetworkHostFailure)?.code == M2HostErrorCode.CAPABILITY_UNAVAILABLE) {
                PageState.Failure("当前系统组件无法隔离此书源的登录会话", null)
            } else PageState.Failure("无法打开登录页", attempt.toLong())
            return@LaunchedEffect
        }
        session = opened
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { coordinator.dismissLogin(opened) }
        }
    }
    val current = session
    val navigation = if (current == null) null else current.navigation.collectAsState().value
    val failure = openingFailure
    val projection = navigation?.projection() ?: SourceBrowserState(
        address = "登录页", secure = false, loading = failure == null,
        canRefresh = failure?.actionGeneration != null,
    )
    val done: () -> Unit = {
        if (current == null || navigation?.retired == true) {
            onBack()
        } else if (!finishing) {
            finishing = true
            scope.launch {
                try {
                    coordinator.finishLogin(current)
                    onBack()
                } catch (failure: CancellationException) {
                    if (current.navigation.value.retired) onBack()
                    else throw failure
                } catch (_: Exception) {
                    onFailure()
                    onBack()
                } finally {
                    finishing = false
                }
            }
        }
    }
    BackHandler {
        if (navigation?.canGoBack == true && !navigation.retired && !finishing) current?.goBack() else done()
    }
    SourceBrowserContent(source.name, projection, { action ->
        when (action) {
            SourceBrowserAction.BACK -> current?.goBack()
            SourceBrowserAction.FORWARD -> current?.goForward()
            SourceBrowserAction.REFRESH -> {
                if (failure?.actionGeneration != null) attempt++ else current?.refresh()
            }
            SourceBrowserAction.DONE -> done()
        }
    }) {
        when {
            navigation?.retired == true -> ReaderPageState(PageState.Empty("该书源的登录会话已关闭"))
            failure != null -> ReaderPageState(failure, onRetry = { attempt++ })
            current == null -> ReaderPageState(PageState.Loading)
            navigation?.failed == true -> ReaderPageState(
                PageState.Failure("登录页未能加载", attempt.toLong()), onRetry = { current.refresh() },
            )
            else -> AndroidView(
                factory = {
                    current.view.apply { (parent as? ViewGroup)?.removeView(this) }
                },
                modifier = Modifier.fillMaxSize().testTag("source.browser.web"),
            )
        }
    }
}

private fun LoginNavigation.projection(): SourceBrowserState {
    val uri = runCatching { URI(url) }.getOrNull()
    val host = uri?.host.orEmpty()
    val address = if (host.isEmpty()) "登录页" else buildString {
        append(host)
        if (uri != null && uri.port != -1) append(":${uri.port}")
        append(uri?.rawPath.orEmpty())
    }
    return SourceBrowserState(address, uri?.scheme.equals("https", ignoreCase = true),
        canGoBack && !retired, canGoForward && !retired, loading, progress, canRefresh = !retired)
}
