package io.reader.rebuild.host.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

internal sealed interface SpeechAction {
    data object Play : SpeechAction
    data object Pause : SpeechAction
    data object Stop : SpeechAction
    data object Next : SpeechAction
    data object Previous : SpeechAction
    data class Rate(val percent: Long) : SpeechAction
}

internal data class SpeechMetadata(
    val owner: UUID,
    val title: String,
    val chapter: String?,
    val playing: Boolean = false,
    val ratePercent: Long = 100,
    val sliceIndex: Long = 0,
    val sliceCount: Long = 1,
)

/**
 * The platform player projects the current reader owner's state. Android
 * controller commands are routed back to that owner before state changes.
 */
@OptIn(UnstableApi::class)
class ReaderSpeechSessionService : MediaSessionService() {
    private lateinit var projection: SpeechSessionPlayer
    private lateinit var session: MediaSession
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        projection = SpeechSessionPlayer()
        session = MediaSession.Builder(this, projection).build()
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == LOCAL_BIND) binder else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        session.release()
        projection.release()
        super.onDestroy()
    }

    internal inner class LocalBinder : Binder() {
        fun apply(metadata: SpeechMetadata, handle: (SpeechAction) -> Unit) =
            projection.show(metadata, handle)

        fun clear(owner: UUID) = projection.clear(owner)
    }

    companion object {
        internal const val LOCAL_BIND = "io.reader.rebuild.speech.owner"
    }
}

@OptIn(UnstableApi::class)
private class SpeechSessionPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var metadata: SpeechMetadata? = null
    private var action: ((SpeechAction) -> Unit)? = null

    fun show(value: SpeechMetadata, handle: (SpeechAction) -> Unit) {
        metadata = value
        action = handle
        invalidateState()
    }

    fun clear(owner: UUID) {
        if (metadata?.owner != owner) return
        metadata = null
        action = null
        invalidateState()
    }

    override fun getState(): State {
        val current = metadata ?: return State.Builder().build()
        val previous = current.sliceIndex > 0
        val following = current.sliceIndex < current.sliceCount - 1
        val start = if (previous) current.sliceIndex - 1 else current.sliceIndex
        val end = if (following) current.sliceIndex + 1 else current.sliceIndex
        val description = MediaMetadata.Builder().setTitle(current.title).setSubtitle(current.chapter)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER).build()
        val adjacent = (start..end).map { index ->
            val identity = "${current.owner}:$index"
            val entry = MediaItem.Builder().setMediaId(identity).setMediaMetadata(description).build()
            MediaItemData.Builder(identity).setMediaItem(entry).build()
        }
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP, Player.COMMAND_SET_SPEED_AND_PITCH,
        ).addIf(Player.COMMAND_SEEK_TO_PREVIOUS, previous).addIf(Player.COMMAND_SEEK_TO_NEXT, following)
        return State.Builder()
            .setPlaylist(adjacent)
            .setCurrentMediaItemIndex((current.sliceIndex - start).toInt())
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(current.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackParameters(PlaybackParameters(current.ratePercent / 100f))
            .setAvailableCommands(commands.build())
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        dispatch(if (playWhenReady) SpeechAction.Play else SpeechAction.Pause)

    override fun handleStop(): ListenableFuture<*> = dispatch(SpeechAction.Stop)

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> =
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT -> dispatch(SpeechAction.Next)
            Player.COMMAND_SEEK_TO_PREVIOUS -> dispatch(SpeechAction.Previous)
            else -> Futures.immediateVoidFuture()
        }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> =
        dispatch(SpeechAction.Rate((playbackParameters.speed * 100).toLong()))

    private fun dispatch(command: SpeechAction): ListenableFuture<*> {
        action?.invoke(command)
        return Futures.immediateVoidFuture()
    }
}

internal class SpeechMediaSession(private val context: Context) {
    private lateinit var ready: CompletableDeferred<ReaderSpeechSessionService.LocalBinder>
    private var bound = false
    private var owner: UUID? = null
    private var binder: ReaderSpeechSessionService.LocalBinder? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service as? ReaderSpeechSessionService.LocalBinder
            val connected = binder
            if (connected == null) ready.completeExceptionally(IllegalStateException("Media session unavailable"))
            else ready.complete(connected)
        }

        override fun onServiceDisconnected(name: ComponentName) { binder = null }

        override fun onBindingDied(name: ComponentName) {
            ready.completeExceptionally(IllegalStateException("Media session disconnected"))
            owner?.let(::disconnect)
        }

        override fun onNullBinding(name: ComponentName) {
            ready.completeExceptionally(IllegalStateException("Media session unavailable"))
        }
    }

    suspend fun connect(metadata: SpeechMetadata, action: (SpeechAction) -> Unit) {
        check(owner == null)
        owner = metadata.owner
        ready = CompletableDeferred()
        val intent = Intent(context, ReaderSpeechSessionService::class.java)
            .setAction(ReaderSpeechSessionService.LOCAL_BIND)
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            check(bound) { "Media session unavailable" }
            withTimeout(5_000) { ready.await() }.apply(metadata, action)
        } catch (failure: Throwable) {
            disconnect(metadata.owner)
            throw failure
        }
    }

    fun update(metadata: SpeechMetadata, action: (SpeechAction) -> Unit) {
        if (owner == metadata.owner) binder?.apply(metadata, action)
    }

    fun disconnect(expected: UUID) {
        if (owner != expected) return
        binder?.clear(expected)
        binder = null
        owner = null
        ready.cancel()
        if (bound) {
            bound = false
            context.unbindService(connection)
        }
    }
}
