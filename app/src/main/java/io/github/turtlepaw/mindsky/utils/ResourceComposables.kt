package io.github.turtlepaw.mindsky.utils

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle

@Composable
fun Int.StringComposable(style: TextStyle? = null) {
    if (style != null) {
        Text(text = stringResource(this), style = style)
    } else {
        Text(text = stringResource(this))
    }
}