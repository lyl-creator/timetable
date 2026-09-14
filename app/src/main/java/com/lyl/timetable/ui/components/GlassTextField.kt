package com.lyl.timetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType

/**
 * iOS 风格的填充式输入框：无边框、圆角、灰底。
 * 可通过 [focusRequester] 在出现时自动聚焦并唤起键盘。
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    focusRequester: FocusRequester? = null
) {
    val p = AppTheme.colors
    val shape = RoundedCornerShape(AppDimens.InnerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.fill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty() && placeholder != null) {
            Text(text = placeholder, style = AppType.Body, color = p.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            textStyle = AppType.Body.copy(color = p.textPrimary),
            cursorBrush = SolidColor(p.accent),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            )
        )
    }
}
