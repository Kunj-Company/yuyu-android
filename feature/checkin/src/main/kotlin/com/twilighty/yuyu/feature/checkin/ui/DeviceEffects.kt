package com.twilighty.yuyu.feature.checkin.ui

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.twilighty.yuyu.feature.checkin.camera.findActivity
import com.twilighty.yuyu.feature.checkin.domain.FeedbackKind
import java.io.Closeable
import kotlinx.coroutines.flow.Flow

/**
 * Distinct haptic + short tone per result, so volunteers know the outcome
 * without reading: one short buzz and an ack tone (valid), a double tap and a
 * double beep (already checked in), a long buzz and an error tone (invalid).
 */
internal class CheckInFeedbackPlayer(context: Context) : Closeable {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun play(kind: FeedbackKind) {
        val (toneType, durationMs, pattern) = when (kind) {
            FeedbackKind.Valid -> Triple(ToneGenerator.TONE_PROP_ACK, 150, longArrayOf(0, 45))
            FeedbackKind.Already -> Triple(ToneGenerator.TONE_PROP_BEEP2, 220, longArrayOf(0, 40, 90, 40))
            FeedbackKind.Invalid -> Triple(ToneGenerator.TONE_SUP_ERROR, 320, longArrayOf(0, 260))
        }
        runCatching { tone?.startTone(toneType, durationMs) }
        runCatching {
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    override fun close() {
        runCatching { tone?.release() }
    }
}

/** Plays every result from [feedback] for as long as the screen is shown. */
@Composable
internal fun ResultFeedback(feedback: Flow<FeedbackKind>) {
    val context = LocalContext.current
    val player = remember(context) { CheckInFeedbackPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.close() } }
    LaunchedEffect(feedback, player) { feedback.collect { player.play(it) } }
}

/** Keeps the display on while the scanner or station is open. */
@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Kiosk: hides the status and navigation bars (swipe from an edge shows them briefly). */
@Composable
internal fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Screen pinning ("Lock to this app") via the activity's lock task mode. */
internal object ScreenPinning {
    fun isPinned(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java)?.lockTaskModeState
            ?.let { it != ActivityManager.LOCK_TASK_MODE_NONE } == true

    fun pin(context: Context): Boolean =
        context.findActivity()?.let { runCatching { it.startLockTask() }.isSuccess } == true

    fun unpin(context: Context) {
        if (!isPinned(context)) return
        context.findActivity()?.let { runCatching { it.stopLockTask() } }
    }
}
