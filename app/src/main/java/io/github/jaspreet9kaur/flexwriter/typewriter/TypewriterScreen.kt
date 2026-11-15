package io.github.jaspreet9kaur.flexwriter.typewriter

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jaspreet9kaur.flexwriter.fold.rememberFoldInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

private val Ink = Color(0xFF26231F)
private val DeckTop = Color(0xFF2A2825)
private val DeckBottom = Color(0xFF121110)
private const val WORD_GOAL = 2000

@Composable
fun TypewriterScreen(activity: Activity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val store = remember { DocStore(ctx) }
    val sound = remember { TypewriterSound(ctx) }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    var docId by rememberSaveable { mutableStateOf(store.currentId()) }
    var text by remember(docId) { mutableStateOf(store.load(docId)) }
    var lampOn by rememberSaveable { mutableStateOf(true) }
    var soundOn by rememberSaveable { mutableStateOf(true) }
    var kbVisible by rememberSaveable { mutableStateOf(true) }
    var showPages by remember { mutableStateOf(false) }
    val strike = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Autosave shortly after typing stops.
    LaunchedEffect(docId, text) {
        delay(600)
        store.save(docId, text)
    }

    fun strikeFx(kind: TypewriterSound.Kind) {
        if (soundOn) sound.play(kind)
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        scope.launch {
            strike.snapTo(1f)
            strike.animateTo(0f, tween(170))
        }
    }

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(send, "Share page"))
    }

    val fold = rememberFoldInfo(activity)
    var rootTopPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(DeckBottom)
            .onGloballyPositioned { rootTopPx = it.positionInWindow().y.roundToInt() },
    ) {
        val density = LocalDensity.current
        val totalPx = constraints.maxHeight
        // Split the screen exactly at the hinge so the paper sits on the upper half
        // and the keyboard deck on the lower half, like a little laptop.
        val hingePx = if (fold.hasHinge) {
            ((fold.hingeTopPx + fold.hingeBottomPx) / 2 - rootTopPx).coerceIn(totalPx / 3, totalPx * 2 / 3)
        } else {
            totalPx / 2
        }
        val hingeDp = with(density) { hingePx.toDp() }

        Column(Modifier.fillMaxSize()) {
            Paper(
                text = text,
                lampOn = lampOn,
                strike = strike.value,
                words = DocStore.countWords(text),
                onBack = onBack,
                onShare = ::share,
                onTap = { kbVisible = true },
                modifier = Modifier.fillMaxWidth().height(hingeDp),
            )
            Deck(
                kbVisible = kbVisible,
                lampOn = lampOn,
                soundOn = soundOn,
                autoCap = text.isEmpty() || text.endsWith("\n") || text.endsWith(". ") ||
                    text.endsWith("? ") || text.endsWith("! "),
                onShowKeyboard = { kbVisible = true },
                onHideKeyboard = { kbVisible = false },
                onNewPage = {
                    store.save(docId, text)
                    val id = store.newId()
                    store.setCurrent(id)
                    docId = id
                    if (soundOn) sound.play(TypewriterSound.Kind.Return)
                },
                onPages = { showPages = true },
                onCopy = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("Typewriter page", text))
                    Toast.makeText(ctx, "Page copied", Toast.LENGTH_SHORT).show()
                },
                onLamp = { lampOn = !lampOn },
                onSound = { soundOn = !soundOn },
                onText = { s ->
                    text += s
                    strikeFx(if (s == " ") TypewriterSound.Kind.Space else TypewriterSound.Kind.Key)
                },
                onBackspace = {
                    if (text.isNotEmpty()) {
                        text = text.dropLast(1)
                        strikeFx(TypewriterSound.Kind.Back)
                    }
                },
                onReturn = {
                    text += "\n"
                    strikeFx(TypewriterSound.Kind.Return)
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }

    if (showPages) {
        PagesDialog(
            store = store,
            currentId = docId,
            onOpen = { id ->
                store.save(docId, text)
                store.setCurrent(id)
                docId = id
                showPages = false
            },
            onDismiss = { showPages = false },
        )
    }
}

/** Word-wraps text to a fixed number of monospace columns, like a typewriter's carriage width. */
internal fun wrapText(text: String, cols: Int): List<String> {
    val out = ArrayList<String>()
    for (para in text.split('\n')) {
        var line = StringBuilder()
        var i = 0
        while (i < para.length) {
            val nextSpace = para.indexOf(' ', i).let { if (it == -1) para.length else it }
            val word = para.substring(i, nextSpace)
            val withSpace = if (nextSpace < para.length) "$word " else word
            when {
                line.length + word.length <= cols -> line.append(withSpace)
                word.length > cols -> {
                    // Hard-break a long word.
                    var w = word
                    while (w.isNotEmpty()) {
                        val room = cols - line.length
                        if (room <= 0) { out += line.toString(); line = StringBuilder(); continue }
                        line.append(w.take(room)); w = w.drop(room)
                        if (w.isNotEmpty()) { out += line.toString(); line = StringBuilder() }
                    }
                    if (nextSpace < para.length) line.append(' ')
                }
                else -> {
                    out += line.toString().trimEnd()
                    line = StringBuilder(withSpace)
                }
            }
            i = nextSpace + 1
        }
        // Keep a typed trailing space visible as caret advance.
        out += if (line.length > cols) line.substring(0, cols) else line.toString()
    }
    return out
}

@Composable
private fun Paper(
    text: String,
    lampOn: Boolean,
    strike: Float,
    words: Int,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current
    val style = remember { TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = Ink) }

    BoxWithConstraints(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTap,
        ),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val marginPx = with(density) { 30.dp.toPx() }
        val lineH = with(density) { 23.dp.toPx() }
        val railH = with(density) { 10.dp.toPx() }
        val charW = remember(style) { measurer.measure("M", style).size.width.toFloat() }
        val cols = max(10, ((widthPx - 2 * marginPx) / charW).toInt())
        val lines = remember(text, cols) { wrapText(text, cols) }
        val maxVisible = (heightPx / lineH).toInt() + 2
        val visible = lines.takeLast(maxVisible)
        // Layouts for each visible line, reused across frames.
        val layouts: List<TextLayoutResult> = remember(visible, style) {
            visible.map { measurer.measure(it, style, softWrap = false, maxLines = 1) }
        }
        val textLeft = (widthPx - cols * charW) / 2f
        val caretCol = (lines.lastOrNull()?.length ?: 0).coerceAtMost(cols)
        val caretX = textLeft + caretCol * charW
        val carriageX by animateFloatAsState(caretX, spring(dampingRatio = 0.78f, stiffness = 520f), label = "carriage")

        // Paper feed: when a new line starts, everything rolls up one line.
        val feed = remember { Animatable(0f) }
        var prevLines by remember { mutableIntStateOf(lines.size) }
        LaunchedEffect(lines.size) {
            if (lines.size > prevLines) {
                feed.snapTo(lineH)
                feed.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            }
            prevLines = lines.size
        }

        val blink by rememberInfiniteTransition(label = "blink").animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(560, easing = { if (it < 0.5f) 0f else 1f }), RepeatMode.Reverse),
            label = "blinkAnim",
        )

        Canvas(Modifier.fillMaxSize()) {
            val lastTop = heightPx - railH - with(density) { 22.dp.toPx() } - lineH
            val glowCenter = Offset(carriageX + charW / 2f, lastTop + lineH * 0.55f)

            // Paper: a dark room when the lamp is on (only the typing spot is lit),
            // evenly lit when the lamp is off.
            drawRect(
                Brush.verticalGradient(
                    if (lampOn) listOf(Color(0xFF0C0B0A), Color(0xFF141210), Color(0xFF1D1A17))
                    else listOf(Color(0xFFC4C0B9), Color(0xFFD7D3CC), Color(0xFFDDD9D2)),
                ),
            )
            if (lampOn) {
                // Faint spill rising from the carriage, like a small desk lamp at the hinge.
                val spill = size.width * 0.95f
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF8C7F6B).copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(glowCenter.x, size.height),
                        radius = spill,
                    ),
                )
                // The pool of light where the letters land: wide and low, falling off fast.
                val pool = size.width * (0.5f + 0.03f * strike)
                scale(scaleX = 1f, scaleY = 0.7f, pivot = glowCenter) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color(0xFFFFF4DE).copy(alpha = 0.96f),
                            0.28f to Color(0xFFE9DCC3).copy(alpha = 0.62f),
                            0.6f to Color(0xFF85796A).copy(alpha = 0.2f),
                            1f to Color.Transparent,
                            center = glowCenter,
                            radius = pool,
                        ),
                        radius = pool,
                        center = glowCenter,
                    )
                }
                // Hot spot right on the letter being struck.
                val hot = with(density) { 40.dp.toPx() } * (1f + 0.15f * strike)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.95f), Color(0xFFFFF1D6).copy(alpha = 0.45f), Color.Transparent),
                        center = glowCenter, radius = hot,
                    ),
                    radius = hot, center = glowCenter,
                )
            }

            // Typed lines, newest at the bottom, older lines fading into the dark.
            val shake = strike * with(density) { 0.8.dp.toPx() }
            val fadeBand = heightPx * 0.55f
            for ((i, layout) in layouts.withIndex()) {
                val fromBottom = layouts.size - 1 - i
                val top = lastTop - fromBottom * lineH + feed.value
                if (top < -lineH) continue
                val a = if (lampOn) ((top + lineH) / fadeBand).coerceIn(0.08f, 1f) else ((top + lineH) / (lineH * 3)).coerceIn(0f, 1f)
                drawText(layout, topLeft = Offset(textLeft, top + if (fromBottom == 0) shake else 0f), alpha = a)
            }

            // Blinking underline caret and the type-guide "T" below it.
            val cx = carriageX + charW / 2f
            val stroke = with(density) { 1.6.dp.toPx() }
            drawRect(
                Ink.copy(alpha = 0.55f * blink),
                topLeft = Offset(carriageX, lastTop + lineH * 0.86f),
                size = Size(charW, stroke),
            )
            val guideTop = lastTop + lineH * 1.08f
            drawLine(Ink.copy(alpha = 0.8f), Offset(cx - 5 * density.density, guideTop), Offset(cx + 5 * density.density, guideTop), stroke)
            drawLine(Ink.copy(alpha = 0.8f), Offset(cx, guideTop), Offset(cx, guideTop + 9 * density.density), stroke)

            // Hinge rail with the sliding carriage.
            val railTop = heightPx - railH
            drawRect(Color(0xFF1E1C19), topLeft = Offset(0f, railTop), size = Size(size.width, railH))
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, railTop + 1f), Offset(size.width, railTop + 1f), 1f)
            val pillW = 30 * density.density
            val pillH = 8 * density.density
            drawRoundRect(
                Color(0xFF8B8781),
                topLeft = Offset(cx - pillW / 2, railTop + (railH - pillH) / 2),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2),
            )
            val dotR = 1.8f * density.density
            listOf(-5f, 0f, 5f).forEachIndexed { k, dx ->
                drawCircle(
                    if (k == 1) Color(0xFFFFB347) else Color(0xFFE8833A).copy(alpha = 0.85f),
                    radius = dotR, center = Offset(cx + dx * density.density, railTop + railH / 2),
                )
            }
        }

        // Top bar: back, word count, share.
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (lampOn) Color(0xFFBDB8AF) else Ink.copy(alpha = 0.65f)
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = tint) }
            Spacer(Modifier.weight(1f))
            Text(
                "%,d / %,d".format(words, WORD_GOAL),
                color = tint, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share", tint = tint, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun Deck(
    kbVisible: Boolean,
    lampOn: Boolean,
    soundOn: Boolean,
    autoCap: Boolean,
    onShowKeyboard: () -> Unit,
    onHideKeyboard: () -> Unit,
    onNewPage: () -> Unit,
    onPages: () -> Unit,
    onCopy: () -> Unit,
    onLamp: () -> Unit,
    onSound: () -> Unit,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier,
) {
    val shown by animateFloatAsState(if (kbVisible) 1f else 0f, tween(430, easing = FastOutSlowInEasing), label = "kb")
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(DeckTop, DeckBottom)))
            .navigationBarsPadding(),
    ) {
        // Lamp light spilling over the hinge onto the deck.
        if (lampOn) {
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                drawRect(Brush.verticalGradient(listOf(Color(0x33FFE9C2), Color.Transparent)))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .graphicsLayer {
                    alpha = shown
                    val b = (1f - shown) * 40f
                    renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                    translationY = (1f - shown) * 28.dp.toPx()
                    val s = 0.96f + 0.04f * shown
                    scaleX = s
                    scaleY = s
                },
        ) {
            Toolbar(lampOn, soundOn, onHideKeyboard, onNewPage, onPages, onCopy, onLamp, onSound)
            Spacer(Modifier.height(6.dp))
            if (shown > 0.01f) {
                TypeKeyboard(
                    autoCap = autoCap,
                    onText = onText,
                    onBackspace = onBackspace,
                    onReturn = onReturn,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (shown < 0.5f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onShowKeyboard,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("tap to type", color = Color.White.copy(alpha = 0.22f * (1f - shown * 2)), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Toolbar(
    lampOn: Boolean,
    soundOn: Boolean,
    onHide: () -> Unit,
    onNewPage: () -> Unit,
    onPages: () -> Unit,
    onCopy: () -> Unit,
    onLamp: () -> Unit,
    onSound: () -> Unit,
) {
    val on = Color(0xFFE6E1D8)
    val off = Color(0xFF8D8880)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF3A3834).copy(alpha = 0.9f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onHide) { Icon(Icons.Filled.KeyboardArrowDown, "Hide keyboard", tint = on) }
        IconButton(onClick = onNewPage) { Icon(Icons.Filled.Add, "New page", tint = on) }
        IconButton(onClick = onPages) { Icon(Icons.AutoMirrored.Filled.List, "Pages", tint = on) }
        IconButton(onClick = onCopy) { CopyGlyph(on) }
        IconButton(onClick = onLamp) { LampGlyph(if (lampOn) Color(0xFFFFC874) else off) }
        IconButton(onClick = onSound) { Icon(Icons.Filled.Notifications, "Typewriter sound", tint = if (soundOn) on else off) }
    }
}

@Composable
private fun LampGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        // Bulb
        drawCircle(color, radius = w * 0.28f, center = Offset(w / 2, h * 0.40f))
        drawRect(color, topLeft = Offset(w * 0.38f, h * 0.62f), size = Size(w * 0.24f, h * 0.18f))
        drawLine(color, Offset(w * 0.40f, h * 0.88f), Offset(w * 0.60f, h * 0.88f), strokeWidth = w * 0.08f)
    }
}

@Composable
private fun CopyGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.width
        val st = Stroke(width = s * 0.09f)
        drawRoundRect(color, topLeft = Offset(s * 0.30f, s * 0.08f), size = Size(s * 0.6f, s * 0.66f), cornerRadius = CornerRadius(s * 0.08f), style = st)
        drawRoundRect(color, topLeft = Offset(s * 0.10f, s * 0.28f), size = Size(s * 0.6f, s * 0.66f), cornerRadius = CornerRadius(s * 0.08f), style = st)
    }
}

@Composable
private fun PagesDialog(store: DocStore, currentId: String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var pages by remember { mutableStateOf(store.list()) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Pages") },
        text = {
            if (pages.isEmpty()) {
                Text("No saved pages yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(pages, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(p.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (p.id == currentId) "● " else "") + p.title,
                                    fontFamily = FontFamily.Monospace, maxLines = 1,
                                )
                                Text(
                                    "${p.words} words · ${fmt.format(Date(p.modified))}",
                                    fontSize = 12.sp, color = Color.Gray,
                                )
                            }
                            if (p.id != currentId) {
                                IconButton(onClick = {
                                    store.delete(p.id)
                                    pages = store.list()
                                }) { Icon(Icons.Filled.Delete, "Delete page") }
                            } else {
                                Spacer(Modifier.width(48.dp))
                            }
                        }
                    }
                }
            }
        },
    )
}
