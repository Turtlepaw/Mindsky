package io.github.turtlepaw.mindsky.routes.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import io.github.turtlepaw.mindsky.R

data class Facet(
    val index: Int,
    val features: List<String>
)

data class PostContent(
    val text: String,
    val facets: List<Facet>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun Composer(navigator: DestinationsNavigator){
    var content by remember { mutableStateOf<PostContent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.composer_title))
                },
                actions = {
                    Button(
                        onClick = { /* TODO: Implement post submission logic */ }
                    ) {
                        Text(stringResource(R.string.post))
                    }
                }
            )
        },
        bottomBar = {
            HorizontalFloatingToolbar(
                expanded = true
            ) {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(

                        ),
                    tooltip = { PlainTooltip { Text("Localized description") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(Icons.Filled.Person, contentDescription = "Localized description")
                    }
                }
            }
        }
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding
        ) {
            item {
                BasicTextField(
                    value = content?.text ?: "",
                    onValueChange = { newText ->
                        content = PostContent(
                            text = newText,
                            facets = content?.facets ?: emptyList()
                        )
                    },
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                        ) {
                            if (content?.text.isNullOrEmpty()) {
                                Text(stringResource(R.string.composer_hint), color = Color.DarkGray)
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun ComposerPreview() {
    Composer(navigator = EmptyDestinationsNavigator)
}