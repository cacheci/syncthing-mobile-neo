package moe.https.syncthing.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.https.syncthing.generated.resources.Res
import moe.https.syncthing.generated.resources.logo_only
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutScreen(
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 40.dp,
            end = 16.dp,
            bottom = 20.dp,
        ),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                    Image(
                        painter = painterResource(Res.drawable.logo_only),
                        contentDescription = null,
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)),
                    )

                Text(
                    text = "Syncthing GUI",
                    modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "v$versionName",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        item {
            AboutCard (
                title = "查看源代码",
                endText = "example.com",
                onClick = { uriHandler.openUri("https://example.com") },
                extraContent = {
                    ArrowPreference (
                        title = "许可证",
                        endActions = {
                            Text(
                                text = "MIT LICENCE",
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                            )
                        },
                        onClick = { uriHandler.openUri("https://example.com") },
                    )
                }
            )
        }
    }
}

@Composable
internal fun LicenceScreen(
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 40.dp,
            end = 16.dp,
            bottom = 20.dp,
        ),
    ) {
        item {
            AboutCard(
                title = "Syncthing",
                summary = "MPL-2.0",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/syncthing/syncthing") },
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
        }

        item {
            AboutCard(
                title = "Kotlin",
                summary = "Apache-2.0",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/JetBrains/kotlin") },
            )
        }

        item {
            AboutCard(
                title = "kotlinx.coroutines",
                summary = "Apache-2.0",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/Kotlin/kotlinx.coroutines") },
            )
        }

        item {
            AboutCard(
                title = "Compose Multiplatform",
                summary = "Apache-2.0",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/JetBrains/compose-multiplatform") },
            )
        }

        item {
            AboutCard(
                title = "AndroidX",
                summary = "Apache-2.0 · Activity / Lifecycle",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/androidx/androidx") },
            )
        }

        item {
            AboutCard(
                title = "Navigation Compose",
                summary = "Apache-2.0",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/JetBrains/compose-multiplatform") },
            )
        }

        item {
            AboutCard(
                title = "Material Components for Android",
                summary = "Apache-2.0",
                endText = "GitHub",
                onClick = {
                    uriHandler.openUri(
                        "https://github.com/material-components/material-components-android",
                    )
                },
            )
        }

        item {
            AboutCard(
                title = "Miuix",
                summary = "Apache-2.0",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") },
            )
        }

        item {
            AboutCard(
                title = "bcrypt",
                summary = "Apache-2.0",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/patrickfav/bcrypt") },
            )
        }

        item {
            AboutCard(
                title = "Bytes Java",
                summary = "Apache-2.0 · bcrypt 运行时依赖",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/patrickfav/bytes-java") },
            )
        }

        item {
            AboutCard(
                title = "QRose",
                summary = "MIT",
                endText = "GitHub",
                onClick = { uriHandler.openUri("https://github.com/alexzhirkevich/qrose") },
            )
        }
    }
}

@Composable
private fun AboutCard(
    title: String,
    summary: String? = null,
    endText: String = "",
    onClick: () -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    Card ( modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth() ) {
        Column {
            ArrowPreference(
                title = title,
                summary = summary,
                endActions = {
                    Text(
                        text = endText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                    )
                },
                onClick = onClick,
            )
            extraContent()
        }
    }
}
