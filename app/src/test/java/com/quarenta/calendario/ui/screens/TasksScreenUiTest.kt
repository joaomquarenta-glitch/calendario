package com.quarenta.calendario.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.ui.tasks.TasksScreen
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
import java.time.LocalDate

/** The Tasks screen, drawn for real and used like a person would: read, type, tap. */
@RunWith(RobolectricTestRunner::class)
// A tall phone, so the whole list fits on screen at once.
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class TasksScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.now().toEpochDay()
    private val tasks = listOf(
        Task(id = 1, title = "Comprar pão", dueEpochDay = today),
        Task(id = 2, title = "Regar plantas", dueEpochDay = today - 2),
        Task(id = 3, title = "Ler um livro"),
        Task(id = 4, title = "Pagar a luz", done = true),
    )

    @After fun resetTheme() { ThemeState.selectedId = Themes.SYSTEM }

    private fun show(list: List<Task>, onAdd: (String) -> Unit = {}, onDelete: (Task) -> Unit = {}, onOpen: (Task) -> Unit = {}) {
        rule.setContent {
            CalendarioTheme { TasksScreen(tasks = list, onAdd = onAdd, onToggle = {}, onDelete = onDelete, onOpen = onOpen) }
        }
    }

    @Test fun groupsTasksByWhenTheyAreDue() {
        show(tasks)
        // "Hoje" is also the date line of today's task, so take the first match.
        listOf("Atrasadas", "Hoje", "Sem data", "Feitas").forEach { rule.onAllNodesWithText(it).onFirst().assertIsDisplayed() }
        listOf("Comprar pão", "Regar plantas", "Ler um livro", "Pagar a luz").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun emptyListInvitesTheFirstTask() {
        show(emptyList())
        rule.onNodeWithText("Ainda não há tarefas", substring = true).assertIsDisplayed()
    }

    @Test fun typingAndTappingAddSendsTheText() {
        val added = mutableListOf<String>()
        show(emptyList(), onAdd = { added += it })
        rule.onNodeWithText("Nova tarefa para hoje").performTextInput("Ligar ao dentista")
        rule.onNodeWithContentDescription("Adicionar tarefa").performClick()
        rule.runOnIdle { assertEquals(listOf("Ligar ao dentista"), added) }
    }

    @Test fun deleteButtonRemovesThatTask() {
        val deleted = mutableListOf<Long>()
        show(listOf(tasks[2]), onDelete = { deleted += it.id })
        rule.onAllNodesWithContentDescription("Apagar tarefa")[0].performClick()
        rule.runOnIdle { assertEquals(listOf(3L), deleted) }
    }

    @Test fun tappingATaskOpensIt() {
        val opened = mutableListOf<Long>()
        show(listOf(tasks[0]), onOpen = { opened += it.id })
        rule.onNodeWithText("Comprar pão").performClick()
        rule.runOnIdle { assertEquals(listOf(1L), opened) }
    }

    @Test fun blackAndGoldThemeDrawsTheSameScreen() {
        ThemeState.selectedId = Themes.Gold.id
        show(tasks)
        rule.onNodeWithText("Tarefas").assertIsDisplayed()
        rule.onNodeWithText("Pagar a luz").assertIsDisplayed()
    }
}
