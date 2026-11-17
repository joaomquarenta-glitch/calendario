package com.quarenta.calendario.ui.screens

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.theme.CalendarioTheme
import com.quarenta.calendario.ui.theme.ThemeState
import com.quarenta.calendario.ui.theme.Themes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tile every screen is built from answers a tap in every look of the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BentoTileUiTest {
    @get:Rule val rule = createComposeRule()

    @After fun resetTheme() { ThemeState.selectedId = Themes.SYSTEM }

    private fun tapIn(themeId: String) {
        ThemeState.selectedId = themeId
        var taps = 0
        rule.setContent { CalendarioTheme { BentoTile(onClick = { taps++ }) { Text("Aniversários") } } }
        rule.onNodeWithText("Aniversários").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun tapWorksInLight() = tapIn(Themes.Light.id)
    @Test fun tapWorksInBlack() = tapIn(Themes.Black.id)
    @Test fun tapWorksInBlackAndGold() = tapIn(Themes.Gold.id)
}
