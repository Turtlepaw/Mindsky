package io.github.turtlepaw.mindsky.routes.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.CustomPdsDestination
import com.ramcosta.composedestinations.generated.destinations.LoginDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.routes.TopAppBarCommon

data class Pds(
    val icon: String?,
    val title: String,
    val description: String,
    val url: String,
    /**
     * if the account is verified by @bsky.app
     */
    val verified: Boolean = false,
)

val publicPdsList = listOf<Pds>(
    Pds(
        "https://cdn.bsky.app/img/avatar/plain/did:plc:z72i7hdynmk6r22z27h6tvur/bafkreihagr2cmvl2jt4mgx3sppwe2it3fwolkrbtjrhcnwjk4jdijhsoze@jpeg",
        "Bluesky PBC",
        "The official Bluesky PDS provided by Bluesky PBC.",
        "https://bsky.social",
        // @bsky.app is a trusted verifier
        verified = true
    ),
    Pds(
        "https://cdn.bsky.app/img/avatar/plain/did:web:witchcraft.systems/bafkreihxuizk4vku4wkmexc5hcemp2huwt5qpdx7p2r3ejnygg4plglkwu@jpeg",
        "witchcraft.systems",
        "witches with ethernet switches",
        "https://pds.witchcraft.systems"
    ),
    Pds(
        null,
        "Your own PDS",
        "You can also run your own PDS. If you have one, enter the URL below.",
        "",
    )
)

@Destination<RootGraph>
@Composable
fun PdsList(navigator: DestinationsNavigator) {
    val viewModel = rememberOnboardingViewModel()
    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
                R.string.sign_in_with_provider,
                MaterialTheme.typography.titleMedium
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            itemsIndexed(publicPdsList) { index, pds ->
                PdsItem(
                    pds = pds,
                    index = index,
                    listSize = publicPdsList.size
                ) {
                    viewModel.pds = pds.url
                    navigator.navigate(
                        if (pds.url.isNullOrBlank())
                            CustomPdsDestination
                        else LoginDestination
                    )
                }
            }
        }
    }
}

@Composable
fun PdsItem(pds: Pds, index: Int, listSize: Int, onClick: () -> Unit) {
    val topRadius = if (listSize == 1 || index == 0) 20.dp else 10.dp
    val bottomRadius = if (listSize == 1 || index == listSize - 1) 20.dp else 10.dp

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = topRadius,
            topEnd = topRadius,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f), // Constrain this part to available space
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pds.icon != null) {
                    Avatar(
                        modifier = Modifier.size(50.dp),
                        pds.icon,
                        "${pds.title} icon",
                        clip = MaterialTheme.shapes.large
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth() // Use remaining width inside weighted container
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(
                            4.dp,
                            alignment = Alignment.Start
                        ),
                    ) {
                        Text(
                            pds.title,
                            style = MaterialTheme.typography.titleMedium,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (pds.verified) {
                            Icon(
                                painterResource(R.drawable.check_circle),
                                contentDescription = "Verified",
                                modifier = Modifier
                                    .size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        pds.description,
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Chevron Right",
            )
        }
    }
}

@Composable
fun TextStyle.toDp(): Dp {
    val density = LocalDensity.current
    return with(density) { fontSize.toDp() }
}

