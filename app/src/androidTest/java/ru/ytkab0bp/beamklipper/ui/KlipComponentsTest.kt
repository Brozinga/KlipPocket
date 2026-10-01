package ru.ytkab0bp.beamklipper.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.ytkab0bp.beamklipper.ui.components.KlipButton
import ru.ytkab0bp.beamklipper.ui.components.KlipSwitch

class KlipComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun buttonRunsItsActionWhenTapped() {
        var taps = 0
        compose.setContent { KlipButton(text = "Run", onClick = { taps++ }) }
        compose.onNodeWithText("Run").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun disabledButtonIgnoresTaps() {
        var taps = 0
        compose.setContent { KlipButton(text = "Run", onClick = { taps++ }, enabled = false) }
        compose.onNodeWithText("Run").assertIsNotEnabled()
        compose.onNodeWithText("Run").performClick()
        assertEquals(0, taps)
    }

    @Test
    fun switchReportsTheNewValueAndFlips() {
        var reported = false
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            KlipSwitch(
                checked = checked,
                onCheckedChange = { checked = it; reported = it },
                modifier = Modifier.testTag("switch")
            )
        }
        compose.onNodeWithTag("switch").performClick()
        assertTrue(reported)
        compose.onNodeWithTag("switch").performClick()
        assertFalse(reported)
    }

    @Test
    fun switchWithoutHandlerHasNoClickAction() {
        compose.setContent { KlipSwitch(checked = false, modifier = Modifier.testTag("switch")) }
        compose.onNodeWithTag("switch").assertHasNoClickAction()
    }
}
