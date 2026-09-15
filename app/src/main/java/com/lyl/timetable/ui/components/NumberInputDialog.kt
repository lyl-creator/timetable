package com.lyl.timetable.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType

/**
 * 数值输入对话框：用于点击步进器中的数字后直接键入目标值。
 */
@Composable
fun NumberInputDialog(
    title: String,
    initial: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    suffix: String = "",
    hint: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val p = AppTheme.colors
    var text by remember { mutableStateOf(initial.toString()) }
    val focusRequester = remember { FocusRequester() }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in range

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                GlassTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() }.take(4)
                    },
                    placeholder = "${range.first} – ${range.last}",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    focusRequester = focusRequester
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (valid) {
                        hint ?: "有效范围 ${range.first} – ${range.last}$suffix"
                    } else {
                        "请输入 ${range.first} – ${range.last} 之间的整数"
                    },
                    style = AppType.Footnote,
                    color = if (valid) p.textSecondary else p.danger
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { parsed?.let(onConfirm) }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 时间文本输入对话框（HH:mm），供需要直接输入时间点的场景使用 */
@Composable
fun TimeInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val p = AppTheme.colors
    var text by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val valid = com.lyl.timetable.data.TimeText.isValid(text)

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                GlassTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
                    placeholder = "HH:mm",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    focusRequester = focusRequester
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (valid) "例如 08:00" else "格式应为 HH:mm，例如 08:00",
                    style = AppType.Footnote,
                    color = if (valid) p.textSecondary else p.danger
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(text) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 步进器对话框：作息相关的数值统一用「− / 数字 / ＋」调整，
 * 数字本身也可点击后直接键入。
 */
@Composable
fun StepperDialog(
    title: String,
    initial: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    suffix: String = "",
    step: Int = 1,
    hint: String? = null
) {
    var value by remember { mutableStateOf(initial.coerceIn(range)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Stepper(
                    value = value,
                    onValueChange = { value = it },
                    range = range,
                    suffix = suffix,
                    step = step,
                    label = title
                )
                if (!hint.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = hint,
                        style = AppType.Footnote,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
