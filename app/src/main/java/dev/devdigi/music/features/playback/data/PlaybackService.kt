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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private lateinit var player:
        ExoPlayer

    private lateinit var mediaLibrarySession:
        MediaLibrarySession

    private lateinit var sourceResolver:
        PlaybackSourceResolver

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate,
        )

    private var activeAccount:
        ServerAccountIdentity? = null

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
                        invalidatePlayback()
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
                            .ACTION_RECONCILE_ACCOUNT,
                        -> {
                            handleReconcile(args)
                        }

                        PlaybackSessionProtocol
                            .ACTION_STOP,
                        -> {
                            invalidatePlayback()
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

        mediaLibrarySession =
            MediaLibrarySession
                .Builder(
                    this,
                    player,
                    sessionCallback,
                ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaLibrarySession

    override fun onDestroy() {
        requestGeneration += 1
        activeAccount = null

        serviceScope.cancel()

        if (::player.isInitialized) {
            internalClear = true
            player.removeListener(playerListener)
            player.stop()
            player.clearMediaItems()
            player.release()
            internalClear = false
        }

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        super.onDestroy()
    }

    private fun handlePlay(args: Bundle): Int {
        val request =
            PlaybackSessionProtocol
                .parsePlayRequest(args)
                ?: return SessionResult
                    .RESULT_ERROR_BAD_VALUE

        requestGeneration += 1

        val generation =
            requestGeneration

        clearPlayerItem()

        activeAccount =
            request.account

        serviceScope.launch {
            when (
                val resolution =
                    sourceResolver.resolve(
                        account =
                            request.account,
                        trackId =
                            request.track.id,
                    )
            ) {
                is StreamResolutionResult.Success -> {
                    if (
                        generation !=
                        requestGeneration ||
                        activeAccount !=
                        request.account
                    ) {
                        return@launch
                    }

                    player.setMediaItem(
                        mediaItem(
                            request =
                            request,
                            source =
                                resolution.source,
                        ),
                    )

                    player.prepare()
                    player.play()
                }

                StreamResolutionResult
                    .AuthenticationRequired,
                -> {
                    if (
                        generation ==
                        requestGeneration
                    ) {
                        activeAccount = null

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
                        activeAccount = null

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

        return SessionResult.RESULT_SUCCESS
    }

    private fun handleReconcile(args: Bundle): Int {
        val request =
            PlaybackSessionProtocol
                .parseReconcileRequest(args)
                ?: return SessionResult
                    .RESULT_ERROR_BAD_VALUE

        val currentAccount =
            when (request) {
                is PlaybackSessionReconcileRequest
                    .Account,
                -> request.account

                PlaybackSessionReconcileRequest
                    .SignedOut,
                -> null
            }

        if (
            shouldClearServicePlayback(
                activeAccount =
                activeAccount,
                currentAccount =
                currentAccount,
            )
        ) {
            invalidatePlayback()
        }

        return SessionResult.RESULT_SUCCESS
    }

    private fun invalidatePlayback() {
        requestGeneration += 1
        activeAccount = null
        clearPlayerItem()
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

    private fun mediaItem(
        request: PlaybackSessionPlayRequest,
        source: ResolvedStreamSource,
    ): MediaItem {
        val metadataBuilder =
            MediaMetadata
                .Builder()
                .setTitle(
                    request.track.title,
                )

        request.track.artist?.let {
            metadataBuilder.setArtist(it)
        }

        return MediaItem
            .Builder()
            .setMediaId(
                request.track.id,
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
                        .reconcileAccountCommand,
                ).add(
                    PlaybackSessionProtocol
                        .stopCommand,
                ).build()

    private val safePlayerCommands
        get() =
            MediaSession
                .ConnectionResult
                .DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .remove(
                    Player.COMMAND_SET_MEDIA_ITEM,
                ).remove(
                    Player.COMMAND_CHANGE_MEDIA_ITEMS,
                ).remove(
                    Player.COMMAND_SEEK_TO_NEXT,
                ).remove(
                    Player
                        .COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                ).remove(
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                ).remove(
                    Player
                        .COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                ).build()
}
