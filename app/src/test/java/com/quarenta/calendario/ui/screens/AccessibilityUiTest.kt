package com.quarenta.calendario.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.ui.tasks.TasksScreen
import com.quarenta.calendario.ui.theme.CalendarioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** What TalkBack and very large letters get from the Tasks screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class AccessibilityUiTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.now().toEpochDay()
    private val tasks = listOf(
        Task(id = 1, title = "Comprar pão", dueEpochDay = today),
        Task(id = 2, title = "Pagar a luz", done = true),
    )

    private val checkBox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun show(onToggle: (Task) -> Unit = {}) {
        rule.setContent {
            CalendarioTheme { TasksScreen(tasks = tasks, onAdd = {}, onToggle = onToggle, onDelete = {}, onOpen = {}) }
        }
    }

    @Test fun tickIsACheckBoxNamedAfterTheTask() {
        val toggled = mutableListOf<Long>()
        show(onToggle = { toggled += it.id })
        rule.onNode(hasContentDescription("Comprar pão") and checkBox).assertIsOff()
        rule.onNode(hasContentDescription("Pagar a luz") and checkBox).assertIsOn()
        rule.onNode(hasContentDescription("Comprar pão") and checkBox).performClick()
        rule.runOnIdle { assertEquals(listOf(1L), toggled) }
    }

    @Test fun titleIsAHeading() {
        show()
        rule.onNode(hasText("Tarefas") and heading).assertIsDisplayed()
    }

    @Test fun doubleSizeLettersStillShowEverything() {
        RuntimeEnvironment.setFontScale(2f)
        try {
            show()
            rule.onNode(hasText("Tarefas") and heading).assertIsDisplayed()
            rule.onNodeWithText("Comprar pão").assertIsDisplayed()
            rule.onNodeWithText("Nova tarefa para hoje").assertIsDisplayed()
            rule.onNodeWithContentDescription("Adicionar tarefa").assertIsDisplayed()
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }
}
