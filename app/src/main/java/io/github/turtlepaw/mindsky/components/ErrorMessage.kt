package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.turtlepaw.mindsky.R

@Composable
fun ErrorMessage(content: Int = R.string.failed_to_load) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = "Warning"
        )
        Text(
            stringResource(content)
        )
    }
}