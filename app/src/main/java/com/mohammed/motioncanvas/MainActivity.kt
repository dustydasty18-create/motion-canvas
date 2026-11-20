package com.mohammed.motioncanvas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mohammed.motioncanvas.engine.FrameRenderer
import com.mohammed.motioncanvas.model.Project

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MotionPreviewScreen()
                }
            }
        }
    }
}

@Composable
fun MotionPreviewScreen() {
    val context = LocalContext.current
    val project = remember { Project.sampleDemo() }
    val renderer = remember(context) { FrameRenderer(context) }

    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var showJson by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (isPlaying) {
            val nowNanos = withFrameNanos { it }
            val deltaMs = (nowNanos - lastNanos) / 1_000_000L
            lastNanos = nowNanos
            currentTimeMs = (currentTimeMs + deltaMs) % project.durationMs
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(project.title, style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { showJson = !showJson }) {
                Text(if (showJson) "Show Preview" else "View JSON")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (showJson) {
            Text(
                text = remember(project) { project.toJson() },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            )
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(project.widthPx.toFloat() / project.heightPx.toFloat())
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    val scaleX = size.width / project.widthPx.toFloat()
                    val scaleY = size.height / project.heightPx.toFloat()
                    drawIntoCanvas { composeCanvas ->
                        val native = composeCanvas.nativeCanvas
                        native.save()
                        native.scale(scaleX, scaleY)
                        renderer.renderFrame(native, project, currentTimeMs)
                        native.restore()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { isPlaying = !isPlaying }) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = String.format("%.2fs / %.2fs", currentTimeMs / 1000f, project.durationMs / 1000f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Slider(
                value = currentTimeMs.toFloat(),
                onValueChange = {
                    isPlaying = false
                    currentTimeMs = it.toLong()
                },
                valueRange = 0f..project.durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
