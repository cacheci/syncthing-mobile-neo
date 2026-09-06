package moe.https.syncthing.ui.model

data class MainUiState(
    val bottomBarPages: Set<AppPage> = DEFAULT_BOTTOM_BAR_PAGES,
    val defaultBottomBarPage: AppPage = AppPage.CORE,
) {
    val canSelectMoreBottomBarPages: Boolean
        get() = bottomBarPages.size < MAX_BOTTOM_BAR_PAGES

    companion object {
        const val MAX_BOTTOM_BAR_PAGES = 5

        val DEFAULT_BOTTOM_BAR_PAGES: Set<AppPage> = setOf(
            AppPage.DEVICES,
            AppPage.FOLDERS,
            AppPage.CORE,
            AppPage.RECENT_CHANGES,
            AppPage.SETTINGS,
        )
    }
}

enum class AppPage(val title: String) {
    DEVICES("连接"),
    FOLDERS("文件夹"),
    CORE("Syncthing"),
    WEBUI("WebUI"),
    RECENT_CHANGES("最近变化"),
    SETTINGS("设置"),
}
