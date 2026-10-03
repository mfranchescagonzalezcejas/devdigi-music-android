package dev.devdigi.music.features.playback.data

import android.os.Bundle
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.devdigi.music.connection.AesGcmSecretCipher
import dev.devdigi.music.connection.AndroidKeystoreAuthKeyProvider
import dev.devdigi.music.connection.AuthSecretDataStoreFactory
import dev.devdigi.music.connection.DataStoreAuthSecretStore
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import dev.devdigi.music.features.playback.persistence.PlaybackQueueStore
import dev.devdigi.music.features.playback.persistence.playbackQueueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private lateinit var player:
        ExoPlayer

    private lateinit var mediaLibrarySession:
        MediaLibrarySession

    private lateinit var sessionPlayer:
        PlaybackQueueNavigationPlayer

    private lateinit var sourceResolver:
        PlaybackSourceResolver

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate,
        )

    private val queueRuntime =
        PlaybackQueueRuntime()

    private lateinit var queueStore:
        PlaybackQueueStore

    private val queuePersistenceMutex =
        Mutex()

    private var queuePersistenceGeneration =
        0L

    private var queueRestoreGeneration =
        0L

    private val activeAccount:
        ServerAccountIdentity?
        get() = queueRuntime.activeAccount

    private var requestGeneration = 0L

    private var internalClear = false

    private val playerListener =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                invalidatePlayback()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (internalClear) {
                    return
                }

                when (playbackState) {
                    Player.STATE_ENDED -> {
                        advanceQueueFromPlaybackEnd()
                    }

                    Player.STATE_IDLE -> {
                        if (
                            player.currentMediaItem !=
                            null
                        ) {
                            invalidatePlayback()
                        }
                    }
                }
            }
        }

    private val sessionCallback =
        object :
            MediaLibrarySession.Callback {
            override fun onConnectAsync(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): ListenableFuture<
                MediaSession.ConnectionResult,
            > {
                val ownApplication =
                    isOwnApplicationController(
                        controllerPackageName =
                            controller.packageName,
                        controllerUid =
                            controller.uid,
                        applicationPackageName =
                        packageName,
                        applicationUid =
                            Process.myUid(),
                    )

                val systemController =
                    session
                        .isMediaNotificationController(
                            controller,
                        ) ||
                        controller.isTrusted

                val playerCommands =
                    if (
                        ownApplication ||
                        systemController
                    ) {
                        safePlayerCommands
                    } else {
                        Player.Commands.EMPTY
                    }

                val sessionCommands =
                    if (ownApplication) {
                        ownApplicationSessionCommands
                    } else {
                        MediaSession
                            .ConnectionResult
                            .DEFAULT_SESSION_COMMANDS
                    }

                return Futures.immediateFuture(
                    MediaSession.ConnectionResult
                        .AcceptedResultBuilder(
                            session,
                            controller,
                        ).setAvailableSessionCommands(
                            sessionCommands,
                        ).setAvailablePlayerCommands(
                            playerCommands,
                        ).build(),
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: androidx.media3.session
                    .SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                if (
                    !isOwnApplicationController(
                        controllerPackageName =
                            controller.packageName,
                        controllerUid =
                            controller.uid,
                        applicationPackageName =
                        packageName,
                        applicationUid =
                            Process.myUid(),
                    )
                ) {
                    return result(
                        SessionResult
                            .RESULT_ERROR_PERMISSION_DENIED,
                    )
                }

                val resultCode =
                    when (
                        customCommand.customAction
                    ) {
                        PlaybackSessionProtocol
                            .ACTION_PLAY_TRACK,
                        -> {
                            handlePlay(args)
                        }

                        PlaybackSessionProtocol
                            .ACTION_REPLACE_QUEUE,
                        -> {
                            handleReplaceQueue(args)
                        }

                        PlaybackSessionProtocol
                            .ACTION_APPEND_QUEUE,
                        -> {
                            handleAppendQueue(args)
                        }

                        PlaybackSessionProtocol
                            .ACTION_REMOVE_QUEUE_ENTRY,
                        -> {
                            handleRemoveQueueEntry(args)
                        }

                        PlaybackSessionProtocol
                            .ACTION_CLEAR_QUEUE,
                        -> {
                            handleClearQueue(args)
                        }

                        PlaybackSessionProtocol
                            .ACTION_RECONCILE_ACCOUNT,
                        -> {
                            handleReconcile(args)
                        }

                        PlaybackSessionProtocol
                            .ACTION_STOP,
                        -> {
                            cancelPendingQueueRestore()
                            stopPlaybackPreservingQueue()
                            SessionResult.RESULT_SUCCESS
                        }

                        else -> {
                            SessionResult
                                .RESULT_ERROR_NOT_SUPPORTED
                        }
                    }

                return result(resultCode)
            }
        }

    override fun onCreate() {
        super.onCreate()

        val secretStore =
            DataStoreAuthSecretStore(
                dataStore =
                    AuthSecretDataStoreFactory
                        .create(this),
                cipher =
                    AesGcmSecretCipher(
                        AndroidKeystoreAuthKeyProvider(),
                    ),
            )

        sourceResolver =
            SecureStreamResolver(
                secretStore = secretStore,
                signer =
                    DefaultSubsonicAuthSigner(),
            )

        queueStore =
            playbackQueueStore(this)

        player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        OkHttpDataSource.Factory(
                            playbackHttpClient(),
                        ),
                    ),
                ).build()

        player.addListener(playerListener)

        sessionPlayer =
            PlaybackQueueNavigationPlayer(
                player = player,
                canNavigate = { direction ->
                    when (direction) {
                        QueueNavigationDirection
                            .PREVIOUS,
                        -> queueRuntime.hasPrevious()

                        QueueNavigationDirection
                            .NEXT,
                        -> queueRuntime.hasNext()
                    }
                },
                navigate = { direction ->
                    navigateQueue(
                        forward =
                            direction ==
                                QueueNavigationDirection
                                    .NEXT,
                        autoplay =
                            player.playWhenReady,
                    )
                },
            )

        mediaLibrarySession =
            MediaLibrarySession
                .Builder(
                    this,
                    sessionPlayer,
                    sessionCallback,
                ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaLibrarySession

    override fun onDestroy() {
        requestGeneration += 1
        queueRuntime.reset()

        serviceScope.cancel()

        if (::player.isInitialized) {
            internalClear = true
            player.removeListener(playerListener)
            player.stop()
            player.clearMediaItems()
            sessionPlayer.release()
            internalClear = false
        }

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        super.onDestroy()
    }

    private fun handlePlay(args: Bundle): Int {
        cancelPendingQueueRestore()
        val request =
            PlaybackSessionProtocol
                .parsePlayRequest(args)
                ?: return SessionResult
                    .RESULT_ERROR_BAD_VALUE

        return when (
            queueRuntime.play(
                request,
            )
        ) {
            QueueRuntimeMutationResult.ACCEPTED -> {
                refreshSessionQueueCommands()

                persistQueueState(
                    request.account,
                )
                loadCurrentQueueItem(
                    autoplay = true,
                )
                SessionResult.RESULT_SUCCESS
            }

            QueueRuntimeMutationResult.ACCOUNT_MISMATCH -> {
                SessionResult
                    .RESULT_ERROR_PERMISSION_DENIED
            }

            QueueRuntimeMutationResult.INVALID_REQUEST -> {
                SessionResult
                    .RESULT_ERROR_BAD_VALUE
            }
        }
    }

    private fun handleReplaceQueue(args: Bundle): Int {
        cancelPendingQueueRestore()
        val request =
            parseReplaceQueueRequest(args)
                ?: return SessionResult
                    .RESULT_ERROR_BAD_VALUE

        return when (
            queueRuntime.replace(
                request,
            )
        ) {
            QueueRuntimeMutationResult.ACCEPTED -> {
                refreshSessionQueueCommands()

                persistQueueState(
                    request.account,
                )

                // Queue replacement represents an explicit track
                // selection. Resolve only the selected entry and
                // start it immediately; queued successors remain
                // unsigned until their transition.
                loadCurrentQueueItem(
                    autoplay = true,
                )

                SessionResult.RESULT_SUCCESS
            }

            QueueRuntimeMutationResult.ACCOUNT_MISMATCH -> {
                SessionResult
                    .RESULT_ERROR_PERMISSION_DENIED
            }

            QueueRuntimeMutationResult.INVALID_REQUEST -> {
                SessionResult
                    .RESULT_ERROR_BAD_VALUE
            }
        }
    }

    private fun handleAppendQueue(args: Bundle): Int {
        cancelPendingQueueRestore()
        val request =
            parseAppendQueueRequest(args)
                ?: return SessionResult
                    .RESULT_ERROR_BAD_VALUE

        return when (
            queueRuntime.append(
                request,
            )
        ) {
            QueueRuntimeMutationResult.ACCEPTED -> {
                refreshSessionQueueCommands()

                persistQueueState(
                    request.account,
                )
                SessionResult.RESULT_SUCCESS
            }

            QueueRuntimeMutationResult.ACCOUNT_MISMATCH -> {
                SessionResult
                    .RESULT_ERROR_PERMISSION_DENIED
            }

            QueueRuntimeMutationResult.INVALID_REQUEST -> {
                SessionResult
                    .RESULT_ERROR_BAD_VALUE
            }
        }
    }

    private fun handleRemoveQueueEntry(args: Bundle): Int {
        cancelPendingQueueRestore()
        val request =
            parseRemoveQueueEntryRequest(args)
                ?: return SessionResult
                    .RESULT_ERROR_BAD_VALUE

        val previousTrack =
            queueRuntime.queue.current
        val continuePlaying =
            player.playWhenReady

        return when (
            queueRuntime.remove(
                request,
            )
        ) {
            QueueRuntimeMutationResult.ACCEPTED -> {
                refreshSessionQueueCommands()

                persistQueueState(
                    request.account,
                )

                val currentTrack =
                    queueRuntime.queue.current

                when {
                    currentTrack == null -> {
                        requestGeneration += 1
                        clearPlayerItem()
                    }

                    currentTrack !=
                        previousTrack -> {
                        loadCurrentQueueItem(
                            autoplay =
                            continuePlaying,
                        )
                    }
                }

                SessionResult.RESULT_SUCCESS
            }

            QueueRuntimeMutationResult.ACCOUNT_MISMATCH -> {
                SessionResult
                    .RESULT_ERROR_PERMISSION_DENIED
            }

            QueueRuntimeMutationResult.INVALID_REQUEST -> {
                SessionResult
                    .RESULT_ERROR_BAD_VALUE
            }
        }
    }

    private fun handleClearQueue(args: Bundle): Int {
        cancelPendingQueueRestore()
        val request =
            parseClearQueueRequest(args)
                ?: return SessionResult
                    .RESULT_ERROR_BAD_VALUE

        return when (
            queueRuntime.clear(
                request,
            )
        ) {
            QueueRuntimeMutationResult.ACCEPTED -> {
                requestGeneration += 1

                refreshSessionQueueCommands()
                clearPlayerItem()
                persistQueueState(
                    request.account,
                )
                SessionResult.RESULT_SUCCESS
            }

            QueueRuntimeMutationResult.ACCOUNT_MISMATCH -> {
                SessionResult
                    .RESULT_ERROR_PERMISSION_DENIED
            }

            QueueRuntimeMutationResult.INVALID_REQUEST -> {
                SessionResult
                    .RESULT_ERROR_BAD_VALUE
            }
        }
    }

    private fun handleReconcile(args: Bundle): Int {
        val request =
            PlaybackSessionProtocol
                .parseReconcileRequest(args)
                ?: return SessionResult
                    .RESULT_ERROR_BAD_VALUE

        cancelPendingQueueRestore()

        when (request) {
            PlaybackSessionReconcileRequest
                .SignedOut,
            -> {
                if (
                    queueReconcileAction(
                        activeAccount =
                        activeAccount,
                        currentAccount = null,
                    ) ==
                    QueueReconcileAction.CLEAR
                ) {
                    invalidatePlayback()
                }
            }

            is PlaybackSessionReconcileRequest
                .Account,
            -> {
                reconcileAccount(
                    request.account,
                )
            }
        }

        return SessionResult.RESULT_SUCCESS
    }

    private fun reconcileAccount(account: ServerAccountIdentity) {
        when (
            queueReconcileAction(
                activeAccount =
                activeAccount,
                currentAccount =
                account,
            )
        ) {
            QueueReconcileAction.NONE,
            QueueReconcileAction.CLEAR,
            -> {
                Unit
            }

            QueueReconcileAction.PRESERVE -> {
                // A previous non-fatal stream resolution may have
                // left the safe queue intact without a MediaItem.
                if (
                    player.currentMediaItem ==
                    null &&
                    queueRuntime.queue.current !=
                    null
                ) {
                    loadCurrentQueueItem(
                        autoplay = false,
                    )
                }
            }

            QueueReconcileAction.RESTORE -> {
                restorePersistedQueue(
                    account,
                )
            }

            QueueReconcileAction
                .CLEAR_AND_RESTORE,
            -> {
                invalidatePlayback()

                restorePersistedQueue(
                    account,
                )
            }
        }
    }

    private fun restorePersistedQueue(account: ServerAccountIdentity) {
        queueRestoreGeneration += 1

        val generation =
            queueRestoreGeneration

        serviceScope.launch {
            val readResult =
                queuePersistenceMutex
                    .withLock {
                        readQueueForRestore(
                            store = queueStore,
                            account = account,
                        )
                    }

            if (
                !canApplyQueueRestore(
                    expectedGeneration =
                    generation,
                    currentGeneration =
                    queueRestoreGeneration,
                    activeAccount =
                    activeAccount,
                )
            ) {
                return@launch
            }

            when (readResult) {
                QueueRestoreReadResult.Missing -> {
                    Unit
                }

                QueueRestoreReadResult.Failed -> {
                    mediaLibrarySession.sendError(
                        SessionError(
                            SessionError.ERROR_UNKNOWN,
                            "Queue restoration failed.",
                        ),
                    )
                }

                is QueueRestoreReadResult.Restored -> {
                    when (
                        queueRuntime.restore(
                            account = account,
                            restoredQueue =
                                readResult.queue,
                        )
                    ) {
                        QueueRuntimeMutationResult.ACCEPTED -> {
                            refreshSessionQueueCommands()

                            // Rehydrate safe metadata/current index and
                            // prepare the selected item, but never autoplay.
                            loadCurrentQueueItem(
                                autoplay = false,
                            )
                        }

                        QueueRuntimeMutationResult.ACCOUNT_MISMATCH,
                        QueueRuntimeMutationResult.INVALID_REQUEST,
                        -> {
                            mediaLibrarySession.sendError(
                                SessionError(
                                    SessionError.ERROR_UNKNOWN,
                                    "Queue restoration failed.",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopPlaybackPreservingQueue() {
        requestGeneration += 1
        internalClear = true

        try {
            player.stop()
        } finally {
            internalClear = false
        }
    }

    private fun invalidatePlayback() {
        cancelPendingQueueRestore()

        requestGeneration += 1
        queueRuntime.reset()

        refreshSessionQueueCommands()
        clearPlayerItem()
    }

    private fun cancelPendingQueueRestore() {
        queueRestoreGeneration += 1
    }

    private fun refreshSessionQueueCommands() {
        if (::sessionPlayer.isInitialized) {
            sessionPlayer.refreshQueueCommands()
        }
    }

    private fun clearPlayerItem() {
        internalClear = true

        try {
            player.stop()
            player.clearMediaItems()
        } finally {
            internalClear = false
        }
    }

    private fun loadCurrentQueueItem(autoplay: Boolean) {
        val account =
            activeAccount
                ?: return
        val track =
            queueRuntime.queue.current
                ?: return

        requestGeneration += 1

        val generation =
            requestGeneration

        clearPlayerItem()

        serviceScope.launch {
            when (
                val resolution =
                    sourceResolver.resolve(
                        account = account,
                        trackId = track.id,
                    )
            ) {
                is StreamResolutionResult.Success -> {
                    if (
                        generation !=
                        requestGeneration ||
                        activeAccount !=
                        account ||
                        queueRuntime.queue
                            .current !=
                        track
                    ) {
                        return@launch
                    }

                    player.setMediaItem(
                        mediaItem(
                            track = track,
                            source =
                                resolution.source,
                        ),
                    )

                    player.prepare()

                    if (autoplay) {
                        player.play()
                    }
                }

                StreamResolutionResult
                    .AuthenticationRequired,
                -> {
                    if (
                        generation ==
                        requestGeneration
                    ) {
                        mediaLibrarySession.sendError(
                            SessionError(
                                SessionError
                                    .ERROR_SESSION_AUTHENTICATION_EXPIRED,
                                "Authentication required.",
                            ),
                        )
                    }
                }

                StreamResolutionResult
                    .InvalidRequest,
                -> {
                    if (
                        generation ==
                        requestGeneration
                    ) {
                        mediaLibrarySession.sendError(
                            SessionError(
                                SessionError
                                    .ERROR_UNKNOWN,
                                "Playback request failed.",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun advanceQueueFromPlaybackEnd() {
        navigateQueue(
            forward = true,
            autoplay = true,
        )
    }

    private fun navigateQueue(
        forward: Boolean,
        autoplay: Boolean,
    ): Boolean {
        val account =
            activeAccount
                ?: return false

        val changed =
            if (forward) {
                queueRuntime.next()
            } else {
                queueRuntime.previous()
            }

        if (!changed) {
            return false
        }

        refreshSessionQueueCommands()

        persistQueueState(
            account,
        )

        loadCurrentQueueItem(
            autoplay = autoplay,
        )

        return true
    }

    private fun persistQueueState(account: ServerAccountIdentity) {
        queuePersistenceGeneration += 1

        val generation =
            queuePersistenceGeneration
        val snapshot =
            queueRuntime.queue

        serviceScope.launch {
            queuePersistenceMutex.withLock {
                if (
                    generation !=
                    queuePersistenceGeneration
                ) {
                    return@withLock
                }

                try {
                    if (snapshot.entries.isEmpty()) {
                        queueStore.clear()
                    } else {
                        queueStore.save(
                            account = account,
                            queue = snapshot,
                        )
                    }
                } catch (
                    error: CancellationException,
                ) {
                    throw error
                } catch (_: Exception) {
                    mediaLibrarySession.sendError(
                        SessionError(
                            SessionError.ERROR_UNKNOWN,
                            "Queue persistence failed.",
                        ),
                    )
                }
            }
        }
    }

    private fun mediaItem(
        track: PlaybackTrack,
        source: ResolvedStreamSource,
    ): MediaItem {
        val metadataBuilder =
            MediaMetadata
                .Builder()
                .setTitle(
                    track.title,
                )

        track.artist?.let {
            metadataBuilder.setArtist(it)
        }

        return MediaItem
            .Builder()
            .setMediaId(
                track.id,
            ).setUri(
                source.url.toString(),
            ).setMediaMetadata(
                metadataBuilder.build(),
            ).build()
    }

    private fun result(resultCode: Int): ListenableFuture<SessionResult> =
        Futures.immediateFuture(
            SessionResult(resultCode),
        )

    private val ownApplicationSessionCommands
        get() =
            MediaSession
                .ConnectionResult
                .DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(
                    PlaybackSessionProtocol
                        .playTrackCommand,
                ).add(
                    PlaybackSessionProtocol
                        .replaceQueueCommand,
                ).add(
                    PlaybackSessionProtocol
                        .appendQueueCommand,
                ).add(
                    PlaybackSessionProtocol
                        .removeQueueEntryCommand,
                ).add(
                    PlaybackSessionProtocol
                        .clearQueueCommand,
                ).add(
                    PlaybackSessionProtocol
                        .reconcileAccountCommand,
                ).add(
                    PlaybackSessionProtocol
                        .stopCommand,
                ).build()

    private val safePlayerCommands
        get() =
            queueSafePlayerCommands(
                MediaSession
                    .ConnectionResult
                    .DEFAULT_PLAYER_COMMANDS,
            )
}
