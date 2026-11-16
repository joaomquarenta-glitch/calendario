package com.quarenta.calendario.data

import java.text.Normalizer

/** What kind of thing an event is, guessed from its title, so its card can show the right symbol. */
enum class EventKind { Birthday, Meal, Coffee, Doctor, Dentist, Travel, Sport, Meeting, Call, School, Car, Hair, Shopping, Party, Other }

object EventKinds {
    // Checked in order: the first kind with a matching word wins, so "jantar de anos" is a party, not a meal.
    private val words: List<Pair<EventKind, List<String>>> = listOf(
        EventKind.Birthday to listOf("aniversario", "anos de", "faz anos", "birthday", "parabens"),
        EventKind.Party to listOf("festa", "casamento", "batizado", "baptizado", "party", "concerto", "arraial"),
        EventKind.Dentist to listOf("dentista", "dentaria", "higienista", "ortodont"),
        EventKind.Doctor to listOf(
            "medico", "medica", "consulta", "clinica", "hospital", "analises", "exame", "sams", "centro de saude",
            "fisioterapia", "vacina", "doctor", "oftalmo", "dermato", "cardio", "pediatra", "farmacia",
        ),
        EventKind.Travel to listOf("voo", "aeroporto", "viagem", "ferias", "hotel", "flight", "comboio", "check-in", "embarque", "trip"),
        EventKind.Sport to listOf(
            "ginasio", "gym", "treino", "corrida", "futebol", "padel", "tenis", "natacao", "piscina",
            "yoga", "pilates", "crossfit", "bicicleta", "caminhada", "jogo",
        ),
        EventKind.Coffee to listOf("cafe", "coffee", "pequeno-almoco", "pequeno almoco", "brunch", "lanche"),
        EventKind.Meal to listOf("jantar", "almoco", "restaurante", "dinner", "lunch", "churrasco", "petisco"),
        EventKind.Call to listOf("chamada", "telefonema", "call", "zoom", "teams", "videochamada", "meet"),
        EventKind.Meeting to listOf("reuniao", "meeting", "entrevista", "apresentacao", "workshop", "formacao", "conferencia"),
        EventKind.School to listOf("escola", "aula", "explicacao", "faculdade", "exame de", "reuniao de pais", "colegio"),
        EventKind.Car to listOf("carro", "oficina", "inspecao", "revisao do", "pneus", "mecanico"),
        EventKind.Hair to listOf("cabeleireiro", "barbeiro", "cabelo", "manicure", "unhas", "estetica", "spa"),
        EventKind.Shopping to listOf("compras", "supermercado", "mercado", "loja", "shopping", "ikea"),
    )

    fun of(title: String, sources: List<String> = emptyList()): EventKind {
        val text = plain(title)
        if (sources.any { plain(it).contains("birthday") || plain(it).contains("aniversario") }) return EventKind.Birthday
        // "exame de" (a school exam) must beat "exame" (a medical exam).
        if (Regex("\\bexame de (matematica|portugues|ingles|historia|fisica|quimica|conducao)").containsMatchIn(text)) return EventKind.School
        if ("reuniao de pais" in text) return EventKind.School
        return words.firstOrNull { (_, list) -> list.any { w -> matches(text, w) } }?.first ?: EventKind.Other
    }

    /** Short words must stand alone ("call" in "recall" is not a call); longer ones may start a word. */
    private fun matches(text: String, word: String): Boolean =
        if (word.length <= 4) Regex("(^|[^a-z])${Regex.escape(word)}($|[^a-z])").containsMatchIn(text)
        else Regex("(^|[^a-z])${Regex.escape(word)}").containsMatchIn(text)

    private fun plain(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
