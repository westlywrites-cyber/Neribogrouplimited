package com.westly.neribogroup.feature.storageroom

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.westly.neribogroup.R

/** The volume of the server hum: steady and soft, so it sits in the background. */
private const val HUM_VOLUME = 0.5f

/**
 * The steady hum of the two servers. It starts when this is first shown (the room opens), loops without a gap, and
 * stops when the room is left or the app goes to the background. It starts again when the app comes back.
 * If the sound cannot be played for any reason, the room simply stays quiet and nothing else is affected.
 */
@Composable
internal fun RoomHum() {
    val context = LocalContext.current
    DisposableEffect(context) {
        var player: MediaPlayer? = null

        fun start() {
            if (player != null) return
            try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val made = MediaPlayer.create(context, R.raw.server_room_hum, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
                if (made != null) {
                    made.isLooping = true
                    made.setVolume(HUM_VOLUME, HUM_VOLUME)
                    made.start()
                    player = made
                }
            } catch (e: Exception) {
                player = null
            }
        }

        fun stop() {
            val current = player ?: return
            player = null
            try {
                current.stop()
            } catch (e: Exception) {
                // Already stopped.
            }
            try {
                current.release()
            } catch (e: Exception) {
                // Nothing more to free.
            }
        }

        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) start()
            if (event == Lifecycle.Event.ON_PAUSE) stop()
        }
        if (owner != null) {
            // Adding the observer plays the lifecycle up to now, so a screen opened while the app is showing starts at once.
            owner.lifecycle.addObserver(observer)
        } else {
            start()
        }
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            stop()
        }
    }
}

/** The activity that hosts this screen, found by looking through the wrappers around the context. */
private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
