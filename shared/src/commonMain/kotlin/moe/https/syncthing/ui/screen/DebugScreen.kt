package moe.https.syncthing.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.https.syncthing.AppPage
import moe.https.syncthing.AppSubPage
import moe.https.syncthing.ui.component.InfoSwitchCard
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun DevSettingPage(
    requestSwitchToPageMain: ( targetPage: AppPage ) -> Unit,
    requestSwitchToPagePlain: ( targetPage: AppSubPage ) -> Unit,

) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 20.dp, horizontal = 20.dp),
    ) {
        InfoSwitchCard(
            title = "前往页面..."
        ) {
            LazyColumn {
                items(AppPage.entries) { item ->
                    ArrowPreference(
                        title = item.title,
                        onClick = { requestSwitchToPageMain(item) },
                    )
                }
                items(AppSubPage.entries) { item ->
                    ArrowPreference(
                        title = item.title,
                        onClick = { requestSwitchToPagePlain(item) },
                    )
                }
            }
        }
    }
}