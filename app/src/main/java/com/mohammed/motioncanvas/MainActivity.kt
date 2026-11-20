package com.mohammed.motioncanvas

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammed.motioncanvas.data.*
import com.mohammed.motioncanvas.engine.FrameRenderer
import com.mohammed.motioncanvas.engine.KeyframeInterpolator
import com.mohammed.motioncanvas.export.VideoExporter
import com.mohammed.motioncanvas.model.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D0F14)) {
                    MotionEditorScreen()
                }
            }
        }
    }
}

private enum class InspectorTab(val title: String) {
    STYLE("Style"), MOTION("Motion"), EFFECTS("Effects & Layer")
}

@Composable
fun ColorSwatchRow(label: String, selectedHex: Long, onSelect: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.width(46.dp))
        SWATCH_COLORS.forEach { hex ->
            val isSelected = hex == selectedHex
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(Color(hex.toInt()))
                    .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
fun KeyframeTrackBar(totalDurationMs: Long, keyTimesMs: List<Long>, activeStartMs: Long, activeEndMs: Long) {
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 12.dp)) {
        val dur = totalDurationMs.coerceAtLeast(1L).toFloat()
        val startX = ((activeStartMs / dur).coerceIn(0f, 1f)) * size.width
        val endX = ((activeEndMs / dur).coerceIn(0f, 1f)) * size.width
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3f)
        if (endX > startX) drawLine(Color(0xFF3B82F6).copy(alpha = 0.75f), Offset(startX, size.height / 2), Offset(endX, size.height / 2), strokeWidth = 6f)
        val r = 5.5.dp.toPx(); val cy = size.height / 2f
        keyTimesMs.forEach { t ->
            val cx = ((t / dur).coerceIn(0f, 1f)) * size.width
            val diamond = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
            drawPath(diamond, Color(0xFFF59E0B))
        }
    }
}

@Composable
fun MotionEditorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf(runCatching { ProjectStorage.loadAuto(context) }.getOrElse { Project.sampleDemo() }) }
    val renderer = remember(context) { FrameRenderer(context) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedLayerId by remember { mutableStateOf(project.layers.firstOrNull()?.id) }
    var selectedProp by remember { mutableStateOf(TrackProperty.X) }
    var selectedEasing by remember { mutableStateOf(EasingType.EASE_IN_OUT) }
    var showProjectDrawer by remember { mutableStateOf(false) }
    var showJson by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(InspectorTab.STYLE) }
    var exportProgress by remember { mutableStateOf<Float?>(null) }

    fun updateProject(newProject: Project) { project = newProject; ProjectStorage.saveAuto(context, newProject) }
    fun updateSelectedLayer(fn: (Layer) -> Layer) {
        val id = selectedLayerId ?: return
        updateProject(project.copy(layers = project.layers.map { if (it.id == id) fn(it) else it }))
    }

    val mediaPlayer = remember(project.audio?.uriString) {
        project.audio?.let { track -> runCatching { MediaPlayer().apply { setDataSource(context, Uri.parse(track.uriString)); prepare() } }.getOrNull() }
    }
    DisposableEffect(mediaPlayer) { onDispose { runCatching { mediaPlayer?.release() } } }

    LaunchedEffect(isPlaying, mediaPlayer, project.audio) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        val audio = project.audio ?: return@LaunchedEffect
        if (isPlaying) {
            val targetMs = (currentTimeMs - audio.startTimeMs + audio.trimStartMs).toInt()
            if (targetMs >= 0 && targetMs < mp.duration) { mp.setVolume(audio.volume, audio.volume); mp.seekTo(targetMs); mp.start() }
        } else if (mp.isPlaying) { mp.pause() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val l = Layer.ImageLayer(name = "Image ${project.layers.size + 1}", durationMs = project.durationMs, uriString = uri.toString())
            updateProject(project.copy(layers = project.layers + l)); selectedLayerId = l.id
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val trackName = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio Track"
            updateProject(project.copy(audio = AudioTrack(uriString = uri.toString(), name = trackName)))
            Toast.makeText(context, "Audio added: $trackName", Toast.LENGTH_SHORT).show()
        }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && ProjectStorage.exportToUri(context, uri, project)) Toast.makeText(context, "JSON saved!", Toast.LENGTH_SHORT).show()
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val loaded = uri?.let { ProjectStorage.importFromUri(context, it) }
        if (loaded != null) { updateProject(loaded); selectedLayerId = loaded.layers.firstOrNull()?.id; currentTimeMs = 0L; Toast.makeText(context, "Project loaded!", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (isPlaying) {
            val now = withFrameNanos { it }
            val next = currentTimeMs + (now - lastNanos) / 1_000_000L
            if (next >= project.durationMs) {
                currentTimeMs = 0L
                project.audio?.let { a -> mediaPlayer?.let { mp -> val t = a.trimStartMs.toInt(); if (t < mp.duration) mp.seekTo(t) } }
            } else currentTimeMs = next
            lastNanos = now
        }
    }

    val selectedLayer = project.layers.find { it.id == selectedLayerId }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // 1. Sleek Single-Row Studio Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Motion Canvas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${project.durationMs / 1000}s • ${project.fps}fps • ${project.layers.size} layers", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { showProjectDrawer = !showProjectDrawer }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), modifier = Modifier.height(34.dp)) {
                    Text(if (showProjectDrawer) "Close" else "Project", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    enabled = exportProgress == null,
                    onClick = {
                        isPlaying = false; exportProgress = 0f
                        scope.launch {
                            val res = VideoExporter.exportToMp4(context, project) { p -> exportProgress = p }
                            exportProgress = null
                            res.onSuccess { path -> Toast.makeText(context, "Saved: $path", Toast.LENGTH_LONG).show() }
                                .onFailure { err -> Toast.makeText(context, "Export error: ${err.message}", Toast.LENGTH_LONG).show() }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(if (exportProgress != null) "${(exportProgress!! * 100).toInt()}%" else "Export MP4", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (exportProgress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { exportProgress ?: 0f }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
        }

        // Collapsible Project Settings Drawer
        AnimatedVisibility(visible = showProjectDrawer) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFF171A23), RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { exportJson.launch("${project.title}.json") }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp)) { Text("Save", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "*/*")) }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp)) { Text("Load", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = { showJson = !showJson }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp)) { Text(if (showJson) "Canvas" else "JSON", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = { val d = Project.sampleDemo(); updateProject(d); selectedLayerId = d.layers.firstOrNull()?.id }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp)) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
                }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(3000L, 5000L, 8000L, 10000L, 15000L).forEach { dur ->
                        FilterChip(selected = project.durationMs == dur, onClick = { currentTimeMs = currentTimeMs.coerceAtMost(dur); updateProject(project.copy(durationMs = dur)) }, label = { Text("${dur / 1000}s") })
                    }
                    listOf(24, 30, 60).forEach { f ->
                        FilterChip(selected = project.fps == f, onClick = { updateProject(project.copy(fps = f)) }, label = { Text("${f}fps") })
                    }
                }
                ColorSwatchRow("Stage", project.backgroundColorHex) { hex -> updateProject(project.copy(backgroundColorHex = hex)) }
                if (project.audio != null) {
                    val a = project.audio!!
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Audio: ${a.name.take(10)} (${String.format("%.1fs", a.trimStartMs / 1000f)})", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(135.dp))
                        Slider(value = a.trimStartMs.toFloat(), onValueChange = { t -> isPlaying = false; updateProject(project.copy(audio = a.copy(trimStartMs = t.toLong()))) }, valueRange = 0f..30000f, modifier = Modifier.weight(1f))
                        TextButton(onClick = { isPlaying = false; updateProject(project.copy(audio = null)) }) { Text("X", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 2. Generous 9:16 Preview Stage
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF13161F), RoundedCornerShape(16.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
            if (showJson) {
                Text(remember(project) { project.toJson() }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Color(0xFF93C5FD), modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp))
            } else {
                Canvas(
                    modifier = Modifier.fillMaxHeight().aspectRatio(project.widthPx.toFloat() / project.heightPx.toFloat(), matchHeightConstraintsFirst = true).clip(RoundedCornerShape(14.dp)).border(1.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)).background(Color.Black)
                        .pointerInput(selectedLayerId, currentTimeMs, selectedEasing) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                if (size.width > 0 && size.height > 0 && selectedLayerId != null) {
                                    isPlaying = false
                                    val dx = pan.x * (project.widthPx.toFloat() / size.width)
                                    val dy = pan.y * (project.heightPx.toFloat() / size.height)
                                    updateSelectedLayer { it.withGestureDelta(currentTimeMs, dx, dy, zoom, rotation, selectedEasing) }
                                }
                            }
                        }
                ) {
                    val sx = size.width / project.widthPx.toFloat(); val sy = size.height / project.heightPx.toFloat()
                    drawIntoCanvas { c -> c.nativeCanvas.save(); c.nativeCanvas.scale(sx, sy); renderer.renderFrame(c.nativeCanvas, project, currentTimeMs); c.nativeCanvas.restore() }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 3. Compact Transport & Scrubber Bar
        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF171A23), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (selectedLayer != null) {
                KeyframeTrackBar(project.durationMs, selectedLayer.keyframeGlobalTimesFor(selectedProp), selectedLayer.startTimeMs, selectedLayer.startTimeMs + selectedLayer.durationMs)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { isPlaying = !isPlaying }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), modifier = Modifier.height(32.dp)) {
                    Text(if (isPlaying) "Pause" else "Play", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(8.dp))
                Text(String.format("%.2fs / %.2fs", currentTimeMs / 1000f, project.durationMs / 1000f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(8.dp))
                Slider(value = currentTimeMs.toFloat(), onValueChange = { isPlaying = false; currentTimeMs = it.toLong() }, valueRange = 0f..project.durationMs.toFloat(), modifier = Modifier.weight(1f).height(28.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        // 4. Unified Layer & Add Strip
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { val l = Layer.TextLayer(name = "Text ${project.layers.size + 1}", text = "New Text", durationMs = project.durationMs); updateProject(project.copy(layers = project.layers + l)); selectedLayerId = l.id; activeTab = InspectorTab.STYLE }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp)) { Text("+ Text", style = MaterialTheme.typography.labelSmall) }
            FilledTonalButton(onClick = { val l = Layer.ShapeLayer(name = "Shape ${project.layers.size + 1}", durationMs = project.durationMs); updateProject(project.copy(layers = project.layers + l)); selectedLayerId = l.id; activeTab = InspectorTab.STYLE }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp)) { Text("+ Shape", style = MaterialTheme.typography.labelSmall) }
            FilledTonalButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp)) { Text("+ Image", style = MaterialTheme.typography.labelSmall) }
            FilledTonalButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp)) { Text("+ Audio", style = MaterialTheme.typography.labelSmall) }
            Box(modifier = Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.2f)))
            project.layers.forEach { layer ->
                FilterChip(selected = layer.id == selectedLayerId, onClick = { selectedLayerId = layer.id }, label = { Text(layer.name, style = MaterialTheme.typography.labelSmall) })
            }
        }

        // 5. Clean 3-Tab Bottom Inspector Card
        if (selectedLayer != null) {
            Spacer(Modifier.height(6.dp))
            val eval = KeyframeInterpolator.evaluateTrack(selectedLayer.transform, (currentTimeMs - selectedLayer.startTimeMs).coerceIn(0L, selectedLayer.durationMs))
            val curVal = when (selectedProp) { TrackProperty.X -> eval.x; TrackProperty.Y -> eval.y; TrackProperty.SCALE -> eval.scaleX; TrackProperty.ROTATION -> eval.rotation; TrackProperty.OPACITY -> eval.opacity }

            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF171A23), RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InspectorTab.entries.forEach { tab ->
                        val isSelected = activeTab == tab
                        Surface(
                            onClick = { activeTab = tab },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text(tab.title, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f))
                            }
                        }
                    }
                }

                when (activeTab) {
                    InspectorTab.STYLE -> {
                        if (selectedLayer is Layer.TextLayer) {
                            OutlinedTextField(value = selectedLayer.text, onValueChange = { txt -> updateSelectedLayer { if (it is Layer.TextLayer) it.copy(text = txt) else it } }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Size: ${selectedLayer.fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp))
                                Slider(value = selectedLayer.fontSizeSp, onValueChange = { sz -> updateSelectedLayer { it.withFontSize(sz) } }, valueRange = 18f..160f, modifier = Modifier.weight(1f).height(28.dp))
                            }
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextStyleKind.entries.forEach { ts -> FilterChip(selected = selectedLayer.textStyle == ts, onClick = { updateSelectedLayer { it.withTextStyle(textStyle = ts) } }, label = { Text(ts.name, style = MaterialTheme.typography.labelSmall) }) }
                            }
                            ColorSwatchRow("Color", selectedLayer.colorHex) { hex -> updateSelectedLayer { it.withColor(hex) } }
                        } else if (selectedLayer is Layer.ShapeLayer) {
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShapeKind.entries.forEach { kind -> FilterChip(selected = selectedLayer.kind == kind, onClick = { updateSelectedLayer { it.withShapeKind(kind) } }, label = { Text(kind.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }) }
                            }
                            ColorSwatchRow("Fill", selectedLayer.colorHex) { hex -> updateSelectedLayer { it.withColor(hex) } }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Stroke: ${selectedLayer.strokeWidthPx.toInt()}px", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(76.dp))
                                Slider(value = selectedLayer.strokeWidthPx, onValueChange = { sw -> updateSelectedLayer { it.withShapeStroke(strokeWidthPx = sw) } }, valueRange = 0f..24f, modifier = Modifier.weight(1f).height(28.dp))
                            }
                            if (selectedLayer.strokeWidthPx > 0.5f) {
                                ColorSwatchRow("Border", selectedLayer.strokeColorHex) { hex -> updateSelectedLayer { it.withShapeStroke(strokeColorHex = hex) } }
                            }
                        } else {
                            Text("Image layer selected — drag or pinch directly on the preview stage, or switch to Motion / Effects.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    InspectorTab.MOTION -> {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TrackProperty.entries.forEach { p -> FilterChip(selected = selectedProp == p, onClick = { selectedProp = p }, label = { Text(p.label, style = MaterialTheme.typography.labelSmall) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${selectedProp.label}: ${String.format("%.1f", curVal)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(105.dp))
                            Slider(value = curVal.coerceIn(selectedProp.min, selectedProp.max), onValueChange = { v -> isPlaying = false; updateSelectedLayer { it.withUpdatedTrack(selectedProp, currentTimeMs, v, selectedEasing) } }, valueRange = selectedProp.min..selectedProp.max, modifier = Modifier.weight(1f).height(28.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            EasingType.entries.forEach { e -> FilterChip(selected = selectedEasing == e, onClick = { selectedEasing = e }, label = { Text(e.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }) }
                            TextButton(onClick = {
                                val def = when (selectedProp) { TrackProperty.X -> 540f; TrackProperty.Y -> 960f; TrackProperty.SCALE -> 1f; TrackProperty.ROTATION -> 0f; TrackProperty.OPACITY -> 1f }
                                updateSelectedLayer { it.clearKeyframesFor(selectedProp, def) }
                            }) { Text("Clear Keys", style = MaterialTheme.typography.labelSmall) }
                        }
                    }

                    InspectorTab.EFFECTS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("In: ${String.format("%.1fs", selectedLayer.startTimeMs / 1000f)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                            Slider(value = selectedLayer.startTimeMs.toFloat(), onValueChange = { s -> updateSelectedLayer { it.withTiming(s.toLong(), it.durationMs) } }, valueRange = 0f..(project.durationMs - 200L).coerceAtLeast(200L).toFloat(), modifier = Modifier.weight(1f).height(28.dp))
                            Text("Dur: ${String.format("%.1fs", selectedLayer.durationMs / 1000f)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(62.dp))
                            Slider(value = selectedLayer.durationMs.toFloat(), onValueChange = { d -> updateSelectedLayer { it.withTiming(it.startTimeMs, d.toLong()) } }, valueRange = 200f..project.durationMs.toFloat(), modifier = Modifier.weight(1f).height(28.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Shadow: ${selectedLayer.effects.shadowRadius.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp))
                            Slider(value = selectedLayer.effects.shadowRadius, onValueChange = { r -> updateSelectedLayer { it.withEffects(it.effects.copy(shadowRadius = r)) } }, valueRange = 0f..60f, modifier = Modifier.weight(1f).height(28.dp))
                            Text("Blur: ${selectedLayer.effects.blurRadius.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                            Slider(value = selectedLayer.effects.blurRadius, onValueChange = { b -> updateSelectedLayer { it.withEffects(it.effects.copy(blurRadius = b)) } }, valueRange = 0f..40f, modifier = Modifier.weight(1f).height(28.dp))
                        }
                        ColorSwatchRow("Glow", selectedLayer.effects.shadowColorHex) { hex -> updateSelectedLayer { it.withEffects(it.effects.copy(shadowColorHex = hex)) } }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BlendModeKind.entries.forEach { bm -> FilterChip(selected = selectedLayer.effects.blendMode == bm, onClick = { updateSelectedLayer { it.withEffects(it.effects.copy(blendMode = bm)) } }, label = { Text(bm.name, style = MaterialTheme.typography.labelSmall) }) }
                            OutlinedButton(onClick = { updateProject(project.moveLayer(selectedLayer.id, 1)) }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("Front", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(onClick = { updateProject(project.moveLayer(selectedLayer.id, -1)) }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("Back", style = MaterialTheme.typography.labelSmall) }
                            TextButton(onClick = { val rem = project.layers.filterNot { it.id == selectedLayer.id }; updateProject(project.copy(layers = rem)); selectedLayerId = rem.firstOrNull()?.id }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
