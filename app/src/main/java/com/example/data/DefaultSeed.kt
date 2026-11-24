package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun nowHHMM(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

data class DefaultProfileSeed(
    val id: String,
    val name: String,
    val role: String,
    val colorHex: String,
    val persona: String,
    val availability: String,
    val routines: List<Pair<String, String>>,
    val reminders: List<Triple<String, String, String>>,
    val contacts: List<Triple<String, String, String>>,
)

object DefaultSeed {
    const val DEFAULT_NETWORK_CODE = "ALIANZA-HOGAR"
    const val DEFAULT_ACTIVE_PROFILE_ID = "prof_constanza"

    val profileColors = listOf(
        "#38BDF8",
        "#F472B6",
        "#60A5FA",
        "#34D399",
        "#FBBF24",
        "#A78BFA",
    )

    val profiles = listOf(
        DefaultProfileSeed(
            id = "prof_constanza",
            name = "Constanza",
            role = "Creadora",
            colorHex = "#38BDF8",
            persona = "ada",
            availability = "Disponible",
            routines = listOf(
                "r1" to "Revisar mensajes de la familia",
                "r2" to "Tomar agua y descansar la vista",
            ),
            reminders = listOf(
                Triple("rem1", "20:30", "Revisar pendientes de mañana con ADA"),
            ),
            contacts = listOf(
                Triple("c1", "Mamá", ""),
                Triple("c2", "Papá", ""),
                Triple("c3", "Amor", ""),
                Triple("c4", "Hermano", ""),
                Triple("c5", "Abuelos", ""),
            ),
        ),
        DefaultProfileSeed(
            id = "prof_mama",
            name = "Mamá",
            role = "Madre",
            colorHex = "#F472B6",
            persona = "ada",
            availability = "Disponible",
            routines = listOf(
                "rm1" to "Tomar vitaminas de la mañana",
            ),
            reminders = emptyList(),
            contacts = listOf(
                Triple("cm1", "Constanza", ""),
            ),
        ),
        DefaultProfileSeed(
            id = "prof_papa",
            name = "Papá",
            role = "Padre",
            colorHex = "#60A5FA",
            persona = "ada",
            availability = "Disponible",
            routines = listOf(
                "rp1" to "Revisar agenda del día",
            ),
            reminders = emptyList(),
            contacts = listOf(
                Triple("cp1", "Constanza", ""),
            ),
        ),
        DefaultProfileSeed(
            id = "prof_hermano",
            name = "Hermano",
            role = "Hermano",
            colorHex = "#34D399",
            persona = "ada",
            availability = "Estudiando",
            routines = listOf(
                "rh1" to "Terminar tareas y estudios",
            ),
            reminders = emptyList(),
            contacts = listOf(
                Triple("ch1", "Mamá", ""),
            ),
        ),
        DefaultProfileSeed(
            id = "prof_abuelos",
            name = "Abuelos",
            role = "Abuelos",
            colorHex = "#FBBF24",
            persona = "ada",
            availability = "Descansando",
            routines = listOf(
                "ra1" to "Medicamento de la mañana",
                "ra2" to "Medicamento de la noche",
            ),
            reminders = listOf(
                Triple("rema1", "10:00", "Hora de tomar el medicamento con agua"),
            ),
            contacts = listOf(
                Triple("ca1", "Constanza", ""),
                Triple("ca2", "Mamá", ""),
            ),
        ),
        DefaultProfileSeed(
            id = "prof_novio",
            name = "Amor",
            role = "Novio",
            colorHex = "#A78BFA",
            persona = "ada",
            availability = "Disponible",
            routines = listOf(
                "rn1" to "Escribir a Constanza",
            ),
            reminders = emptyList(),
            contacts = listOf(
                Triple("cn1", "Constanza", ""),
            ),
        ),
    )
}
