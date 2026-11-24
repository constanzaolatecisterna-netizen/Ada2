package com.example.ai

import com.example.data.AvailabilityStatus
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface LocalAssistantIntent {
    data class CallContact(val targetQuery: String) : LocalAssistantIntent
    data class WhatsAppContact(val targetQuery: String, val messageBody: String) : LocalAssistantIntent
    data class AddRoutine(val text: String) : LocalAssistantIntent
    data class CompleteRoutine(val query: String) : LocalAssistantIntent
    data class AddReminder(val time: String?, val text: String) : LocalAssistantIntent
    data class BroadcastAlliance(val targetQuery: String, val text: String) : LocalAssistantIntent
    data class SetAvailability(val status: AvailabilityStatus) : LocalAssistantIntent
    data class UtilityAnswer(val answer: String) : LocalAssistantIntent
    data object ReadNotifications : LocalAssistantIntent
    data object ReadAllianceMessages : LocalAssistantIntent
    data object CheckWeather : LocalAssistantIntent
    data object StartCalm : LocalAssistantIntent
    data object DailySummary : LocalAssistantIntent
    data object ListRoutines : LocalAssistantIntent
    data object ListReminders : LocalAssistantIntent
    data object FamilyStatus : LocalAssistantIntent
    data object CheckTime : LocalAssistantIntent
    data object CheckDate : LocalAssistantIntent
    data object WhoIsSpeaking : LocalAssistantIntent
    data object HowAreYou : LocalAssistantIntent
    data object Greeting : LocalAssistantIntent
    data object Thanks : LocalAssistantIntent
    data object Help : LocalAssistantIntent
}

object LocalCommandMatcher {
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return diacriticsRegex.replace(decomposed, "")
            .replace(Regex("[¿?¡!.,;]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun match(rawText: String): LocalAssistantIntent? {
        val clean = rawText.trim()
        if (clean.isEmpty()) return null
        val norm = normalize(clean)

        // 1. Direct call / WhatsApp actions
        if (norm.startsWith("llama") || norm.contains("llamar a ") || norm.contains("haz una llamada")) {
            return LocalAssistantIntent.CallContact(clean)
        }
        if ((norm.contains("whatsapp") && !norm.contains("notificacion") && !norm.contains("lee") && !norm.contains("mensajes de") && !norm.contains("ultimo")) ||
            norm.startsWith("escribe a ") ||
            norm.startsWith("escribele a ") ||
            norm.startsWith("manda mensaje a ") ||
            norm.startsWith("enviar mensaje a ")
        ) {
            val splitRegex = Regex("\\b(?:que|diciendo)\\b|:", RegexOption.IGNORE_CASE)
            val parts = clean.split(splitRegex)
            val body = if (parts.size > 1) parts.drop(1).joinToString(" ").trim() else ""
            return LocalAssistantIntent.WhatsAppContact(clean, body)
        }

        // 2. Add or complete routine
        val addRoutineMatch = Regex(
            "^(?:agrega|anade|añade|nueva|crear|crea)\\s+(?:una\\s+)?rutina[:\\s]+(.+)$",
            RegexOption.IGNORE_CASE
        ).find(clean)
        if (addRoutineMatch != null) {
            val itemText = addRoutineMatch.groupValues[1].trim()
            if (itemText.isNotEmpty()) return LocalAssistantIntent.AddRoutine(itemText)
        }

        val doneRoutineMatch = Regex(
            "^(?:complete|completé|termine|terminé|ya hice|marcar como hecha|listo con)\\s+(?:la\\s+rutina\\s+(?:de\\s+)?)?(.+)$",
            RegexOption.IGNORE_CASE
        ).find(clean)
        if (doneRoutineMatch != null) {
            val q = doneRoutineMatch.groupValues[1].trim()
            if (q.length >= 2) return LocalAssistantIntent.CompleteRoutine(q)
        }

        // 3. Relative or exact reminders ("recuérdame en 15 minutos..." / "recuérdame a las 18:30...")
        val relRemMatch = Regex(
            "^(?:recuerdame|recuérdame|avisame|avísame)\\s+en\\s+(\\d+)\\s+(minuto|minutos|min|hora|horas)\\s*(?:que\\s+|de\\s+)?(.+)$",
            RegexOption.IGNORE_CASE
        ).find(clean)
        if (relRemMatch != null) {
            val amount = relRemMatch.groupValues[1].toIntOrNull()
            val unit = relRemMatch.groupValues[2].lowercase()
            val body = relRemMatch.groupValues[3].trim()
            if (amount != null && amount > 0 && body.isNotEmpty()) {
                val minsToAdd = if (unit.startsWith("hora")) amount * 60 else amount
                val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, minsToAdd) }
                val hhmm = String.format(
                    Locale.US,
                    "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                )
                return LocalAssistantIntent.AddReminder(hhmm, body)
            }
        }

        val addRemMatch = Regex(
            "^(?:recuerdame|recuérdame|crea\\s+un\\s+recordatorio|nuevo\\s+recordatorio|recordatorio)(?:\\s+a\\s+las?\\s+(\\d{1,2}:\\d{2}))?[:\\s]+(.+)$",
            RegexOption.IGNORE_CASE
        ).find(clean)
        if (addRemMatch != null) {
            val remTime = addRemMatch.groupValues[1].takeIf { it.isNotBlank() }?.padStart(5, '0')
            val remBody = addRemMatch.groupValues[2].trim()
            if (remBody.isNotEmpty()) return LocalAssistantIntent.AddReminder(remTime, remBody)
        }

        // 4. Broadcast or targeted message to Alianza family network
        val familyBroadcastPrefix = Regex(
            "^(?:avisa a la familia que|avisa a la familia|avisa a todos que|avisa a todos|avisa que|di a la familia que|di a la familia|manda aviso a la familia que|manda aviso)[:\\s]*",
            RegexOption.IGNORE_CASE
        )
        if (familyBroadcastPrefix.containsMatchIn(clean)) {
            val msg = clean.replace(familyBroadcastPrefix, "").trim().ifEmpty { clean }
            return LocalAssistantIntent.BroadcastAlliance("Toda la Familia", msg)
        }

        val personMsgMatch = Regex(
            "^(?:avisa|avisale|avísale|dile|di|envia aviso|envía aviso|manda aviso)\\s+a\\s+([^:]+?)\\s+(?:que\\s+)(.+)$",
            RegexOption.IGNORE_CASE
        ).find(clean)
        if (personMsgMatch != null) {
            val target = personMsgMatch.groupValues[1].trim()
            val msg = personMsgMatch.groupValues[2].trim()
            if (target.isNotEmpty() && msg.isNotEmpty()) {
                return LocalAssistantIntent.BroadcastAlliance(target, msg)
            }
        }

        // 5. Availability status switches
        if (isAvailabilityCommand(norm)) {
            val status = when {
                norm.contains("silencio") || norm.contains("no molestar") -> AvailabilityStatus.EN_SILENCIO
                norm.contains("ocupad") -> AvailabilityStatus.OCUPADO
                norm.contains("estudi") -> AvailabilityStatus.ESTUDIANDO
                norm.contains("descans") -> AvailabilityStatus.DESCANSANDO
                norm.contains("disponible") -> AvailabilityStatus.DISPONIBLE
                else -> null
            }
            if (status != null) return LocalAssistantIntent.SetAvailability(status)
        }

        // 6. Notifications & WhatsApp alerts reading
        if (norm in setOf("notificaciones", "alertas", "lee notificaciones", "leer notificaciones", "lee mis notificaciones") ||
            norm.contains("ultimas notificaciones") ||
            norm.contains("notificaciones recientes") ||
            norm.contains("mensajes de whatsapp") ||
            norm.contains("notificaciones de whatsapp") ||
            norm.contains("ultimo whatsapp") ||
            norm.contains("tengo notificaciones") ||
            norm.contains("hay notificaciones") ||
            norm.contains("llego algun whatsapp") ||
            norm.contains("notificacion nueva")
        ) {
            return LocalAssistantIntent.ReadNotifications
        }

        // 7. Red Alianza messages reading
        if (norm in setOf("mensajes", "leer mensajes", "lee mensajes", "lee mis mensajes") ||
            norm.contains("mensajes de la familia") ||
            norm.contains("mensajes de alianza") ||
            norm.contains("mensajes de red alianza") ||
            norm.contains("tengo mensajes") ||
            norm.contains("hay mensajes nuevos") ||
            norm.contains("avisos de la familia")
        ) {
            return LocalAssistantIntent.ReadAllianceMessages
        }

        // 8. Weather
        if (norm == "clima" ||
            norm.contains("como esta el clima") ||
            norm.contains("que clima hace") ||
            norm.contains("cual es el clima") ||
            norm.contains("temperatura") ||
            norm.contains("pronostico") ||
            norm.contains("va a llover") ||
            norm.contains("tiempo hoy")
        ) {
            return LocalAssistantIntent.CheckWeather
        }

        // 9. Calm breathing mode
        if (norm in setOf("calma", "modo calma", "respirar", "relajarme") ||
            norm.contains("activa modo calma") ||
            norm.contains("activar calma") ||
            norm.contains("necesito calma") ||
            norm.contains("quiero relajarme")
        ) {
            return LocalAssistantIntent.StartCalm
        }

        // 10. Daily briefing / routines / reminders / family status
        if (norm in setOf("resumen", "resumen del dia", "briefing", "briefing del dia") ||
            norm.contains("como va mi dia") ||
            norm.contains("que tengo para hoy") ||
            norm.contains("que tengo pendiente") ||
            norm.contains("que hago hoy") ||
            norm.contains("mi resumen")
        ) {
            return LocalAssistantIntent.DailySummary
        }

        if (norm in setOf("rutinas", "mis rutinas") ||
            norm.contains("que rutinas tengo") ||
            norm.contains("rutinas pendientes") ||
            norm.contains("tareas pendientes")
        ) {
            return LocalAssistantIntent.ListRoutines
        }

        if (norm in setOf("recordatorios", "mis recordatorios") ||
            norm.contains("que recordatorios tengo") ||
            norm.contains("tengo recordatorios")
        ) {
            return LocalAssistantIntent.ListReminders
        }

        if (norm.contains("estado de la familia") ||
            norm.contains("como esta la familia") ||
            norm.contains("quien esta disponible") ||
            norm.contains("quienes estan conectados") ||
            norm.contains("familia conectada")
        ) {
            return LocalAssistantIntent.FamilyStatus
        }

        // 11. Speaker / Time / Date
        if (norm.contains("quien esta hablando") || norm.contains("quien habla") || norm.contains("reconoces mi voz")) {
            return LocalAssistantIntent.WhoIsSpeaking
        }
        if (norm in setOf("hora", "que hora es", "dime la hora", "la hora")) {
            return LocalAssistantIntent.CheckTime
        }
        if (norm.contains("que dia es") || norm.contains("que fecha es") || norm == "fecha" || norm == "hoy") {
            return LocalAssistantIntent.CheckDate
        }

        // 12. Deterministic Math / Percentages / Unit conversions / Coin & Dice
        LocalUtilitySolver.trySolve(clean)?.let { answer ->
            return LocalAssistantIntent.UtilityAnswer(answer)
        }

        // 13. Conversational greetings, status, help, thanks
        if (norm in setOf("como estas", "como te sientes", "todo bien", "que tal")) {
            return LocalAssistantIntent.HowAreYou
        }
        if (norm in setOf("ayuda", "comandos") ||
            norm.contains("que puedes hacer") ||
            norm.contains("como funcionas")
        ) {
            return LocalAssistantIntent.Help
        }
        if (norm == "hola" ||
            norm.startsWith("hola ") ||
            norm.contains("buenos dias") ||
            norm.contains("buenas tardes") ||
            norm.contains("buenas noches")
        ) {
            return LocalAssistantIntent.Greeting
        }
        if (norm in setOf("gracias", "muchas gracias", "perfecto", "listo", "ok ada", "vale", "genial")) {
            return LocalAssistantIntent.Thanks
        }

        return null
    }

    private fun isAvailabilityCommand(norm: String): Boolean {
        val triggers = listOf("estoy ", "ponme ", "cambia mi estado", "cambiar estado", "modo ", "pasar a ")
        val mentionsStatus = norm.contains("ocupad") ||
            norm.contains("disponible") ||
            norm.contains("estudi") ||
            norm.contains("descans") ||
            norm.contains("silencio") ||
            norm.contains("no molestar")
        return mentionsStatus && (triggers.any { norm.contains(it) } || norm.split(" ").size <= 3)
    }

    fun formatCurrentSpanishDate(): String {
        val fmt = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES"))
        return fmt.format(Date())
    }
}
