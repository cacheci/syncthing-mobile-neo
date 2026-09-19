package moe.https.syncthing.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import moe.https.syncthing.generated.resources.Res
import moe.https.syncthing.generated.resources.logo_only
import moe.https.syncthing.ui.theme.AppTheme
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
@OptIn(ExperimentalResourceApi::class)
internal fun AboutScreen(
    versionName: String,
    pagePaddingHorizontal: Dp,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val libraries by produceLibraries {
        Res.readBytes(ABOUT_LIBRARIES_RESOURCE).decodeToString()
    }
    val projectLibrary = libraries?.libraries?.firstOrNull { it.uniqueId == PROJECT_LIBRARY_ID }
    var showLicenceOverlay by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = pagePaddingHorizontal),
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
                    color = AppTheme.colorScheme.onBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "v$versionName",
                    color = AppTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        item {
            AboutCard (
                title = "查看源代码",
                endText = "",
                licence = projectLibrary?.licenseSummary().orEmpty(),
                onClick = { uriHandler.openUri("https://github.com/cacheci/syncthing-mobile-neo") },
                onShowLicence = {
                    if (projectLibrary != null) {
                        showLicenceOverlay = true
                    }
                },
            )
        }
    }

    OverlayDialog(
        show = showLicenceOverlay,
        onDismissRequest = { showLicenceOverlay = false },
    ) {
        Column {
            LicenceShowContent(
                title = listOfNotNull(projectLibrary?.name, projectLibrary?.artifactVersion),
                content = projectLibrary?.licenseContent().orEmpty(),
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = "确定",
                onClick = { showLicenceOverlay = false }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalResourceApi::class)
internal fun LicenceScreen(
    pagePaddingHorizontal: Dp,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val libraries by produceLibraries {
        Res.readBytes(ABOUT_LIBRARIES_RESOURCE).decodeToString()
    }
    var showLicenceOverlay by rememberSaveable { mutableStateOf(false) }
    var selectedLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    val displayedLibraries = libraries?.libraries
        .orEmpty()
        .filterNot { it.uniqueId == PROJECT_LIBRARY_ID }
        .sortedWith(
            compareBy<Library> { it.uniqueId != SYNCTHING_LIBRARY_ID }
                .thenBy { it.name.lowercase() }
                .thenBy { it.uniqueId.length },
        )
        .distinctBy(Library::displayKey)
    val selectedLibrary = displayedLibraries.firstOrNull { it.uniqueId == selectedLibraryId }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = pagePaddingHorizontal),
    ) {
        items(
            items = displayedLibraries,
            key = Library::artifactId,
        ) { library ->
            val displayId = library.uniqueId.split(":")[1]
            val displaySource = library.uniqueId.split(":")[0] + ": " + library.artifactVersion
            AboutCard(
                title = displayId,
                summary = displaySource,
                licence = library.licenseSummary(),
                onClick = {
                    library.website?.takeIf(String::isNotBlank)?.let(uriHandler::openUri)
                },
                onShowLicence = {
                    selectedLibraryId = library.uniqueId
                    showLicenceOverlay = true
                },
            )
        }
    }

    OverlayDialog(
        show = showLicenceOverlay,
        onDismissRequest = { showLicenceOverlay = false },
    ) {
        Column {
            LicenceShowContent(
                title = listOfNotNull(selectedLibrary?.name),
                content = selectedLibrary?.licenseContent().orEmpty(),
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = "确定",
                onClick = { showLicenceOverlay = false },
            )
        }
    }
}

@Composable
private fun AboutCard(
    title: String,
    summary: String? = null,
    licence: String? = null,
    endText: String = "源代码",
    onClick: () -> Unit,
    onShowLicence: () -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    Card ( modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth() ) {
        Column (
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArrowPreference(
                title = title,
                summary = summary,
                endActions = {
                    Text(
                        text = endText,
                        color = AppTheme.colorScheme.onSurfaceVariantActions,
                        fontSize = AppTheme.textStyles.body2.fontSize,
                    )
                },
                startAction = {},
                onClick = onClick,
            )
            HorizontalDivider( modifier = Modifier.padding(horizontal = 12.dp) )
            ArrowPreference(
                title = licence ?: "",
                onClick = onShowLicence,
                titleColor = BasicComponentColors(AppTheme.colorScheme.onSecondaryContainerVariant, AppTheme.colorScheme.onSecondaryContainerVariant),
            )
            extraContent()
        }
    }
}

@Composable
private fun LicenceShowContent(
    title: List<String>,
    content: List<String>,
) {
    Column( modifier = Modifier.fillMaxHeight(0.5f) ) {
        title.forEach { text ->
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = AppTheme.textStyles.title3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        LazyColumn ( modifier = Modifier.padding(vertical = 8.dp)) {
            items(content) { text ->
                Text(
                    text = text,
                    textAlign = TextAlign.Start,
                    style = AppTheme.textStyles.main,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun Library.licenseSummary(): String =
    licenses
        .sortedBy { it.name }
        .joinToString(" / ") { it.spdxId ?: it.name }

private fun Library.licenseContent(): List<String> =
    licenses
        .sortedBy { it.name }
        .flatMap { license ->
            buildList {
                add(license.name)
                license.licenseContent
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
                    ?: license.url
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
            }
        }

private fun Library.displayKey(): LibraryDisplayKey = LibraryDisplayKey(
    name = name.lowercase(),
    website = website.orEmpty(),
    licenseHashes = licenses.mapTo(mutableSetOf()) { it.hash },
)

private data class LibraryDisplayKey(
    val name: String,
    val website: String,
    val licenseHashes: Set<String>,
)

private const val ABOUT_LIBRARIES_RESOURCE = "files/aboutlibraries.json"
private const val PROJECT_LIBRARY_ID = "app:syncthing-gui"
private const val SYNCTHING_LIBRARY_ID = "native:syncthing"
