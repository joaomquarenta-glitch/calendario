package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GiftIdeasTest {
    @Test fun roundTrip() {
        val ideas = listOf(GiftIdea("Livro de receitas"), GiftIdea("Bilhetes\npara o concerto", bought = true))
        val back = GiftIdeas.decode(GiftIdeas.encode(ideas))
        assertEquals(listOf(GiftIdea("Livro de receitas"), GiftIdea("Bilhetes para o concerto", bought = true)), back)
    }

    @Test fun blankIdeasAreDropped() {
        assertEquals("", GiftIdeas.encode(listOf(GiftIdea("  "))))
        assertEquals(emptyList<GiftIdea>(), GiftIdeas.decode(null))
    }
}
