package io.reader.android.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.os.Parcelable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LineHeightSpan
import android.util.TypedValue
import android.util.SparseArray
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import io.reader.android.runtime.ReaderChapter
import io.reader.android.runtime.ReaderOnlineChapter
import io.reader.android.runtime.ReaderTextPresentationKind
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import io.reader.android.R
import io.reader.android.runtime.ReaderTextCoordinates
import kotlin.math.roundToInt

private enum class NativeLayoutPhase { Restoring, Ready, Failed }

/** Presentation input only; never a local format/Original or an online business session. */
private data class ReaderNativeTextOwner(val identity: Long, val chapter: ReaderChapter, val visibleScalarOffset: Int, val requiresVisibleAdmission: Boolean = false)

/** Existing local entry retains its signature, rendering and Unit callback semantics. */
@Composable
internal fun ReaderNativeChapterBody(reading: ReadingSession, fontSize: Int, paper: ReaderPaper,
    userScrollEnabled: Boolean, onVisibleAnchor: (Int) -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    ReaderNativeTextPresentationBody(ReaderNativeTextOwner(reading.identity, reading.chapter, reading.visibleScalarOffset),
        fontSize, paper, userScrollEnabled, { onVisibleAnchor(it); true }, onTap, modifier)
}

/** Stored-source text uses the SAME native body; accepted means display admission, never Core ACK. */
@Composable
internal fun ReaderOnlineNativeChapterBody(readingOwner: Long, opened: ReaderOnlineChapter, visibleScalarOffset: Int,
    fontSize: Int, paper: ReaderPaper, userScrollEnabled: Boolean, onVisibleAnchor: (Int) -> Boolean,
    onTap: () -> Unit, modifier: Modifier = Modifier, onReady: () -> Unit = {}) {
    val chapter = opened.chapter
    check(chapter.sourceId != "local" && chapter.presentationKind == ReaderTextPresentationKind.ONLINE_TEXT && visibleScalarOffset in 0..chapter.scalarCount)
    key(readingOwner, chapter.sourceId, chapter.bookId, chapter.sessionToken) {
        ReaderNativeTextPresentationBody(ReaderNativeTextOwner(readingOwner, chapter, visibleScalarOffset, requiresVisibleAdmission = true),
            fontSize, paper, userScrollEnabled, onVisibleAnchor, onTap, modifier, onReady)
    }
}

/** One native text/selection owner per immutable reading session; never reused across keys. */
@Composable
private fun ReaderNativeTextPresentationBody(
    reading: ReaderNativeTextOwner,
    fontSize: Int,
    paper: ReaderPaper,
    userScrollEnabled: Boolean,
    onVisibleAnchor: (Int) -> Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onReady: (() -> Unit)? = null,
) {
    var phase by remember { mutableStateOf(NativeLayoutPhase.Restoring) }
    val readyCallback by rememberUpdatedState(onReady)
    LaunchedEffect(phase, reading.identity, reading.chapter.sessionToken) {
        // This phase is emitted only after the existing native UTF-16/visible-line/OnDraw gate.
        if (phase == NativeLayoutPhase.Ready) readyCallback?.invoke()
    }
    val native = remember { arrayOfNulls<ReaderNativeScrollView>(1) }
    Box(modifier.testTag("reader_body")) {
        AndroidView(
            factory = { context -> ReaderNativeScrollView(context, reading).also { native[0] = it } },
            modifier = Modifier.fillMaxSize(),
            onReset = null,
            onRelease = { view -> view.release(); if (native[0] === view) native[0] = null },
            update = { view ->
                view.bind(reading, fontSize, paper, userScrollEnabled, onVisibleAnchor, onTap) { phase = it }
            },
        )
        if (phase != NativeLayoutPhase.Ready) Surface(color = paper.background(),
            modifier = Modifier.fillMaxSize().testTag("reader_restore_loading")) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (phase == NativeLayoutPhase.Failed) ReaderPageState("暂时无法恢复阅读位置", action = "重试排版",
                    onAction = { native[0]?.restoreLogicalAnchor() })
                else CircularProgressIndicator()
            }
        }
    }
}

/** Native paragraph decoration; the underlying UTF-16 text is never rewritten. */
private class NativeParagraphGap(private val pixels: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int,
        lineHeightV: Int, fm: Paint.FontMetricsInt) {
        if (end > start && text[end - 1] == '\n') {
            fm.descent += pixels
            fm.bottom += pixels
        }
    }
}

private class ReaderNativeScrollView(context: Context, private val owner: ReaderNativeTextOwner) : ScrollView(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private val titleView = TextView(context).apply {
        id = R.id.reader_native_title
        typeface = ResourcesCompat.getFont(context, R.font.reader_serif_bold)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
        includeFontPadding = false
        setPadding(0, dp(32), 0, dp(12))
        setTextIsSelectable(false)
        text = owner.chapter.title
    }
    private val bodyView = TextView(context).apply {
        id = R.id.reader_native_text
        typeface = ResourcesCompat.getFont(context, R.font.reader_serif_regular)
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        setTextIsSelectable(true)
        setHorizontallyScrolling(false)
    }
    private val rawText = owner.chapter.text
    private var font = -1
    private var released = false
    private var scrollingAllowed = true
    private var logicalScalar = owner.visibleScalarOffset
    private var restoring = true
    private var pendingPlacement = true
    private var epoch = 0L
    private var rawVerifiedEpoch = -1L
    private var finishEpoch = -1L
    private var finishPostedEpoch = -1L
    private var userGestureObserved = false
    private var userMoved = false
    private var anchor: (Int) -> Boolean = { false }
    private var tap: () -> Unit = {}
    private var phase: (NativeLayoutPhase) -> Unit = {}
    private val timeout: Runnable = Runnable {
        if (!released && restoring) {
            pendingPlacement = false
            removeCallbacks(finish)
            phase(NativeLayoutPhase.Failed)
        }
    }
    private val finish: Runnable = Runnable {
        if (!released && finishEpoch == epoch && finishPostedEpoch == epoch && rawVerifiedEpoch == epoch && isAttachedToWindow &&
            !bodyView.isLayoutRequested && logicalLineIntersectsViewport()) {
            restoring = false
            userGestureObserved = false
            userMoved = false
            phase(NativeLayoutPhase.Ready)
            removeCallbacks(timeout)
        } else if (!released && finishEpoch == epoch && finishPostedEpoch == epoch) {
            pendingPlacement = true
            requestLayout()
        }
    }
    private val drawObserver = ViewTreeObserver.OnDrawListener {
        if (!released && restoring && !pendingPlacement && finishEpoch == epoch &&
            rawVerifiedEpoch == epoch && finishPostedEpoch != epoch) {
            finishPostedEpoch = epoch
            // Handler work posted from OnDraw runs after this frame's pre-draw
            // caret requests. Keep restoration ownership until then; an animation
            // callback would release it before those TextView requests execute.
            post(finish)
        }
    }
    private val layoutObserver = ViewTreeObserver.OnGlobalLayoutListener {
        val layout = bodyView.layout
        if (!released && restoring && pendingPlacement && isAttachedToWindow && !bodyView.isLayoutRequested &&
            layout != null && bodyView.width == width - paddingLeft - paddingRight) {
            if (rawVerifiedEpoch != epoch) {
                if (!rawText.contentEquals(layout.text)) {
                    pendingPlacement = false
                    phase(NativeLayoutPhase.Failed)
                    return@OnGlobalLayoutListener
                }
                rawVerifiedEpoch = epoch
            }
            val utf16 = ReaderTextCoordinates.utf16Offset(rawText, logicalScalar)
            val line = layout.getLineForOffset(utf16)
            pendingPlacement = false
            scrollTo(0, bodyView.top + bodyView.totalPaddingTop + layout.getLineTop(line))
            finishEpoch = epoch
            finishPostedEpoch = -1L
            removeCallbacks(finish)
            invalidate()
        }
    }
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            if (!released && !restoring && !bodyView.hasSelection()) {
                // Stop with the platform's own scroller, then capture its final
                // actual line before opening controls. A tap alone never rebases.
                fling(0)
                if (userMoved) publishVisibleAnchor()
                tap()
            }
            return false
        }
    })

    init {
        id = R.id.reader_native_scroll
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        isSmoothScrollingEnabled = false
        clipToPadding = true
        setPadding(dp(22), 0, dp(22), dp(52))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(bodyView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        viewTreeObserver.addOnGlobalLayoutListener(layoutObserver)
        viewTreeObserver.addOnDrawListener(drawObserver)
    }

    // Core/ReadingSession owns the logical position. AndroidView otherwise
    // restores ScrollView pixels and TextView selection from a second hierarchy
    // state, which can override the current scalar during Activity recreation.
    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) = Unit
    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) = Unit

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        // Selectable TextView's pre-draw can bring its initial/restored caret
        // into view after our global-layout placement. During that one restore,
        // the logical anchor has priority. Normal selection keeps native scroll.
        if (restoring) return false
        return super.requestChildRectangleOnScreen(child, rectangle, immediate)
    }

    private fun logicalLineIntersectsViewport(): Boolean {
        val layout = bodyView.layout ?: return false
        if (height <= paddingTop + paddingBottom) return false
        val utf16 = ReaderTextCoordinates.utf16Offset(rawText, logicalScalar)
        val line = layout.getLineForOffset(utf16)
        val top = bodyView.top + bodyView.totalPaddingTop - bodyView.scrollY + layout.getLineTop(line)
        val bottom = bodyView.top + bodyView.totalPaddingTop - bodyView.scrollY + layout.getLineBottom(line)
        return top < scrollY + height - paddingBottom && bottom > scrollY + paddingTop
    }

    fun bind(reading: ReaderNativeTextOwner, fontSize: Int, paper: ReaderPaper, scrollEnabled: Boolean,
        onAnchor: (Int) -> Boolean, onTap: () -> Unit, onPhase: (NativeLayoutPhase) -> Unit) {
        check(reading.identity == owner.identity && reading.chapter.key == owner.chapter.key && reading.chapter.sessionToken == owner.chapter.sessionToken)
        if (released) return
        anchor = onAnchor
        tap = onTap
        phase = onPhase
        if (scrollingAllowed && !scrollEnabled) {
            fling(0)
            if (userMoved && !restoring) publishVisibleAnchor()
        }
        scrollingAllowed = scrollEnabled
        val ink = paper.ink().toArgb()
        setBackgroundColor(paper.background().toArgb())
        titleView.setTextColor(ink)
        bodyView.setTextColor(ink)
        if (font != fontSize) {
            restoring = true
            font = fontSize
            bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
            val lineHeight = bodyView.textSize * 1.8f
            bodyView.setLineSpacing(lineHeight - bodyView.paint.fontSpacing, 1f)
            bodyView.text = SpannableString(rawText).apply {
                rawText.forEachIndexed { index, char -> if (char == '\n')
                    setSpan(NativeParagraphGap(dp(12)), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            restoreLogicalAnchor()
        }
    }

    fun restoreLogicalAnchor() {
        if (released) return
        epoch++
        restoring = true
        pendingPlacement = true
        userGestureObserved = false
        userMoved = false
        phase(NativeLayoutPhase.Restoring)
        removeCallbacks(finish)
        finishPostedEpoch = -1L
        removeCallbacks(timeout)
        postDelayed(timeout, 5_000)
        requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!released && (w != oldw || h != oldh)) restoreLogicalAnchor()
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            userGestureObserved = false
            userMoved = false
        }
        if (!released) gestures.onTouchEvent(event) // Observe only; native widgets own dispatch/selection.
        // The control host gates user interaction only. Programmatic native
        // layout restoration still uses scrollTo, never this touch path.
        if (released || !scrollingAllowed || restoring) return true
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (released || !scrollingAllowed || restoring) return false
        val intercepted = super.onInterceptTouchEvent(event)
        if (intercepted && event.actionMasked == MotionEvent.ACTION_MOVE && !bodyView.hasSelection())
            userGestureObserved = true
        return intercepted
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (released || !scrollingAllowed || restoring) return false
        val before = scrollY
        val handled = super.onTouchEvent(event)
        // A stream that started on blank/non-clickable content can be owned by
        // ScrollView directly, without a child interception callback. Its actual
        // synchronous native MOVE is the admission proof, not our own slop math.
        if (handled && event.actionMasked == MotionEvent.ACTION_MOVE && scrollY != before && !bodyView.hasSelection()) {
            userGestureObserved = true
            userMoved = true
            publishVisibleAnchor()
        }
        return handled
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (released || restoring || !userGestureObserved || t == oldt) return
        if (bodyView.hasSelection()) {
            userGestureObserved = false
            userMoved = false
            return
        }
        userMoved = true
        publishVisibleAnchor() // Includes the platform fling's final onScrollChanged frame.
    }

    private fun publishVisibleAnchor() {
        val layout = bodyView.layout ?: return
        // The private read-only body was checked once for this layout epoch;
        // scroll frames only read native line coordinates, not the full text.
        if (released || restoring || !isAttachedToWindow || rawVerifiedEpoch != epoch) return
        val y = (scrollY - bodyView.top - bodyView.totalPaddingTop).coerceAtLeast(0)
        val utf16 = layout.getLineStart(layout.getLineForVertical(y))
        val scalar = ReaderTextCoordinates.scalarOffset(rawText, utf16)
        if (logicalScalar != scalar) {
            if (owner.requiresVisibleAdmission) {
                if (anchor(scalar)) logicalScalar = scalar // Only accepted remote display observations become a reflow baseline.
            } else {
                logicalScalar = scalar; anchor(scalar) // Preserve the existing local Unit callback order.
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        epoch++
        removeCallbacks(timeout)
        removeCallbacks(finish)
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalLayoutListener(layoutObserver)
            viewTreeObserver.removeOnDrawListener(drawObserver)
        }
        bodyView.clearFocus()
        bodyView.setTextIsSelectable(false)
        anchor = { false }; tap = {}; phase = {}
    }
}
