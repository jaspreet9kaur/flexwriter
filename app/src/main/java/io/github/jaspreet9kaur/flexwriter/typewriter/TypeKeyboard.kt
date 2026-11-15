package io.github.jaspreet9kaur.flexwriter.typewriter

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class KType { Char, Shift, Back, Return, Space, Layer, Gap }
private enum class Layer { Letters, Numbers, Symbols }

private data class KeyDef(
    val label: String,
    val type: KType = KType.Char,
    val w: Float = 1f,
    val target: Layer = Layer.Letters,
)

private fun chars(s: String) = s.map { KeyDef(it.toString()) }

private val lettersRows = listOf(
    chars("qwertyuiop"),
    listOf(KeyDef("", KType.Gap, 0.5f)) + chars("asdfghjkl") + KeyDef("", KType.Gap, 0.5f),
    listOf(KeyDef("shift", KType.Shift, 1.45f)) + chars("zxcvbnm") + KeyDef("back", KType.Back, 1.45f),
    listOf(
        KeyDef("123", KType.Layer, 1.45f, Layer.Numbers), KeyDef(","), KeyDef("space", KType.Space, 5f),
        KeyDef("."), KeyDef("return", KType.Return, 1.75f),
    ),
)

private val numberRows = listOf(
    chars("1234567890"),
    chars("-/:;()&@\""),
    listOf(KeyDef("#+=", KType.Layer, 1.45f, Layer.Symbols)) + chars(".,?!'") + KeyDef("back", KType.Back, 1.45f),
    listOf(
        KeyDef("ABC", KType.Layer, 1.45f, Layer.Letters), KeyDef(","), KeyDef("space", KType.Space, 5f),
        KeyDef("."), KeyDef("return", KType.Return, 1.75f),
    ),
)

private val symbolRows = listOf(
    chars("[]{}#%^*+="),
    chars("_\\|~<>€£¥"),
    listOf(KeyDef("123", KType.Layer, 1.45f, Layer.Numbers)) + chars(".,?!'") + KeyDef("back", KType.Back, 1.45f),
    numberRows[3],
)

/**
 * A typewriter-style keyboard drawn inside the app, so it fills the lower half of the
 * folded phone exactly like a laptop deck. Keys fire on press, like a real type bar.
 */
@Composable
fun TypeKeyboard(
    autoCap: Boolean,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var layer by remember { mutableStateOf(Layer.Letters) }
    var shift by remember { mutableIntStateOf(0) } // 0 off, 1 once, 2 caps lock
    var lastShiftTap by remember { mutableLongStateOf(0L) }

    LaunchedEffect(autoCap) {
        if (autoCap && shift == 0 && layer == Layer.Letters) shift = 1
        if (!autoCap && shift == 1) shift = 0
    }

    val rows = when (layer) {
        Layer.Letters -> lettersRows
        Layer.Numbers -> numberRows
        Layer.Symbols -> symbolRows
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { def ->
                    val km = Modifier.weight(def.w).fillMaxHeight()
                    if (def.type == KType.Gap) {
                        Spacer(km)
                    } else {
                        KeyCap(
                            def = def,
                            upper = shift > 0,
                            shiftState = shift,
                            modifier = km,
                            repeat = def.type == KType.Back,
                            onPress = {
                                when (def.type) {
                                    KType.Char -> {
                                        onText(if (shift > 0) def.label.uppercase() else def.label)
                                        if (shift == 1) shift = 0
                                    }
                                    KType.Space -> onText(" ")
                                    KType.Back -> onBackspace()
                                    KType.Return -> onReturn()
                                    KType.Layer -> {
                                        layer = def.target
                                        shift = 0
                                    }
                                    KType.Shift -> {
                                        val now = System.currentTimeMillis()
                                        shift = when {
                                            shift == 2 -> 0
                                            now - lastShiftTap < 350 -> 2
                                            shift == 1 -> 0
                                            else -> 1
                                        }
                                        lastShiftTap = now
                                    }
                                    KType.Gap -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCap(
    def: KeyDef,
    upper: Boolean,
    shiftState: Int,
    modifier: Modifier,
    repeat: Boolean,
    onPress: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.55f, stiffness = 1600f), label = "key")
    val special = def.type != KType.Char && def.type != KType.Space
    val bg = when {
        pressed -> Color(0xFFEDEAE4)
        def.type == KType.Shift && shiftState > 0 -> Color(0xFFE2DED6)
        special -> Color(0xFF7C7974)
        else -> Color(0xFFB7B4AE)
    }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (pressed) 1.5f * density else 0f
            }
            .shadow(if (pressed) 0.dp else 2.dp, shape)
            .clip(shape)
            .background(bg)
            .pointerInput(def) {
                detectTapGestures(onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    press()
                    var job: Job? = null
                    if (repeat) {
                        job = scope.launch {
                            delay(420)
                            while (true) {
                                press()
                                delay(55)
                            }
                        }
                    }
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        val ink = Color(0xFF1F1D1A)
        val label = when (def.type) {
            KType.Shift -> if (shiftState == 2) "⇪" else "⇧"
            KType.Back -> "⌫"
            KType.Return -> "⏎"
            KType.Space -> ""
            else -> if (upper && def.type == KType.Char) def.label.uppercase() else def.label
        }
        val size = when {
            def.type == KType.Layer -> 14.sp
            def.type == KType.Shift || def.type == KType.Back || def.type == KType.Return -> 20.sp
            else -> 20.sp
        }
        Text(
            label,
            color = if (special && !pressed && !(def.type == KType.Shift && shiftState > 0)) Color(0xFFF1EEE8) else ink,
            fontSize = size,
            fontWeight = if (def.type == KType.Layer) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
