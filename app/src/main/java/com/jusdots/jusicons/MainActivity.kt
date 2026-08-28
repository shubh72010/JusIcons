package com.jusdots.jusicons

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.jusdots.jusicons.engine.IconPackGenerator
import com.jusdots.jusicons.engine.JusIconsRenderer
import com.jusdots.jusicons.engine.MonoProcessor
import com.jusdots.jusicons.engine.RenderOptions
import com.jusdots.jusicons.ui.theme.JusIconsTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { JusIconsTheme { JusIconsTestScreen() } }
    }
}

data class AppIconEntry(val label: String, val pkg: String, val original: Drawable, val rendered: Drawable)

@Composable
fun JusIconsTestScreen() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<AppIconEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tracePkg by remember { mutableStateOf<String?>(null) }
    var traceStages by remember { mutableStateOf<List<Pair<String, androidx.compose.ui.graphics.ImageBitmap>>>(emptyList()) }
    var traceLabel by remember { mutableStateOf("") }
    var forensicScale by remember { mutableStateOf(false) } // visual 0.72 matches Image 2 black circle, not tiny 0.3888
    var showBg by remember { mutableStateOf(true) } // Nothing-style = black circle bg
    var binary by remember { mutableStateOf(false) } // continuous preserves inner lines
    var generating by remember { mutableStateOf(false) }
    var generateProgress by remember { mutableStateOf("") }

    LaunchedEffect(Unit, forensicScale, showBg, binary) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolves = pm.queryIntentActivities(mainIntent, 0)
        val sorted = resolves.sortedBy { it.loadLabel(pm).toString().lowercase() }
        val renderer = JusIconsRenderer(context)
        val sizePx = (64 * context.resources.displayMetrics.density).toInt()
        val opts = RenderOptions(forensicScale = forensicScale, showBackground = showBg, binary = binary)
        val list = mutableListOf<AppIconEntry>()
        for (ri in sorted.take(40)) {
            try {
                val ai: ApplicationInfo = pm.getApplicationInfo(ri.activityInfo.packageName, 0)
                val original: Drawable = pm.getApplicationIcon(ai) ?: continue
                val rendered: Drawable = renderer.renderForPackage(ai.packageName, original, sizePx, opts)
                val label = pm.getApplicationLabel(ai).toString()
                list.add(AppIconEntry(label, ai.packageName, original, rendered))
            } catch (_: Exception) {}
        }
        if (list.isEmpty()) {
            try {
                val ai = pm.getApplicationInfo(context.packageName, 0)
                val original = pm.getApplicationIcon(ai)
                val rendered = JusIconsRenderer(context).render(original, (64 * context.resources.displayMetrics.density).toInt())
                list.add(AppIconEntry("JusIcons", context.packageName, original, rendered))
            } catch (_: Exception) {}
        }
        entries = list
        loading = false
        // auto-trace first entry or YouTube if present
        val auto = list.find { it.pkg.contains("youtube") } ?: list.firstOrNull()
        if (auto != null) tracePkg = auto.pkg
    }

    // When tracePkg changes, re-run debug pipeline for that entry
    LaunchedEffect(tracePkg, entries, forensicScale, showBg, binary) {
        val pkg = tracePkg ?: return@LaunchedEffect
        val entry = entries.find { it.pkg == pkg } ?: return@LaunchedEffect
        traceLabel = "${entry.label} (${entry.pkg})"
        val sizePx = 192
        val renderer = JusIconsRenderer(context)
        val stages = mutableListOf<Pair<String, androidx.compose.ui.graphics.ImageBitmap>>()
        val debug = object : MonoProcessor.DebugSink {
            override fun onStage(name: String, bitmap: Bitmap) {
                try {
                    val dir = File(context.cacheDir, "debug-output").apply { mkdirs() }
                    val file = File(dir, "$name.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (_: Exception) {}
                try {
                    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    stages.add(name to copy.asImageBitmap())
                } catch (_: Exception) {}
            }
        }
        val opts = RenderOptions(forensicScale = forensicScale, showBackground = showBg, binary = binary)
        renderer.renderWithDebug(entry.pkg, entry.original, sizePx, opts, debug)
        // Sort by 01..08 prefix for stable order
        traceStages = stages.sortedBy { it.first }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Text("JusIcons — ORIGINAL | JUSICONS (${entries.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
            if (tracePkg != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Forensic trace: $traceLabel", style = MaterialTheme.typography.labelLarge)
                        Text("Tap any row to trace it. Saved to cache/debug-output/ (adb pull). 07 most similar = forensic 0.3888 scale too small.", style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Button(onClick = {
                                generating = true
                                generateProgress = "Scanning..."
                            }, enabled = !generating, modifier = Modifier.padding(end = 8.dp)) { Text(if (generating) "Generating…" else "Generate Pack (Device)", style = MaterialTheme.typography.labelSmall) }
                            if (generating) Text(generateProgress, style = MaterialTheme.typography.labelSmall)
                        }
                        if (generating) {
                            LaunchedEffect(Unit) {
                                val gen = IconPackGenerator(context, JusIconsRenderer(context))
                                val outDir = File(context.cacheDir, "generated_pack").apply { mkdirs() }
                                val list = gen.generateForInstalled(40, outDir) { cur, total -> generateProgress = "$cur/$total" }
                                generateProgress = "Done ${list.size} → cache/generated_pack/ (adb pull)"
                                generating = false
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState())) {
                            Text("Forensic", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = forensicScale, onCheckedChange = { forensicScale = it }, modifier = Modifier.padding(horizontal = 4.dp))
                            Text("Visual", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(8.dp))
                            Text("BG", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = showBg, onCheckedChange = { showBg = it }, modifier = Modifier.padding(start = 4.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("B&W", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = binary, onCheckedChange = { binary = it }, modifier = Modifier.padding(start = 4.dp))
                        }
                        Text("BG=black circle (pack applied), no-BG=glyph only. Visual=large (Image2), Forensic=tiny (0.3888). B&W=hard threshold.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                        if (traceStages.isEmpty()) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        } else {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(traceStages) { (name, bmp) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                                        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                                        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Text("01→02→03→04_hist(dominant+fill)→05_remapped(continuous)→06_rect(centered square)→07_cropped→08_final (SRC_IN fg on bg)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                HorizontalDivider()
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ORIGINAL", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("JUSICONS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    items(entries) { e ->
                        IconRow(e, selected = e.pkg == tracePkg, onClick = { tracePkg = e.pkg })
                    }
                }
            }
        }
    }
}

@Composable
fun IconRow(entry: AppIconEntry, selected: Boolean, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val size = 64.dp
    val origBm = remember(entry.original) {
        try { entry.original.toBitmap(192, 192).asImageBitmap() } catch (_: Exception) { null }
    }
    val renderedBm = remember(entry.rendered) {
        try { entry.rendered.toBitmap(192, 192).asImageBitmap() } catch (_: Exception) { null }
    }
    val component = remember(entry.pkg) {
        try {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply { addCategory(android.content.Intent.CATEGORY_LAUNCHER); setPackage(entry.pkg) }
            val ri = pm.queryIntentActivities(intent, 0).firstOrNull()
            ri?.let { "ComponentInfo{" + it.activityInfo.packageName + "/" + it.activityInfo.name + "}" } ?: entry.pkg
        } catch (_: Exception) { entry.pkg }
    }
    val isMapped = remember(entry.pkg) {
        try { com.jusdots.jusicons.engine.ThemedIconProvider(context).getThemeDataForPackage(entry.pkg) != null } catch(_:Exception){false}
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick() }.background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (origBm != null) Image(bitmap = origBm, contentDescription = null, modifier = Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant))
            else Box(Modifier.size(size).background(MaterialTheme.colorScheme.errorContainer))
            Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
            Text(entry.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (renderedBm != null) Image(bitmap = renderedBm, contentDescription = null, modifier = Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant))
            else Box(Modifier.size(size).background(MaterialTheme.colorScheme.errorContainer))
            Text(if(isMapped) "Curated" else "Generic d7/f", style = MaterialTheme.typography.labelSmall, color = if(isMapped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(component, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 2.dp))
}
