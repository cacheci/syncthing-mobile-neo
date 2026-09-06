package moe.https.syncthing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.ui.model.AppPage
import moe.https.syncthing.ui.model.MainUiState

class MainViewModel(
    private val appSettingsStorage: AppSettingPrivateStorage,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(loadUiState())
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    fun onBottomBarPageToggled(page: AppPage) {
        if (page == AppPage.SETTINGS) return

        val currentPages = mutableUiState.value.bottomBarPages
        val updatedPages = if (page in currentPages) {
            currentPages - page
        } else {
            if (currentPages.size >= MainUiState.MAX_BOTTOM_BAR_PAGES) return
            currentPages + page
        }
        updateBottomBarPages(updatedPages)
    }

    fun onDefaultBottomBarPageSelected(page: AppPage) {
        val currentState = mutableUiState.value
        if (page !in currentState.bottomBarPages) return

        appSettingsStorage.putString(
            AppSettingPrivateStorage.KEY_BOTTOM_BAR_DEFAULT_PAGE,
            page.name,
        )
        mutableUiState.value = currentState.copy(defaultBottomBarPage = page)
    }

    private fun updateBottomBarPages(pages: Set<AppPage>) {
        val normalizedPages = normalizeBottomBarPages(pages)
        val normalizedDefaultPage = resolveDefaultBottomBarPage(
            page = mutableUiState.value.defaultBottomBarPage,
            availablePages = normalizedPages,
        )
        appSettingsStorage.putStringSet(
            AppSettingPrivateStorage.KEY_BOTTOM_BAR_PAGES,
            normalizedPages.mapTo(mutableSetOf(), AppPage::name),
        )
        appSettingsStorage.putString(
            AppSettingPrivateStorage.KEY_BOTTOM_BAR_DEFAULT_PAGE,
            normalizedDefaultPage.name,
        )
        mutableUiState.value = MainUiState(
            bottomBarPages = normalizedPages,
            defaultBottomBarPage = normalizedDefaultPage,
        )
    }

    private fun loadUiState(): MainUiState {
        val storedPageNames = appSettingsStorage.getStringSet(
            AppSettingPrivateStorage.KEY_BOTTOM_BAR_PAGES,
        )
        val bottomBarPages = normalizeBottomBarPages(
            if (storedPageNames == null) {
                MainUiState.DEFAULT_BOTTOM_BAR_PAGES
            } else {
                AppPage.entries.filterTo(mutableSetOf()) { it.name in storedPageNames }
            },
        )
        val storedDefaultPage = appSettingsStorage.getString(
            AppSettingPrivateStorage.KEY_BOTTOM_BAR_DEFAULT_PAGE,
        )?.let { storedName ->
            AppPage.entries.firstOrNull { it.name == storedName }
        }
        return MainUiState(
            bottomBarPages = bottomBarPages,
            defaultBottomBarPage = resolveDefaultBottomBarPage(
                page = storedDefaultPage,
                availablePages = bottomBarPages,
            ),
        )
    }

    private fun normalizeBottomBarPages(pages: Set<AppPage>): Set<AppPage> = buildSet {
        add(AppPage.SETTINGS)
        AppPage.entries
            .filter { it != AppPage.SETTINGS && it in pages }
            .take(MainUiState.MAX_BOTTOM_BAR_PAGES - 1)
            .forEach(::add)
    }

    private fun resolveDefaultBottomBarPage(
        page: AppPage?,
        availablePages: Set<AppPage>,
    ): AppPage = when {
        page != null && page in availablePages -> page
        AppPage.CORE in availablePages -> AppPage.CORE
        else -> AppPage.entries.first(availablePages::contains)
    }

    companion object {
        fun factory(
            appSettingsStorage: AppSettingPrivateStorage,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(appSettingsStorage)
            }
        }
    }
}
