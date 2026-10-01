package ru.ytkab0bp.beamklipper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.junit.Rule
import org.junit.Test
import ru.ytkab0bp.beamklipper.ui.components.KlipTile
import ru.ytkab0bp.beamklipper.utils.Frontends
import ru.ytkab0bp.beamklipper.utils.Prefs

// The front end tile of Settings, without the ViewModel: a KlipTile that
// shows the current front end's name and advances it with Frontends.next.
class FrontendTileTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tapCyclesFluiddMainsailVoyager() {
        compose.setContent {
            var frontend by remember { mutableStateOf(Prefs.FRONTEND_FLUIDD) }
            KlipTile(
                modifier = Modifier.testTag("tile"),
                onClick = { frontend = Frontends.next(frontend) }
            ) {
                Column { Text(stringResource(Frontends.nameRes(frontend))) }
            }
        }
        compose.onNodeWithText("Fluidd").assertIsDisplayed()
        compose.onNodeWithTag("tile").performClick()
        compose.onNodeWithText("Mainsail").assertIsDisplayed()
        compose.onNodeWithTag("tile").performClick()
        compose.onNodeWithText("Voyager UI").assertIsDisplayed()
        compose.onNodeWithTag("tile").performClick()
        compose.onNodeWithText("Fluidd").assertIsDisplayed()
    }
}
