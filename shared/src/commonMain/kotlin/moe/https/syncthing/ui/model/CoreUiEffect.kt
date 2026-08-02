package moe.https.syncthing.ui.model

sealed interface CoreUiEffect {
    data object OpenCorePicker : CoreUiEffect
}
