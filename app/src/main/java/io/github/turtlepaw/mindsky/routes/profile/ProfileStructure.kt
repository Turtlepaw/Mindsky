package io.github.turtlepaw.mindsky.routes.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.routes.post.ImageBackButton

@Composable
fun ProfileStructure(
    banner: @Composable (modifier: Modifier) -> Unit,
    avatar: @Composable (modifier: Modifier) -> Unit,
    navigator: DestinationsNavigator,
    avatarOnClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp) // height of the banner
        ) {
            banner(
                Modifier.matchParentSize()
            )

            // Avatar placed at bottom start inside the banner
            avatar(
                Modifier
                    .size(100.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = 40.dp)
                    .border(
                        5.dp,
                        MaterialTheme.colorScheme.surface,
                        CircleShape
                    )
                    .then(
                        if (avatarOnClick != null)
                            Modifier.clickable(
                                onClick = avatarOnClick
                            )
                        else Modifier
                    )
            )

            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    surface = Color.Black,
                    onSurface = Color.White
                )
            ) {
                ImageBackButton(
                    navigator,
                    modifier = Modifier
                        .padding(WindowInsets.systemBars.asPaddingValues())
                        .padding(start = 15.dp),
                    30.dp,
                    0.6f
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp)) // This makes room for the offset avatar
    }
}