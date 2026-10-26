package com.twilighty.yuyu.feature.checkin.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A QR scanner with the camera-permission flow: asks with a rationale, and
 * after a permanent denial offers the app's system settings. Re-checks the
 * permission whenever the screen resumes (e.g. back from settings).
 */
@Composable
internal fun QrScanner(
    paused: Boolean,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Point the camera at the ticket QR code",
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
        showRationale = context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }
    LifecycleResumeEffect(Unit) {
        granted = context.hasCameraPermission()
        onPauseOrDispose { }
    }

    // Set when the camera or the QR reader can't start on this phone: offer typing or search instead of crashing.
    var unavailable by rememberSaveable { mutableStateOf(false) }

    if (granted && unavailable) {
        CameraUnavailableCard(modifier)
    } else if (granted) {
        CameraPreview(paused = paused, onCode = onCode, hint = hint, onUnavailable = { unavailable = true }, modifier = modifier)
    } else {
        // Asked before and the system will no longer show the dialog: only settings can help.
        val permanentlyDenied = asked && !showRationale
        CameraPermissionCard(
            permanentlyDenied = permanentlyDenied,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
            modifier = modifier,
        )
    }
}

@Composable
private fun CameraPermissionCard(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val c = YuyuTheme.colors
    YuyuCard(modifier = modifier.fillMaxWidth(), tone = YuyuCardTone.Subtle) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.NoPhotography, contentDescription = null, tint = c.textSecondary)
            Text("Camera access needed", style = YuyuTheme.typography.h4, color = c.text)
            Text(
                if (permanentlyDenied) {
                    "Camera access is turned off for Yuyu. Turn it on in Settings to scan tickets, or type the code or search by name instead."
                } else {
                    "Yuyu uses the camera only to read ticket QR codes at the door. Nothing is recorded or uploaded."
                },
                style = YuyuTheme.typography.bodyUi,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (permanentlyDenied) {
                YuyuButton("Open settings", onOpenSettings, fullWidth = true)
            } else {
                YuyuButton("Allow camera", onRequest, fullWidth = true)
            }
        }
    }
}

@Composable
private fun CameraUnavailableCard(modifier: Modifier) {
    val c = YuyuTheme.colors
    YuyuCard(modifier = modifier.fillMaxWidth(), tone = YuyuCardTone.Subtle) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.NoPhotography, contentDescription = null, tint = c.textSecondary)
            Text("Camera scanning isn't available", style = YuyuTheme.typography.h4, color = c.text)
            Text(
                "The camera or the QR reader couldn't start on this phone. Type the ticket code or search by name instead.",
                style = YuyuTheme.typography.bodyUi,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CameraPreview(
    paused: Boolean,
    onCode: (String) -> Unit,
    hint: String,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentPaused by rememberUpdatedState(paused)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val analysisExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "yuyu-qr-analyzer") }
        // Late frames and ML Kit callbacks after dispose are dropped instead of throwing.
        val safeExecutor = Executor { task -> runCatching { analysisExecutor.execute(task) } }
        val main = ContextCompat.getMainExecutor(context)
        var disposed = false
        // Creating the ML Kit client can throw on a broken install; it must never take the app down.
        val analyzer = runCatching {
            QrCodeAnalyzer(safeExecutor, isPaused = { currentPaused }) { code ->
                main.execute { if (!currentPaused) currentOnCode(code) }
            }
        }.getOrNull()
        if (analyzer == null) {
            currentOnUnavailable()
        } else {
            controller.setImageAnalysisAnalyzer(safeExecutor, analyzer)
            // Bind only once CameraX is ready and a camera exists: binding without one throws on the main thread.
            controller.initializationFuture.addListener({
                if (disposed) return@addListener
                val bound = runCatching {
                    controller.initializationFuture.get()
                    controller.cameraSelector = when {
                        controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> error("No camera")
                    }
                    controller.bindToLifecycle(lifecycleOwner)
                    hasTorch = controller.cameraInfo?.hasFlashUnit() == true
                }
                if (bound.isFailure) currentOnUnavailable()
            }, main)
        }
        onDispose {
            disposed = true
            runCatching {
                controller.clearImageAnalysisAnalyzer()
                controller.unbind()
            }
            analyzer?.close()
            analysisExecutor.shutdown()
        }
    }
    LaunchedEffect(torchOn, hasTorch) {
        if (hasTorch) runCatching { controller.enableTorch(torchOn) }
    }

    Box(
        modifier
            .clip(YuyuRadius.extraLarge)
            .background(Color.Black)
            .semantics { contentDescription = "Camera viewfinder. $hint" },
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        ScanFrameOverlay(dimmed = paused, modifier = Modifier.fillMaxSize())
        Crossfade(
            targetState = if (paused) "Paused while a result is showing" else hint,
            animationSpec = YuyuSpecs.quick(),
            label = "scanHint",
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
        ) { text ->
            Text(text, style = YuyuTheme.typography.caption, color = Color.White, textAlign = TextAlign.Center)
        }
        if (hasTorch) {
            YuyuIconButton(
                icon = if (torchOn) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                onClick = { torchOn = !torchOn },
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

/** Dark surround with a clear square and corner brackets (DESIGN §4.12 viewfinder). */
@Composable
private fun ScanFrameOverlay(dimmed: Boolean, modifier: Modifier) {
    val bracket = YuyuTheme.colors.brandMarigold
    val reduced = LocalReducedMotion.current
    val surround by animateFloatAsState(if (dimmed) 0.7f else 0.45f, YuyuSpecs.standard(), label = "scanDim")
    // Brackets draw in a touch when a result pauses the camera, and back out when it's live again.
    val inset by animateFloatAsState(if (dimmed && !reduced) 0.04f else 0f, YuyuSpecs.settle(), label = "scanInset")
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val frame = scanFrame()
            drawRect(Color.Black.copy(alpha = surround))
            drawRoundRect(Color.Transparent, frame.topLeft, frame.size, CornerRadius(20.dp.toPx()), blendMode = BlendMode.Clear)
            val side = frame.size.width
            val shift = side * inset
            val len = side * 0.18f
            val width = 4.dp.toPx()
            val l = frame.left + shift
            val t = frame.top + shift
            val r = frame.right - shift
            val b = frame.bottom - shift
            listOf(
                Offset(l, t + len) to Offset(l, t), Offset(l, t) to Offset(l + len, t),
                Offset(r - len, t) to Offset(r, t), Offset(r, t) to Offset(r, t + len),
                Offset(l, b - len) to Offset(l, b), Offset(l, b) to Offset(l + len, b),
                Offset(r - len, b) to Offset(r, b), Offset(r, b) to Offset(r, b - len),
            ).forEach { (from, to) -> drawLine(bracket, from, to, strokeWidth = width, cap = StrokeCap.Round) }
        }
        // The one live indicator: a faint line sweeping the frame while the camera is reading.
        if (!dimmed && !reduced) ScanSweep(bracket, Modifier.fillMaxSize())
    }
}

@Composable
private fun ScanSweep(color: Color, modifier: Modifier) {
    val sweep = rememberInfiniteTransition(label = "scanSweep")
    val progress by sweep.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanSweepProgress",
    )
    Canvas(modifier) {
        val frame = scanFrame()
        val y = frame.top + frame.size.height * progress
        val inset = frame.size.width * 0.06f
        drawLine(
            Brush.horizontalGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.75f), Color.Transparent),
                startX = frame.left + inset,
                endX = frame.right - inset,
            ),
            Offset(frame.left + inset, y),
            Offset(frame.right - inset, y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.scanFrame(): Rect {
    val side = size.minDimension * 0.66f
    return Rect(Offset((size.width - side) / 2f, (size.height - side) / 2f), Size(side, side))
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
