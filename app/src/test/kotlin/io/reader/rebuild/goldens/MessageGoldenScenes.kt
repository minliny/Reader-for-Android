package io.reader.rebuild.goldens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderConfirmation
import io.reader.rebuild.design.ReaderConfirmationState
import io.reader.rebuild.design.ReaderMessageOverlay
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.ReaderTaskProgress
import io.reader.rebuild.design.ReaderTaskProgressState
import io.reader.rebuild.design.TransientToastState

internal fun messageGoldenScenes() = listOf(
    AndroidGoldenScene("message-toast", "前台宿主短提示", "书签已保存") {
        val state = remember { TransientToastState() }
        LaunchedEffect(Unit) { state.present("golden-bookmark", "书签已保存", true) }
        ReaderMessageOverlay(true, state, null, { _, _, _ -> }, Modifier.fillMaxSize()) {
            ReaderPageState(PageState.Empty("暂无书签"))
        }
    },
    AndroidGoldenScene("message-page-loading", "ReaderPageState 首次加载", "目录首次载入") {
        ReaderPageState(PageState.Loading)
    },
    AndroidGoldenScene("message-page-failed", "ReaderPageState 无缓存失败", "当前离线，目录未缓存") {
        ReaderPageState(PageState.Failure("当前离线，目录未缓存", 1))
    },
    AndroidGoldenScene("message-confirmation", "原生 Compose Dialog 删除确认", "已入架本地小说",
        difference = "将 Material AlertDialog 窗口与其实际 dim 层合成。", dialog = true) {
        ReaderPageState(PageState.Empty("书架"))
        ReaderConfirmation(ReaderConfirmationState("移出书架？", "移出后可重新导入原书。", "移出", 1), true, {}, {})
    },
    AndroidGoldenScene("message-progress", "持续任务进度", "完成 12、失败 2、进行中 3、未执行 3") {
        Box(Modifier.padding(16.dp)) { ReaderTaskProgress(ReaderTaskProgressState("正在下载章节", 12, 2, 3, 3)) }
    },
)
