package com.quarenta.calendario.ui.event

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.quarenta.calendario.data.EventKind

/** The symbol drawn on an event's card for its kind. */
fun EventKind.icon(): ImageVector = when (this) {
    EventKind.Birthday -> Icons.Rounded.Cake
    EventKind.Party -> Icons.Rounded.Celebration
    EventKind.Meal -> Icons.Rounded.Restaurant
    EventKind.Coffee -> Icons.Rounded.LocalCafe
    EventKind.Doctor -> Icons.Rounded.LocalHospital
    EventKind.Dentist -> Icons.Rounded.MedicalServices
    EventKind.Travel -> Icons.Rounded.Flight
    EventKind.Sport -> Icons.Rounded.FitnessCenter
    EventKind.Meeting -> Icons.Rounded.Groups
    EventKind.Call -> Icons.Rounded.Phone
    EventKind.School -> Icons.Rounded.School
    EventKind.Car -> Icons.Rounded.DirectionsCar
    EventKind.Hair -> Icons.Rounded.ContentCut
    EventKind.Shopping -> Icons.Rounded.ShoppingBag
    EventKind.Other -> Icons.Rounded.CalendarMonth
}
