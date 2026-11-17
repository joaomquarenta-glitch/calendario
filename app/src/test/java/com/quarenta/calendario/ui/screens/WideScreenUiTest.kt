package com.quarenta.calendario.ui.screens

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.ui.components.Adaptive
import com.quarenta.calendario.ui.components.CenteredContent
import com.quarenta.calendario.ui.components.TabRail
import com.quarenta.calendario.ui.components.isWide
import com.quarenta.calendario.ui.tasks.TasksScreen
import com.quarenta.calendario.ui.theme.CalendarioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A phone lying on its side: tabs on a rail, the screen centred and readable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w891dp-h411dp-land")
class WideScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private val tabs = listOf(
        "Início" to Icons.Rounded.Dashboard,
        "Agenda" to Icons.Rounded.CalendarMonth,
        "Pessoas" to Icons.Rounded.Groups,
        "Tarefas" to Icons.Rounded.CheckBox,
    )

    @Test fun sidewaysPhoneCountsAsWide() {
        var wide = false
        rule.setContent { wide = isWide() }
        rule.runOnIdle { assertTrue(wide) }
    }

    @Test fun railShowsEveryTabAndSwitches() {
        val picked = mutableListOf<Int>()
        rule.setContent { CalendarioTheme { TabRail(tabs, selected = 0, onSelect = { picked += it }) } }
        tabs.forEach { (label, _) -> rule.onNodeWithText(label).assertIsDisplayed() }
        rule.onNodeWithText("Início").assertIsSelected()
        rule.onNodeWithText("Tarefas").performClick()
        rule.runOnIdle { assertEquals(listOf(3), picked) }
    }

    @Test fun contentStaysAtAReadableWidth() {
        rule.setContent {
            CalendarioTheme {
                Row {
                    TabRail(tabs, selected = 3, onSelect = {})
                    CenteredContent(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxSize().testTag("tasks")) {
                            TasksScreen(tasks = listOf(Task(id = 1, title = "Comprar pão")), onAdd = {}, onToggle = {}, onDelete = {})
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("Comprar pão").assertIsDisplayed()
        val width = rule.onNodeWithTag("tasks").getUnclippedBoundsInRoot().width
        assertTrue("width $width", width <= Adaptive.MaxContent + 0.5.dp)
    }
}
