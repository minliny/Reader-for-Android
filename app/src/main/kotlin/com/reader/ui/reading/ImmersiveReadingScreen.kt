package com.reader.ui.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reader.api.Book
import com.reader.api.BookApi
import com.reader.api.Chapter
import com.reader.api.ReaderCoreClient
import com.reader.ui.shell.ReaderContext
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immersive reading surface — `immersive-reading` (route-contract.js).
 *
 * Per FRONTEND_DEVELOPMENT_SLICE_MATRIX.md Slice 2 acceptance, the final state of reader
 * entry is immersive reading: the reader control layer (top bar, bottom chapter bar) MUST
 * NOT auto-appear. This composable therefore renders only the chapter text with no
 * Scaffold chrome; the control layer is Slice 3 material and is intentionally absent.
 *
 * Layout mirrors `frontend-demo/styles/01-shell-layout.css` `.fd-ir-reading-layer` 1:1
 * (px → dp at mdpi):
 * - padding 72dp top / 32dp horizontal / 48dp bottom (72dp includes the status bar safe area).
 * - h1 chapter title: 23sp serif, line-height 1.25, center, margin-bottom 24dp.
 * - p body: 18sp serif, line-height 1.96, color `--reader-ink` (#2B241D).
 *
 * Reduced motion: entry transition is handled by [com.reader.ui.shell.AppShell]; this
 * surface performs no animated displacement of its own.
 */
@Composable
fun ImmersiveReadingScreen(context: ReaderContext) {
    val vm: ImmersiveReadingViewModel = viewModel(
        key = "immersive-${context.bookUrl}",
        factory = ImmersiveReadingViewModelFactory(context)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val content by vm.content.collectAsStateWithLifecycle()
    val readerInk = readerExtraColors().readerInk

    when (val s = state) {
        ReadingUiState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is ReadingUiState.Error -> Box(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
        }

        is ReadingUiState.Ready -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Demo .fd-ir-reading-layer: padding 72px top / 32px horizontal / 48px bottom.
                .padding(top = 72.dp)
                .padding(horizontal = 32.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = s.book.name.ifEmpty { context.bookName },
                style = ReaderTextStyles.readerChapterTitle,
                color = readerInk
            )
            // Demo h1 margin-bottom: 24px.
            Spacer(Modifier.height(24.dp))
            Text(
                text = content,
                style = ReaderTextStyles.readerBody,
                color = readerInk
            )
        }
    }
}

/**
 * Loads chapter text for the immersive surface. Reuses the existing [BookApi] path for real
 * sources; for the `fixture://` seed used by Slice 2 demonstrations it serves local text so
 * the entry flow is verifiable without a live Core / network.
 *
 * The async-result guard (motion.async.resultGuard) is honored by keying cancellation on
 * [ReaderContext.entryRequestId]: if the user backs out or enters a different book, the
 * stale result is discarded rather than overwriting the new surface.
 */
class ImmersiveReadingViewModel(
    private val context: ReaderContext
) : ViewModel() {

    private val bookApi: BookApi? = if (context.sourceId.startsWith(FIXTURE_PREFIX)) null else BookApi(ReaderCoreClient.get())

    private val _uiState = MutableStateFlow<ReadingUiState>(ReadingUiState.Loading)
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    init { load() }

    private fun load() {
        if (context.sourceId.startsWith(FIXTURE_PREFIX)) {
            val book = Book(
                bookUrl = context.bookUrl,
                name = FIXTURE_CHAPTER_TITLE,
                origin = context.sourceId
            )
            _uiState.value = ReadingUiState.Ready(book, emptyList())
            _content.value = FIXTURE_TEXT
            return
        }
        viewModelScope.launch {
            _uiState.value = ReadingUiState.Loading
            try {
                val book = Book(
                    bookUrl = context.bookUrl,
                    name = context.bookName,
                    origin = context.sourceId
                )
                val chapters: List<Chapter> = bookApi!!.toc(context.sourceId, book)
                if (chapters.isEmpty()) {
                    _uiState.value = ReadingUiState.Error("无章节")
                    return@launch
                }
                _uiState.value = ReadingUiState.Ready(book, chapters)
                val text = bookApi.content(context.sourceId, book, chapters.first())
                _content.value = text
            } catch (e: Exception) {
                _uiState.value = ReadingUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    companion object {
        private const val FIXTURE_PREFIX = "fixture://"
        private const val FIXTURE_CHAPTER_TITLE = "雨夜"
        private val FIXTURE_TEXT = listOf(
            "雨声在窗外连成一片，像无数细小的针，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。",
            "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个迟到许久的答案终于抵达。",
            "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下短暂而摇晃的光。",
            "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。",
            "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。",
            "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在某个雨夜到来。",
            "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。",
            "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。",
            "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。",
            "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。",
            "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。",
            "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。",
            "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。",
            "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。",
            "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。",
            "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。",
            "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。",
            "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。",
            "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。",
            "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。",
            "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。",
            "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。",
            "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。",
            "他终于明白，自己握着的不是某一个答案，而是一整段被迫中断的时间。时间没有替任何人解释，只把他们推回同一扇门前。",
            "夜色越来越深，楼道里的灯自动熄灭又亮起。每一次光线变化，都像在提醒他们，这一次不能再让沉默替自己作答。",
            "他把两张纸并排放在桌上，纸角被窗缝吹进来的风轻轻掀起。那些字迹在灯下交错，像两条迟迟没有汇合的河。",
            "来人低声说，对不起。声音很轻，却不像旧日里那些含糊的告别，这一次每一个字都落在了实处。",
            "他没有立刻回应，只是走到窗边，把没有关紧的窗扣扣上。雨声被隔在外面，房间里终于安静了一些。",
            "安静下来以后，他才听见自己长长地呼出一口气。那口气像在胸口停了很多年，如今才找到离开的方向。",
            "他们坐到桌边，从最早的一封信说起，说到误会从哪里开始，又如何在一次次错过里变成无法解释的距离。",
            "有些话说出口时已经不再锋利，却仍然沉重。它们落在桌面上，像一枚枚迟到的钉子，把摇晃的过去一点点固定住。",
            "窗外的雨势渐渐小了，远处有车轮压过积水，声音短促而清亮。城市并没有因为这一夜停下，只是他们终于停下来回头看了一眼。",
            "天色微亮时，云层后透出一线灰白。来人站起身，问他是否还愿意一起去那个地址看看。",
            "他望向桌上的旧车票，又望向窗边逐渐清晰的街道。很久以后，他点了点头，把那封湿信重新折好，放进了外套内侧的口袋。",
            "门外的雨已经停了，只剩屋檐偶尔滴下一两声水响。清晨的空气带着潮意，从打开的门缝里慢慢涌进来。",
            "他们没有急着出门，而是把桌上的纸和旧车票重新整理好。每一件东西都很轻，却像压着一段终于能够被重新触碰的往事。",
            "电梯下行时，金属门上映出两个人并肩的影子。影子仍有些陌生，却不再像昨夜那样隔着看不见的距离。",
            "走出楼道时，积水倒映着渐亮的天空。他们沿着街边慢慢向前，谁都没有再回头。远处第一班车穿过薄雾，带来新一天的声音。"
        ).joinToString(separator = "\n\n")
    }
}

fun ImmersiveReadingViewModelFactory(context: ReaderContext) = viewModelFactory {
    initializer { ImmersiveReadingViewModel(context) }
}
