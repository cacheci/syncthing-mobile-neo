package moe.https.syncthing.ui.model

data class MainUiState(
    val bottomBarPages: Set<AppPage> = AppPage.entries.toSet(),
    val defaultBottomBarPage: AppPage = AppPage.CORE,
) {
    val canSelectMoreBottomBarPages: Boolean
        get() = bottomBarPages.size < MAX_BOTTOM_BAR_PAGES

    companion object {
        const val MAX_BOTTOM_BAR_PAGES = 5
    }
}

enum class AppPage(val title: String) {
    DEVICES("连接"),
    FOLDERS("文件夹"),
    CORE("Syncthing"),
    WEBUI("WebUI"),
    SETTINGS("设置"),
}
