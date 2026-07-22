package com.anvilvm.app.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class KeyDef(
    val label: String,
    val keySym: Int,
    val width: Float = 1f,
    val isModifier: Boolean = false,
    val altLabel: String? = null
)

@Composable
fun TouchKeyboard(
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var shiftActive by remember { mutableStateOf(false) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    val rows = remember { buildKeyboardLayout() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1B1D))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Special keys row (Esc, Tab, Ctrl, Alt, arrows)
        SpecialKeysRow(
            ctrlActive = ctrlActive,
            altActive = altActive,
            onCtrlToggle = {
                ctrlActive = !ctrlActive
                if (ctrlActive) onKeyDown(KeySym.CONTROL_L) else onKeyUp(KeySym.CONTROL_L)
            },
            onAltToggle = {
                altActive = !altActive
                if (altActive) onKeyDown(KeySym.ALT_L) else onKeyUp(KeySym.ALT_L)
            },
            onKey = { keySym ->
                onKeyDown(keySym)
                onKeyUp(keySym)
            }
        )

        // Main keyboard rows
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (key in row) {
                    KeyButton(
                        key = key,
                        shiftActive = shiftActive,
                        isActive = when (key.keySym) {
                            KeySym.SHIFT_L -> shiftActive
                            KeySym.CONTROL_L -> ctrlActive
                            KeySym.ALT_L -> altActive
                            else -> false
                        },
                        modifier = Modifier.weight(key.width),
                        onClick = {
                            if (key.isModifier && key.keySym == KeySym.SHIFT_L) {
                                shiftActive = !shiftActive
                                if (shiftActive) onKeyDown(KeySym.SHIFT_L) else onKeyUp(KeySym.SHIFT_L)
                            } else {
                                val sym = if (shiftActive && key.altLabel != null) {
                                    KeySym.fromChar(key.altLabel[0])
                                } else {
                                    key.keySym
                                }
                                onKeyDown(sym)
                                onKeyUp(sym)

                                // Auto-release shift after non-modifier key
                                if (shiftActive && !key.isModifier) {
                                    shiftActive = false
                                    onKeyUp(KeySym.SHIFT_L)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecialKeysRow(
    ctrlActive: Boolean,
    altActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onKey: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SmallKey("Esc", Modifier.weight(1f)) { onKey(KeySym.ESCAPE) }
        SmallKey("Tab", Modifier.weight(1f)) { onKey(KeySym.TAB) }
        SmallKey("Ctrl", Modifier.weight(1f), active = ctrlActive) { onCtrlToggle() }
        SmallKey("Alt", Modifier.weight(1f), active = altActive) { onAltToggle() }
        SmallKey("|", Modifier.weight(0.7f)) { onKey(KeySym.fromChar('|')) }
        SmallKey("/", Modifier.weight(0.7f)) { onKey(KeySym.fromChar('/')) }
        SmallKey("-", Modifier.weight(0.7f)) { onKey(KeySym.fromChar('-')) }
        SmallKey("←", Modifier.weight(1f)) { onKey(KeySym.LEFT) }
        SmallKey("↑", Modifier.weight(1f)) { onKey(KeySym.UP) }
        SmallKey("↓", Modifier.weight(1f)) { onKey(KeySym.DOWN) }
        SmallKey("→", Modifier.weight(1f)) { onKey(KeySym.RIGHT) }
    }
}

@Composable
private fun SmallKey(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) Color(0xFF00E5FF) else Color(0xFF2A2B2D))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) Color.Black else Color(0xFFCCCCCC),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KeyButton(
    key: KeyDef,
    shiftActive: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val displayLabel = if (shiftActive && key.altLabel != null) key.altLabel else key.label

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    isActive -> Color(0xFF00E5FF)
                    key.isModifier -> Color(0xFF333435)
                    else -> Color(0xFF2A2B2D)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayLabel,
            fontSize = if (key.label.length > 2) 10.sp else 14.sp,
            fontWeight = if (key.isModifier) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) Color.Black else Color(0xFFEEEEEE),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

private fun buildKeyboardLayout(): List<List<KeyDef>> {
    return listOf(
        // Number row
        listOf(
            KeyDef("1", KeySym.fromChar('1'), altLabel = "!"),
            KeyDef("2", KeySym.fromChar('2'), altLabel = "@"),
            KeyDef("3", KeySym.fromChar('3'), altLabel = "#"),
            KeyDef("4", KeySym.fromChar('4'), altLabel = "$"),
            KeyDef("5", KeySym.fromChar('5'), altLabel = "%"),
            KeyDef("6", KeySym.fromChar('6'), altLabel = "^"),
            KeyDef("7", KeySym.fromChar('7'), altLabel = "&"),
            KeyDef("8", KeySym.fromChar('8'), altLabel = "*"),
            KeyDef("9", KeySym.fromChar('9'), altLabel = "("),
            KeyDef("0", KeySym.fromChar('0'), altLabel = ")"),
        ),
        // QWERTY row
        listOf(
            KeyDef("q", KeySym.fromChar('q'), altLabel = "Q"),
            KeyDef("w", KeySym.fromChar('w'), altLabel = "W"),
            KeyDef("e", KeySym.fromChar('e'), altLabel = "E"),
            KeyDef("r", KeySym.fromChar('r'), altLabel = "R"),
            KeyDef("t", KeySym.fromChar('t'), altLabel = "T"),
            KeyDef("y", KeySym.fromChar('y'), altLabel = "Y"),
            KeyDef("u", KeySym.fromChar('u'), altLabel = "U"),
            KeyDef("i", KeySym.fromChar('i'), altLabel = "I"),
            KeyDef("o", KeySym.fromChar('o'), altLabel = "O"),
            KeyDef("p", KeySym.fromChar('p'), altLabel = "P"),
        ),
        // ASDF row
        listOf(
            KeyDef("a", KeySym.fromChar('a'), altLabel = "A"),
            KeyDef("s", KeySym.fromChar('s'), altLabel = "S"),
            KeyDef("d", KeySym.fromChar('d'), altLabel = "D"),
            KeyDef("f", KeySym.fromChar('f'), altLabel = "F"),
            KeyDef("g", KeySym.fromChar('g'), altLabel = "G"),
            KeyDef("h", KeySym.fromChar('h'), altLabel = "H"),
            KeyDef("j", KeySym.fromChar('j'), altLabel = "J"),
            KeyDef("k", KeySym.fromChar('k'), altLabel = "K"),
            KeyDef("l", KeySym.fromChar('l'), altLabel = "L"),
        ),
        // ZXCV row
        listOf(
            KeyDef("⇧", KeySym.SHIFT_L, width = 1.5f, isModifier = true),
            KeyDef("z", KeySym.fromChar('z'), altLabel = "Z"),
            KeyDef("x", KeySym.fromChar('x'), altLabel = "X"),
            KeyDef("c", KeySym.fromChar('c'), altLabel = "C"),
            KeyDef("v", KeySym.fromChar('v'), altLabel = "V"),
            KeyDef("b", KeySym.fromChar('b'), altLabel = "B"),
            KeyDef("n", KeySym.fromChar('n'), altLabel = "N"),
            KeyDef("m", KeySym.fromChar('m'), altLabel = "M"),
            KeyDef("⌫", KeySym.BACKSPACE, width = 1.5f),
        ),
        // Space row
        listOf(
            KeyDef(".", KeySym.fromChar('.'), altLabel = ">"),
            KeyDef(",", KeySym.fromChar(','), altLabel = "<"),
            KeyDef("Space", KeySym.SPACE, width = 4f),
            KeyDef("_", KeySym.fromChar('_'), altLabel = "-"),
            KeyDef("↵", KeySym.RETURN, width = 2f),
        )
    )
}
