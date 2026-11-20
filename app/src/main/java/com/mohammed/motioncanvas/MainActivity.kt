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
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.mohammed.motioncanvas.data.*
import com.mohammed.motioncanvas.engine.*
import com.mohammed.motioncanvas.export.VideoExporter
import com.mohammed.motioncanvas.model.*
import kotlinx.coroutines.launch

private val AmBg = Color(0xFF08090D)
private val AmPanel = Color(0xFF12151E)
private val AmCard = Color(0xFF1A1E2B)
private val AmMint = Color(0xFF00E5B0)
private val AmAmber = Color(0xFFFFB020)
private val AmText = Color(0xFFF1F5F9)
private val AmMuted = Color(0xFF94A3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = AmMint, onPrimary = Color(0xFF04130F), surface = AmPanel, onSurface = AmText, background = AmBg, onBackground = AmText)) {
                Surface(modifier = Modifier.fillMaxSize(), color = AmBg) { MotionEditorScreen() }
            }
        }
    }
}

private enum class AmTile(val icon: String, val title: String) {
    MOVE("✥", "Move & Transform"),
    COLOR("◐", "Color & Fill"),
    EFFECTS("✦", "Effects"),
    AE_LINK("⛓", "AE Parent & Timing")
}

@Composable
fun ColorSwatchRow(label: String, selectedHex: Long, onSelect: (Long) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AmMuted, modifier = Modifier.width(46.dp))
        SWATCH_COLORS.forEach { hex ->
            val sel = hex == selectedHex
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(hex.toInt())).border(if (sel) 2.5.dp else 1.dp, if (sel) AmMint else Color.White.copy(alpha = 0.25f), CircleShape).clickable { onSelect(hex) })
        }
    }
}

@Composable
fun AlightTouchPad(label: String, onDelta: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).background(AmCard).border(1.dp, AmMint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .pointerInput(label) { detectDragGestures { change, drag -> change.consume(); onDelta(drag.x, drag.y) } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 18.dp.toPx()
            var x = step; while (x < size.width) { drawLine(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = step; while (y < size.height) { drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y)); y += step }
        }
        Text("SWIPE HERE • $label", style = MaterialTheme.typography.labelSmall, color = AmMint.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CenterPlayheadTimeline(
    project: Project,
    currentTimeMs: Long,
    selectedLayerId: String?,
    selectedProp: TrackProperty,
    onScrub: (Long) -> Unit,
    onSelectLayer: (String) -> Unit,
    onToggleVisible: (String) -> Unit,
    onToggleLock: (String) -> Unit
) {
    val dur = project.durationMs.coerceAtLeast(1L).toFloat()
    val pxPerMs = 0.07f
    Row(modifier = Modifier.fillMaxWidth().weight(1f).background(AmPanel, RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp)).padding(6.dp)) {
        // Left Gutter: Eye & Lock icons
        Column(modifier = Modifier.width(68.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Spacer(Modifier.height(14.dp))
            project.layers.asReversed().forEach { layer ->
                val isSel = layer.id == selectedLayerId
                Row(
                    modifier = Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp)).background(if (isSel) AmMint.copy(alpha = 0.16f) else AmCard).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (layer.visible) "👁" else "—", fontSize = 11.sp, color = if (layer.visible) AmText else AmMuted, modifier = Modifier.clickable { onToggleVisible(layer.id) }.padding(2.dp))
                    Text(if (layer.locked) "🔒" else "🔓", fontSize = 11.sp, color = if (layer.locked) AmAmber else AmMuted, modifier = Modifier.clickable { onToggleLock(layer.id) }.padding(2.dp))
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        // Right Track Area: Center-locked playhead + scrolling layer bars
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(AmBg)
                .pointerInput(project.durationMs, currentTimeMs) {
                    detectHorizontalDragGestures { change, drag ->
                        change.consume()
                        val deltaMs = (-drag / pxPerMs).toLong()
                        onScrub((currentTimeMs + deltaMs).coerceIn(0L, project.durationMs))
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Spacer(Modifier.height(14.dp))
                project.layers.asReversed().forEach { layer ->
                    val isSel = layer.id == selectedLayerId
                    val barColor = when (layer) { is Layer.TextLayer -> Color(0xFF2563EB); is Layer.ShapeLayer -> Color(0xFF0D9488); is Layer.ImageLayer -> Color(0xFF7C3AED) }
                    val keys = layer.keyframeGlobalTimesFor(selectedProp)
                    Box(modifier = Modifier.fillMaxWidth().height(28.dp).clickable { onSelectLayer(layer.id) }) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = size.width / 2f
                            val sx = center + (layer.startTimeMs - currentTimeMs) * pxPerMs
                            val ex = center + (layer.startTimeMs + layer.durationMs - currentTimeMs) * pxPerMs
                            drawRoundRect(barColor.copy(alpha = if (isSel) 0.85f else 0.45f), Offset(sx, 1f), Size((ex - sx).coerceAtLeast(10f), size.height - 2f), CornerRadius(8f, 8f))
                            if (isSel) drawRoundRect(AmMint, Offset(sx, 1f), Size((ex - sx).coerceAtLeast(10f), size.height - 2f), CornerRadius(8f, 8f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            val r = 4.5.dp.toPx(); val cy = size.height / 2f
                            keys.forEach { kt -> val kx = center + (kt - currentTimeMs) * pxPerMs; val d = Path().apply { moveTo(kx, cy - r); lineTo(kx + r, cy); lineTo(kx, cy + r); lineTo(kx - r, cy); close() }; drawPath(d, AmAmber) }
                        }
                        Text(layer.name + if (layer.parentId != null) " ⛓" else "", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmText, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp))
                    }
                }
            }
            // Center-Locked Alight Motion Playhead
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                drawLine(Color.White, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(AmMint, 4.5.dp.toPx(), Offset(cx, 5.dp.toPx()))
            }
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
    var selectedLayerId by remember { mutableStateOf<String?>(null) }
    var activeTile by remember { mutableStateOf<AmTile?>(null) }
    var selectedProp by remember { mutableStateOf(TrackProperty.X) }
    var selectedEasing by remember { mutableStateOf(EasingType.EASE_IN_OUT) }
    var showSettings by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showJson by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<Float?>(null) }

    fun updateProject(p: Project) { project = p; ProjectStorage.saveAuto(context, p) }
    fun updateLayerById(id: String, fn: (Layer) -> Layer) { updateProject(project.copy(layers = project.layers.map { if (it.id == id) fn(it) else it })) }
    fun updateSelectedLayer(fn: (Layer) -> Layer) { val id = selectedLayerId ?: return; val l = project.layers.find { it.id == id } ?: return; if (!l.locked) updateLayerById(id, fn) }

    val mediaPlayer = remember(project.audio?.uriString) { project.audio?.let { t -> runCatching { MediaPlayer().apply { setDataSource(context, Uri.parse(t.uriString)); prepare() } }.getOrNull() } }
    DisposableEffect(mediaPlayer) { onDispose { runCatching { mediaPlayer?.release() } } }
    LaunchedEffect(isPlaying, mediaPlayer, project.audio) {
        val mp = mediaPlayer ?: return@LaunchedEffect; val audio = project.audio ?: return@LaunchedEffect
        if (isPlaying) { val t = (currentTimeMs - audio.startTimeMs + audio.trimStartMs).toInt(); if (t >= 0 && t < mp.duration) { mp.setVolume(audio.volume, audio.volume); mp.seekTo(t); mp.start() } } else if (mp.isPlaying) mp.pause()
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; val l = Layer.ImageLayer(name = "Image ${project.layers.size + 1}", durationMs = project.durationMs, uriString = uri.toString()); updateProject(project.copy(layers = project.layers + l)); selectedLayerId = l.id }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; val n = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio"; updateProject(project.copy(audio = AudioTrack(uriString = uri.toString(), name = n))); Toast.makeText(context, "Audio: $n", Toast.LENGTH_SHORT).show() }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null && ProjectStorage.exportToUri(context, uri, project)) Toast.makeText(context, "JSON saved!", Toast.LENGTH_SHORT).show() }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> val l = uri?.let { ProjectStorage.importFromUri(context, it) }; if (l != null) { updateProject(l); selectedLayerId = null; activeTile = null; currentTimeMs = 0L } }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect; var last = withFrameNanos { it }
        while (isPlaying) { val now = withFrameNanos { it }; val next = currentTimeMs + (now - last) / 1_000_000L; if (next >= project.durationMs) { currentTimeMs = 0L; project.audio?.let { a -> mediaPlayer?.let { mp -> val t = a.trimStartMs.toInt(); if (t < mp.duration) mp.seekTo(t) } } } else currentTimeMs = next; last = now }
    }

    val selectedLayer = project.layers.find { it.id == selectedLayerId }
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(AmMint))
                    Text("Alight Studio Pro", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AmText)
                    Text("${project.durationMs / 1000}s • ${project.fps}fps", style = MaterialTheme.typography.labelSmall, color = AmMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(onClick = { showSettings = !showSettings }, shape = RoundedCornerShape(8.dp), color = AmCard, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) { Text(if (showSettings) "Done" else "Project", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
                    Surface(onClick = { if (exportProgress == null) { isPlaying = false; exportProgress = 0f; scope.launch { val res = VideoExporter.exportToMp4(context, project) { exportProgress = it }; exportProgress = null; res.onSuccess { Toast.makeText(context, "Exported: $it", Toast.LENGTH_LONG).show() }.onFailure { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show() } } } }, shape = RoundedCornerShape(8.dp), color = AmMint) { Text(if (exportProgress != null) "${(exportProgress!! * 100).toInt()}%" else "Export", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF04130F), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                }
            }
            if (exportProgress != null) { Spacer(Modifier.height(4.dp)); LinearProgressIndicator(progress = { exportProgress ?: 0f }, color = AmMint, modifier = Modifier.fillMaxWidth().height(3.dp)) }

            AnimatedVisibility(visible = showSettings) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(AmPanel, RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { exportJson.launch("${project.title}.json") }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(30.dp)) { Text("Save JSON", style = MaterialTheme.typography.labelSmall, color = AmText) }
                        OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "*/*")) }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(30.dp)) { Text("Load JSON", style = MaterialTheme.typography.labelSmall, color = AmText) }
                        OutlinedButton(onClick = { showJson = !showJson }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(30.dp)) { Text(if (showJson) "Canvas" else "JSON", style = MaterialTheme.typography.labelSmall, color = AmText) }
                        OutlinedButton(onClick = { val d = Project.sampleDemo(); updateProject(d); selectedLayerId = null; activeTile = null }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(30.dp)) { Text("Reset", style = MaterialTheme.typography.labelSmall, color = AmText) }
                    }
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3000L, 5000L, 8000L, 10000L, 15000L).forEach { dur -> FilterChip(selected = project.durationMs == dur, onClick = { currentTimeMs = currentTimeMs.coerceAtMost(dur); updateProject(project.copy(durationMs = dur)) }, label = { Text("${dur / 1000}s") }) }
                        listOf(24, 30, 60).forEach { f -> FilterChip(selected = project.fps == f, onClick = { updateProject(project.copy(fps = f)) }, label = { Text("${f}fps") }) }
                    }
                    ColorSwatchRow("Stage", project.backgroundColorHex) { hex -> updateProject(project.copy(backgroundColorHex = hex)) }
                }
            }

            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.weight(1.25f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showJson) Text(remember(project) { project.toJson() }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = AmMint, modifier = Modifier.fillMaxSize().background(AmPanel, RoundedCornerShape(12.dp)).verticalScroll(rememberScrollState()).padding(10.dp))
                else Canvas(modifier = Modifier.fillMaxHeight().aspectRatio(project.widthPx.toFloat() / project.heightPx.toFloat(), matchHeightConstraintsFirst = true).clip(RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)).background(Color.Black).pointerInput(selectedLayerId, currentTimeMs, selectedEasing) {
                    detectTransformGestures { _, pan, zoom, rotation -> if (size.width > 0 && size.height > 0 && selectedLayerId != null) { isPlaying = false; updateSelectedLayer { it.withGestureDelta(currentTimeMs, pan.x * (project.widthPx.toFloat() / size.width), pan.y * (project.heightPx.toFloat() / size.height), zoom, rotation, selectedEasing) } } }
                }) { val sx = size.width / project.widthPx.toFloat(); val sy = size.height / project.heightPx.toFloat(); drawIntoCanvas { c -> c.nativeCanvas.save(); c.nativeCanvas.scale(sx, sy); renderer.renderFrame(c.nativeCanvas, project, currentTimeMs); c.nativeCanvas.restore() } }
            }

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(String.format("%02d:%05.2f", currentTimeMs / 60000L, (currentTimeMs % 60000L) / 1000f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = AmMint)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = { isPlaying = false; currentTimeMs = 0L }, shape = CircleShape, color = AmCard) { Text("|<", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
                    Surface(onClick = { isPlaying = !isPlaying }, shape = CircleShape, color = AmMint) { Text(if (isPlaying) "Pause" else "Play", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF04130F), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                    if (selectedLayer != null) {
                        Surface(onClick = { val keys = selectedLayer.keyframeGlobalTimesFor(selectedProp); val prev = keys.lastOrNull { it < currentTimeMs - 10L } ?: keys.firstOrNull(); if (prev != null) { isPlaying = false; currentTimeMs = prev } }, shape = CircleShape, color = AmCard) { Text("<◆", style = MaterialTheme.typography.labelSmall, color = AmAmber, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) }
                        Surface(onClick = { val keys = selectedLayer.keyframeGlobalTimesFor(selectedProp); val next = keys.firstOrNull { it > currentTimeMs + 10L } ?: keys.lastOrNull(); if (next != null) { isPlaying = false; currentTimeMs = next } }, shape = CircleShape, color = AmCard) { Text("◆>", style = MaterialTheme.typography.labelSmall, color = AmAmber, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) }
                    }
                }
                Text(String.format("%02d:%05.2f", project.durationMs / 60000L, (project.durationMs % 60000L) / 1000f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = AmMuted)
            }
            Spacer(Modifier.height(4.dp))
            CenterPlayheadTimeline(
                project = project,
                currentTimeMs = currentTimeMs,
                selectedLayerId = selectedLayerId,
                selectedProp = selectedProp,
                onScrub = { isPlaying = false; currentTimeMs = it },
                onSelectLayer = { id -> selectedLayerId = id },
                onToggleVisible = { id -> updateLayerById(id) { it.withAeProps(visible = !it.visible) } },
                onToggleLock = { id -> updateLayerById(id) { it.withAeProps(locked = !it.locked) } }
            )
            Spacer(Modifier.height(52.dp))
        }

        // Floating Dismissible Alight Motion Layer Drawer (Opens only when a layer is selected)
        if (selectedLayer != null) {
            val eval = KeyframeInterpolator.evaluateTrack(selectedLayer.transform, (currentTimeMs - selectedLayer.startTimeMs).coerceIn(0L, selectedLayer.durationMs))
            val curVal = when (selectedProp) { TrackProperty.X -> eval.x; TrackProperty.Y -> eval.y; TrackProperty.SCALE -> eval.scaleX; TrackProperty.ROTATION -> eval.rotation; TrackProperty.OPACITY -> eval.opacity }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                    .background(AmPanel, RoundedCornerShape(18.dp)).border(1.dp, AmMint.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header with Layer Name + Close (✕) button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (activeTile != null) {
                            Surface(onClick = { activeTile = null }, shape = RoundedCornerShape(6.dp), color = AmCard) { Text("‹ Tiles", style = MaterialTheme.typography.labelSmall, color = AmMint, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                        }
                        Text(activeTile?.title ?: selectedLayer.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AmText)
                    }
                    Surface(onClick = { activeTile = null; selectedLayerId = null }, shape = CircleShape, color = AmCard) {
                        Text("✕", style = MaterialTheme.typography.labelSmall, color = AmMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                if (activeTile == null) {
                    // Alight Motion Square Tile Grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmTile.entries.forEach { tile ->
                            Surface(onClick = { activeTile = tile }, shape = RoundedCornerShape(12.dp), color = AmCard, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), modifier = Modifier.weight(1f).height(64.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(4.dp)) {
                                    Text(tile.icon, fontSize = 18.sp, color = AmMint)
                                    Spacer(Modifier.height(2.dp))
                                    Text(tile.title.substringBefore(" "), fontSize = 10.sp, color = AmText, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else when (activeTile) {
                    AmTile.MOVE -> {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TrackProperty.entries.forEach { p -> FilterChip(selected = selectedProp == p, onClick = { selectedProp = p }, label = { Text(p.label, style = MaterialTheme.typography.labelSmall) }) }
                        }
                        AlightTouchPad(selectedProp.label) { dx, dy ->
                            isPlaying = false
                            val delta = when (selectedProp) { TrackProperty.X -> dx * 1.5f; TrackProperty.Y -> dy * 1.5f; TrackProperty.SCALE -> -dy * 0.01f; TrackProperty.ROTATION -> dx * 0.5f; TrackProperty.OPACITY -> -dy * 0.005f }
                            updateSelectedLayer { it.withUpdatedTrack(selectedProp, currentTimeMs, (curVal + delta).coerceIn(selectedProp.min, selectedProp.max), selectedEasing) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${selectedProp.label}: ${String.format("%.1f", curVal)}", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.width(92.dp))
                            Slider(value = curVal.coerceIn(selectedProp.min, selectedProp.max), onValueChange = { v -> isPlaying = false; updateSelectedLayer { it.withUpdatedTrack(selectedProp, currentTimeMs, v, selectedEasing) } }, valueRange = selectedProp.min..selectedProp.max, modifier = Modifier.weight(1f).height(24.dp))
                            Surface(onClick = { isPlaying = false; updateSelectedLayer { it.withUpdatedTrack(selectedProp, currentTimeMs, curVal, selectedEasing) } }, shape = RoundedCornerShape(8.dp), color = AmAmber.copy(alpha = 0.2f), border = BorderStroke(1.dp, AmAmber)) { Text("+◆", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AmAmber, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            EasingType.entries.forEach { e -> FilterChip(selected = selectedEasing == e, onClick = { selectedEasing = e }, label = { Text(e.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }) }
                        }
                    }

                    AmTile.COLOR -> {
                        if (selectedLayer is Layer.TextLayer) {
                            OutlinedTextField(value = selectedLayer.text, onValueChange = { txt -> updateSelectedLayer { if (it is Layer.TextLayer) it.copy(text = txt) else it } }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("Size: ${selectedLayer.fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.width(72.dp)); Slider(value = selectedLayer.fontSizeSp, onValueChange = { sz -> updateSelectedLayer { it.withFontSize(sz) } }, valueRange = 18f..160f, modifier = Modifier.weight(1f).height(24.dp)) }
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextStyleKind.entries.forEach { ts -> FilterChip(selected = selectedLayer.textStyle == ts, onClick = { updateSelectedLayer { it.withTextStyle(textStyle = ts) } }, label = { Text(ts.name, style = MaterialTheme.typography.labelSmall) }) } }
                            ColorSwatchRow("Color", selectedLayer.colorHex) { hex -> updateSelectedLayer { it.withColor(hex) } }
                        } else if (selectedLayer is Layer.ShapeLayer) {
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> FilterChip(selected = selectedLayer.kind == kind, onClick = { updateSelectedLayer { it.withShapeKind(kind) } }, label = { Text(kind.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }) } }
                            ColorSwatchRow("Fill", selectedLayer.colorHex) { hex -> updateSelectedLayer { it.withColor(hex) } }
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("Stroke: ${selectedLayer.strokeWidthPx.toInt()}px", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.width(76.dp)); Slider(value = selectedLayer.strokeWidthPx, onValueChange = { sw -> updateSelectedLayer { it.withShapeStroke(strokeWidthPx = sw) } }, valueRange = 0f..24f, modifier = Modifier.weight(1f).height(24.dp)) }
                            if (selectedLayer.strokeWidthPx > 0.5f) ColorSwatchRow("Border", selectedLayer.strokeColorHex) { hex -> updateSelectedLayer { it.withShapeStroke(strokeColorHex = hex) } }
                        } else Text("Image layer — use Move & Transform or Effects.", style = MaterialTheme.typography.bodySmall, color = AmMuted)
                    }
                    AmTile.EFFECTS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Shadow: ${selectedLayer.effects.shadowRadius.toInt()}", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.width(72.dp)); Slider(value = selectedLayer.effects.shadowRadius, onValueChange = { r -> updateSelectedLayer { it.withEffects(it.effects.copy(shadowRadius = r)) } }, valueRange = 0f..60f, modifier = Modifier.weight(1f).height(24.dp)); Text("Blur: ${selectedLayer.effects.blurRadius.toInt()}", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.width(56.dp)); Slider(value = selectedLayer.effects.blurRadius, onValueChange = { b -> updateSelectedLayer { it.withEffects(it.effects.copy(blurRadius = b)) } }, valueRange = 0f..40f, modifier = Modifier.weight(1f).height(24.dp)) }
                        ColorSwatchRow("Glow", selectedLayer.effects.shadowColorHex) { hex -> updateSelectedLayer { it.withEffects(it.effects.copy(shadowColorHex = hex)) } }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { BlendModeKind.entries.forEach { bm -> FilterChip(selected = selectedLayer.effects.blendMode == bm, onClick = { updateSelectedLayer { it.withEffects(it.effects.copy(blendMode = bm)) } }, label = { Text(bm.name, style = MaterialTheme.typography.labelSmall) }) } }
                    }
                    AmTile.AE_LINK -> {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Parent:", style = MaterialTheme.typography.labelSmall, color = AmMuted)
                            FilterChip(selected = selectedLayer.parentId == null, onClick = { updateSelectedLayer { it.withAeProps(parentId = null) } }, label = { Text("None", style = MaterialTheme.typography.labelSmall) })
                            project.layers.filter { it.id != selectedLayer.id }.forEach { cand -> FilterChip(selected = selectedLayer.parentId == cand.id, onClick = { updateSelectedLayer { it.withAeProps(parentId = cand.id) } }, label = { Text(cand.name, style = MaterialTheme.typography.labelSmall) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Anchor X: ${selectedLayer.anchorX.toInt()}", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.width(82.dp)); Slider(value = selectedLayer.anchorX, onValueChange = { ax -> updateSelectedLayer { it.withAeProps(anchorX = ax) } }, valueRange = -300f..300f, modifier = Modifier.weight(1f).height(24.dp)); Text("Skew: ${String.format("%.2f", selectedLayer.skewX)}", style = MaterialTheme.typography.labelSmall, color = AmText, modifier = Modifier.width(68.dp)); Slider(value = selectedLayer.skewX, onValueChange = { sk -> updateSelectedLayer { it.withAeProps(skewX = sk) } }, valueRange = -1f..1f, modifier = Modifier.weight(1f).height(24.dp)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { updateProject(project.moveLayer(selectedLayer.id, 1)) }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(28.dp)) { Text("Up", style = MaterialTheme.typography.labelSmall, color = AmText) }
                            OutlinedButton(onClick = { updateProject(project.moveLayer(selectedLayer.id, -1)) }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(28.dp)) { Text("Down", style = MaterialTheme.typography.labelSmall, color = AmText) }
                            TextButton(onClick = { val rem = project.layers.filterNot { it.id == selectedLayer.id }; updateProject(project.copy(layers = rem)); selectedLayerId = null; activeTile = null }) { Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    null -> {}
                }
            }
        } else {
            // Alight Motion Floating Mint "+" Creation Button when drawer is dismissed
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(visible = showAddMenu) {
                    Row(modifier = Modifier.background(AmCard, RoundedCornerShape(14.dp)).border(1.dp, AmMint.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = { showAddMenu = false; val l = Layer.ShapeLayer(name = "Shape ${project.layers.size + 1}", durationMs = project.durationMs); updateProject(project.copy(layers = project.layers + l)); selectedLayerId = l.id }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(34.dp)) { Text("Shape", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalButton(onClick = { showAddMenu = false; val l = Layer.TextLayer(name = "Text ${project.layers.size + 1}", text = "New Text", durationMs = project.durationMs); updateProject(project.copy(layers = project.layers + l)); selectedLayerId = l.id }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(34.dp)) { Text("Text", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalButton(onClick = { showAddMenu = false; imagePicker.launch(arrayOf("image/*")) }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(34.dp)) { Text("Image", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalButton(onClick = { showAddMenu = false; audioPicker.launch(arrayOf("audio/*")) }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(34.dp)) { Text("Audio", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                FloatingActionButton(onClick = { showAddMenu = !showAddMenu }, containerColor = AmMint, contentColor = Color(0xFF04130F), shape = CircleShape, modifier = Modifier.size(48.dp)) { Text(if (showAddMenu) "×" else "+", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
