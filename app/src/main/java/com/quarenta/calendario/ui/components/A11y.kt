package com.quarenta.calendario.ui.components

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.quarenta.calendario.ui.Speech

/** Page titles: TalkBack can jump from title to title. */
fun Modifier.asHeading(): Modifier = semantics { heading() }

/**
 * A task's round tick. TalkBack hears a check box that says whether the task is done,
 * and [label] (the task's title) when the tick sits apart from the title.
 */
fun Modifier.taskToggle(done: Boolean, label: String? = null, onToggle: () -> Unit): Modifier =
    semantics {
        stateDescription = Speech.taskState(done)
        if (label != null) contentDescription = label
    }.toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
