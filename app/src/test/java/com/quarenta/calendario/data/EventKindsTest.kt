package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EventKindsTest {
    @Test
    fun readsTheKindFromPortugueseTitles() {
        assertEquals(EventKind.Meal, EventKinds.of("Jantar com a Marta"))
        assertEquals(EventKind.Meal, EventKinds.of("Almoço de equipa"))
        assertEquals(EventKind.Doctor, EventKinds.of("Consulta de rotina"))
        assertEquals(EventKind.Dentist, EventKinds.of("Dentista"))
        assertEquals(EventKind.Travel, EventKinds.of("Voo para o Porto"))
        assertEquals(EventKind.Sport, EventKinds.of("Ginásio"))
        assertEquals(EventKind.Coffee, EventKinds.of("Café com o Tiago"))
        assertEquals(EventKind.Meeting, EventKinds.of("Reunião de projeto"))
        assertEquals(EventKind.Hair, EventKinds.of("Cabeleireiro"))
        assertEquals(EventKind.Car, EventKinds.of("Inspeção do carro"))
    }

    @Test
    fun theOccasionWinsOverTheMeal() {
        assertEquals(EventKind.Birthday, EventKinds.of("Jantar de aniversário da Rita"))
        assertEquals(EventKind.Party, EventKinds.of("Jantar de casamento"))
        assertEquals(EventKind.Birthday, EventKinds.of("Rui Costa", listOf("Birthday calendar")))
    }

    @Test
    fun schoolExamsAndParentMeetingsAreSchool() {
        assertEquals(EventKind.School, EventKinds.of("Exame de matemática"))
        assertEquals(EventKind.School, EventKinds.of("Reunião de pais"))
        assertEquals(EventKind.Doctor, EventKinds.of("Exame ao sangue"))
    }

    @Test
    fun shortWordsMustStandAlone() {
        assertEquals(EventKind.Other, EventKinds.of("Recall do produto"))
        assertEquals(EventKind.Call, EventKinds.of("Call com a Ana"))
        assertEquals(EventKind.Other, EventKinds.of("Algo qualquer"))
    }
}
