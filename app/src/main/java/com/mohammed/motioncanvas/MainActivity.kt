package com.mohammed.motioncanvas

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import com.mohammed.motioncanvas.data.*
import com.mohammed.motioncanvas.engine.FrameRenderer
import com.mohammed.motioncanvas.engine.KeyframeInterpolator
import com.mohammed.motioncanvas.model.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { MotionEditorScreen() }
            }
        }
    }
}

@Composable
fun MotionEditorScreen() {
    val context = LocalContext.current
    var project by remember { mutableStateOf(ProjectStorage.loadAuto(context)) }
    val renderer = remember(context) { FrameRenderer(context) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedLayerId by remember { mutableStateOf(project.layers.firstOrNull()?.id) }
    var selectedProp by remember { mutableStateOf(TrackProperty.X) }
    var selectedEasing by remember { mutableStateOf(EasingType.EASE_IN_OUT) }
    var showJson by remember { mutableStateOf(false) }

    fun updateProject(newProject: Project) {
        project = newProject
        ProjectStorage.saveAuto(context, newProject)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val newLayer = Layer.ImageLayer(name = "Image ${project.layers.size + 1}", uriString = uri.toString())
            updateProject(project.copy(layers = project.layers + newLayer))
            selectedLayerId = newLayer.id
        }
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && ProjectStorage.exportToUri(context, uri, project)) Toast.makeText(context, "JSON saved!", Toast.LENGTH_SHORT).show()
    }

    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val loaded = uri?.let { ProjectStorage.importFromUri(context, it) }
        if (loaded != null) {
            updateProject(loaded); selectedLayerId = loaded.layers.firstOrNull()?.id; currentTimeMs = 0L
            Toast.makeText(context, "Project loaded!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (isPlaying) {
            val now = withFrameNanos { it }
            currentTimeMs = (currentTimeMs + (now - lastNanos) / 1_000_000L) % project.durationMs
            lastNanos = now
        }
    }
    val selectedLayer = project.layers.find { it.id == selectedLayerId }
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { exportJson.launch("${project.title}.json") }) { Text("Save JSON") }
            OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "*/*")) }) { Text("Load JSON") }
            OutlinedButton(onClick = { showJson = !showJson }) { Text(if (showJson) "Canvas" else "Code") }
            OutlinedButton(onClick = { val d = Project.sampleDemo(); updateProject(d); selectedLayerId = d.layers.firstOrNull()?.id }) { Text("Reset") }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showJson) {
                Text(remember(project) { project.toJson() }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            } else {
                Canvas(Modifier.fillMaxHeight().aspectRatio(project.widthPx.toFloat() / project.heightPx.toFloat()).clip(RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).background(Color.Black)) {
                    val sx = size.width / project.widthPx.toFloat(); val sy = size.height / project.heightPx.toFloat()
                    drawIntoCanvas { c -> c.nativeCanvas.save(); c.nativeCanvas.scale(sx, sy); renderer.renderFrame(c.nativeCanvas, project, currentTimeMs); c.nativeCanvas.restore() }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { isPlaying = !isPlaying }) { Text(if (isPlaying) "Pause" else "Play") }
            Spacer(Modifier.width(10.dp))
            Text(String.format("%.2fs / %.2fs", currentTimeMs / 1000f, project.durationMs / 1000f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            Slider(value = currentTimeMs.toFloat(), onValueChange = { isPlaying = false; currentTimeMs = it.toLong() }, valueRange = 0f..project.durationMs.toFloat(), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { val l = Layer.TextLayer(name = "Text ${project.layers.size + 1}", text = "New Text"); updateProject(project.copy(layers = project.layers + l)); selectedLayerId = l.id }) { Text("+ Text") }
            FilledTonalButton(onClick = { val l = Layer.ShapeLayer(name = "Shape ${project.layers.size + 1}"); updateProject(project.copy(layers = project.layers + l)); selectedLayerId = l.id }) { Text("+ Shape") }
            FilledTonalButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("+ Image") }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            project.layers.forEach { layer -> FilterChip(selected = layer.id == selectedLayerId, onClick = { selectedLayerId = layer.id }, label = { Text(layer.name) }) }
        }
        if (selectedLayer != null) {
            val eval = KeyframeInterpolator.evaluateTrack(selectedLayer.transform, (currentTimeMs - selectedLayer.startTimeMs).coerceIn(0L, selectedLayer.durationMs))
            val curVal = when (selectedProp) { TrackProperty.X -> eval.x; TrackProperty.Y -> eval.y; TrackProperty.SCALE -> eval.scaleX; TrackProperty.ROTATION -> eval.rotation; TrackProperty.OPACITY -> eval.opacity }
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                if (selectedLayer is Layer.TextLayer) {
                    OutlinedTextField(value = selectedLayer.text, onValueChange = { txt -> updateProject(project.copy(layers = project.layers.map { if (it.id == selectedLayer.id) selectedLayer.copy(text = txt) else it })) }, label = { Text("Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrackProperty.entries.forEach { p -> FilterChip(selected = selectedProp == p, onClick = { selectedProp = p }, label = { Text(p.label) }) }
                    TextButton(onClick = { val rem = project.layers.filterNot { it.id == selectedLayer.id }; updateProject(project.copy(layers = rem)); selectedLayerId = rem.firstOrNull()?.id }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedProp.label}: ${String.format("%.1f", curVal)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(105.dp))
                    Slider(value = curVal.coerceIn(selectedProp.min, selectedProp.max), onValueChange = { v -> isPlaying = false; val u = selectedLayer.withUpdatedTrack(selectedProp, currentTimeMs, v, selectedEasing); updateProject(project.copy(layers = project.layers.map { if (it.id == selectedLayer.id) u else it })) }, valueRange = selectedProp.min..selectedProp.max, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    EasingType.entries.forEach { e -> FilterChip(selected = selectedEasing == e, onClick = { selectedEasing = e }, label = { Text(e.name.replace("_", " ")) }) }
                    TextButton(onClick = { val def = when (selectedProp) { TrackProperty.X -> 540f; TrackProperty.Y -> 960f; TrackProperty.SCALE -> 1f; TrackProperty.ROTATION -> 0f; TrackProperty.OPACITY -> 1f }; val c = selectedLayer.clearKeyframesFor(selectedProp, def); updateProject(project.copy(layers = project.layers.map { if (it.id == selectedLayer.id) c else it })) }) { Text("Clear Keys") }
                }
            }
        }
    }
}
