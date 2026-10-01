package ru.ytkab0bp.beamklipper.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ytkab0bp.beamklipper.PermissionsChecker
import ru.ytkab0bp.beamklipper.ui.screens.PermissionScreen
import ru.ytkab0bp.beamklipper.ui.state.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KlipApp(
    isCurrentLauncher: Boolean,
    mainViewModel: MainViewModel = viewModel()
) {
    var permissionBlocked by rememberSaveable { mutableStateOf(PermissionsChecker.needBlockStart()) }

    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        if (permissionBlocked) {
            PermissionScreen(
                onNext = {
                    permissionBlocked = false
                }
            )
        } else {
            NavHost(
                isCurrentLauncher = isCurrentLauncher,
                mainViewModel = mainViewModel
            )
        }
    }
}
