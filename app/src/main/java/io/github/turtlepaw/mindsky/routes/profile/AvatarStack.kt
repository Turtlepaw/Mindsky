package io.github.turtlepaw.mindsky.routes.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex

@Composable
fun <T> AvatarStack(
    items: List<T>,
    avatarSize: Dp = 40.dp,
    overlap: Dp = 18.dp,
    modifier: Modifier = Modifier,
    renderAvatar: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.height(avatarSize)) {
        items.reversed().forEachIndexed { index, item ->
            Box(
                modifier = Modifier
                    .offset(x = (index * overlap))
                    .size(avatarSize)
                    .zIndex(index.toFloat())
            ) {
                renderAvatar(item)
            }
        }
    }
}
