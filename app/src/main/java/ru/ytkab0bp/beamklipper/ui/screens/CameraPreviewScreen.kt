package ru.ytkab0bp.beamklipper.ui.screens

import android.Manifest
import ru.ytkab0bp.beamklipper.ui.theme.Line
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.service.CameraService
import ru.ytkab0bp.beamklipper.utils.CameraFocus
import ru.ytkab0bp.beamklipper.ui.components.KlipButton
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

private data class FocusTap(val point: Offset, val id: Int)

// Local MJPEG endpoint served by CameraService (same stream Fluidd/Mainsail use).
private const val STREAM_URL = "http://127.0.0.1:8889/"

@Composable
fun CameraPreviewScreen() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    val focusSupported = remember { CameraFocus.isSupportedForSelectedCamera(context) }
    // Where the last tap landed (in box pixels) + a counter so repeated taps
    // on the same spot still restart the animation.
    var focusTap by remember { mutableStateOf<FocusTap?>(null) }
    val focusAnim = remember { Animatable(0f) }
    LaunchedEffect(focusTap) {
        if (focusTap == null) return@LaunchedEffect
        focusAnim.snapTo(0f)
        focusAnim.animateTo(1f, tween(1400))
        focusTap = null
    }

    // Only connected while this tab is on screen — CameraService skips JPEG
    // encoding entirely when nobody is watching, so leaving the tab is free.
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        while (true) {
            try {
                streamFrames { frame = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                frame = null
            }
            delay(1000)
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Scrollable so the preview can never push past the screen in landscape.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.CameraPreview), style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(12.dp))
        if (!granted) {
            Text(stringResource(R.string.CameraPreviewPermission), color = Ink)
            Spacer(Modifier.height(12.dp))
            KlipButton(
                text = stringResource(R.string.CameraPreviewGrant),
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
            return@Column
        }
        Box(
            Modifier
                // Landscape: a fraction of the width keeps the 4:3 box short
                // enough for the tab bar and hint to stay visible.
                .fillMaxWidth(if (landscape) 0.4f else 1f)
                .align(Alignment.CenterHorizontally)
                .aspectRatio(4f / 3f)
                .background(Color.Black, RectangleShape)
                .border(2.dp, Line, RectangleShape)
                .pointerInput(focusSupported) {
                    if (!focusSupported) return@pointerInput
                    detectTapGestures { tap ->
                        val bmp = frame ?: return@detectTapGestures
                        // Image is drawn with ContentScale.Fit: map the tap onto
                        // the fitted frame, ignoring the letterbox bars.
                        val boxW = size.width.toFloat()
                        val boxH = size.height.toFloat()
                        val aspect = bmp.width.toFloat() / bmp.height
                        val w = if (boxW / boxH > aspect) boxH * aspect else boxW
                        val h = if (boxW / boxH > aspect) boxH else boxW / aspect
                        val left = (boxW - w) / 2f
                        val top = (boxH - h) / 2f
                        val nx = (tap.x - left) / w
                        val ny = (tap.y - top) / h
                        if (nx !in 0f..1f || ny !in 0f..1f) return@detectTapGestures
                        focusTap = FocusTap(tap, (focusTap?.id ?: 0) + 1)
                        KlipperApp.INSTANCE.sendBroadcast(
                            Intent(CameraService.ACTION_TAP_FOCUS)
                                .putExtra(CameraService.KEY_TAP_X, nx)
                                .putExtra(CameraService.KEY_TAP_Y, ny),
                            KlipperApp.PERMISSION
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = frame
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                focusTap?.let { tap ->
                    val t = focusAnim.value
                    // Ring closes in on the point, holds, then fades out.
                    Canvas(Modifier.fillMaxSize()) {
                        val side = 72.dp.toPx() * (1.5f - 0.5f * (t / 0.2f).coerceAtMost(1f))
                        val alpha = if (t < 0.7f) 1f else 1f - (t - 0.7f) / 0.3f
                        val topLeft = Offset(tap.point.x - side / 2, tap.point.y - side / 2)
                        drawRect(Color.Black.copy(alpha = alpha * 0.6f), topLeft, Size(side, side), style = Stroke(5.dp.toPx()))
                        drawRect(Color(0xFFFFD60A).copy(alpha = alpha), topLeft, Size(side, side), style = Stroke(2.5.dp.toPx()))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.CameraPreviewConnecting),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (focusSupported) R.string.CameraPreviewFocusHint else R.string.CameraPreviewFocusUnsupported),
            style = MaterialTheme.typography.bodySmall, color = Ink
        )
        if (focusSupported) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.CameraPreviewFocusSaved), style = MaterialTheme.typography.bodySmall, color = Ink)
            Spacer(Modifier.height(8.dp))
            KlipButton(
                text = stringResource(R.string.CameraPreviewFocusReset),
                onClick = {
                    KlipperApp.INSTANCE.sendBroadcast(
                        Intent(CameraService.ACTION_RESET_FOCUS),
                        KlipperApp.PERMISSION
                    )
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.CameraPreviewHint), style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

// Parses CameraService's multipart/x-mixed-replace stream: each part is
// "--camera-frame", headers incl. Content-Length, blank line, JPEG bytes.
private suspend fun streamFrames(onFrame: (Bitmap) -> Unit) = withContext(Dispatchers.IO) {
    val conn = (URL(STREAM_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 3000
        readTimeout = 5000
    }
    try {
        val input = DataInputStream(BufferedInputStream(conn.inputStream, 64 * 1024))
        while (true) {
            coroutineContext.ensureActive()
            var length = -1
            while (true) {
                val line = input.readHeaderLine() ?: return@withContext
                if (line.isEmpty()) {
                    if (length >= 0) break else continue
                }
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    length = line.substringAfter(':').trim().toIntOrNull() ?: -1
                }
            }
            val data = ByteArray(length)
            input.readFully(data)
            BitmapFactory.decodeByteArray(data, 0, data.size)?.let { bmp ->
                withContext(Dispatchers.Main) { onFrame(bmp) }
            }
        }
    } finally {
        conn.disconnect()
    }
}

private fun InputStream.readHeaderLine(): String? {
    val sb = StringBuilder()
    while (true) {
        val c = read()
        if (c == -1) return if (sb.isEmpty()) null else sb.toString()
        if (c == '\n'.code) return sb.toString().trimEnd('\r')
        sb.append(c.toChar())
    }
}
