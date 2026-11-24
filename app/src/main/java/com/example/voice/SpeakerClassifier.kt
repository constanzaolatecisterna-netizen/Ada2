package com.example.voice

import com.example.data.FamilyProfile
import java.text.Normalizer
import kotlin.math.roundToInt

enum class NoiseFilterMode(
    val label: String,
    val minRms: Float,
    val snrMultiplier: Float,
    val minSpeechFrames: Int,
) {
    STRICT(
        label = "Estricto",
        minRms = 520f,
        snrMultiplier = 2.7f,
        minSpeechFrames = 6, // ~192 ms sustained human speech
    ),
    BALANCED(
        label = "Normal",
        minRms = 340f,
        snrMultiplier = 2.1f,
        minSpeechFrames = 5, // ~160 ms
    ),
}

enum class SpeakerClassificationMethod(val label: String) {
    NAME_CUE("Identificado al hablar"),
    ACOUSTIC_VOICEPRINT("Huella vocal"),
    PITCH_ESTIMATE("Registro de voz"),
    UNKNOWN_GUEST("Voz externa"),
}

data class SpeakerClassification(
    val profileId: String?,
    val displayName: String,
    val confidencePercent: Int,
    val method: SpeakerClassificationMethod,
    val pitchHz: Int?,
    val registerLabel: String,
)

data class WakeWordResult(
    val triggered: Boolean,
    val commandText: String,
)

object SpeakerClassifier {

    private val wakeWordRegex = Regex(
        """^(?:(?:oye|hola|oiga|hey|ok|buenas|disculpa)[,:\s]+)?(?:a\.?d\.?a\.?|hada|ada)(?:[,:.\s]+(.*))?$""",
        RegexOption.IGNORE_CASE
    )

    private val inlineWakeWordRegex = Regex(
        """\b(?:oye\s+ada|hola\s+ada|oiga\s+ada|ok\s+ada|hey\s+ada|ada|hada)\b[,:.\s]*""",
        RegexOption.IGNORE_CASE
    )

    private val greetingOnlyRegex = Regex(
        """^(?:oye|hola|oiga|hey|ok|buenas|disculpa)[,.\s]*$""",
        RegexOption.IGNORE_CASE
    )

    fun parseWakeWord(rawTranscript: String): WakeWordResult {
        val clean = rawTranscript.trim()
        if (clean.isEmpty()) return WakeWordResult(false, "")
        val normalized = stripAccents(clean).lowercase()

        val direct = wakeWordRegex.find(normalized)
        if (direct != null) {
            val suffixLen = direct.groupValues[1].length
            val startIdx = (clean.length - suffixLen).coerceIn(0, clean.length)
            val rest = clean.substring(startIdx).trim()
            return WakeWordResult(triggered = true, commandText = rest)
        }

        val inline = inlineWakeWordRegex.find(normalized)
        if (inline != null) {
            val afterStart = (inline.range.last + 1).coerceIn(0, normalized.length)
            val beforeEnd = inline.range.first.coerceIn(0, normalized.length)
            val after = normalized.substring(afterStart).trim()
            val beforeRaw = normalized.substring(0, beforeEnd).trim()
            val before = if (greetingOnlyRegex.matches(beforeRaw)) "" else beforeRaw
            val cmd = after.ifEmpty { before }.trim()
            return WakeWordResult(triggered = true, commandText = cmd)
        }

        return WakeWordResult(triggered = false, commandText = "")
    }

    fun classify(
        samples: ShortArray?,
        transcript: String,
        profiles: List<FamilyProfile>,
        voicePrints: List<EnrolledVoicePrint>,
    ): SpeakerClassification? {
        val features = samples?.let { AudioFeatureExtractor.extractFeatures(it) }
        val registerLabel = features?.let { pitchRegister(it.f0Hz) } ?: "Voz activa"
        val pitchHz = features?.f0Hz?.roundToInt()

        // 1. Check explicit self-identification in speech (e.g., "Soy Papá", "Habla Constanza")
        val normalizedTranscript = stripAccents(transcript).lowercase()
        for (profile in profiles) {
            val normName = stripAccents(profile.name).lowercase()
            val normRole = stripAccents(profile.role).lowercase()
            val matchesSelf = listOf(
                "soy $normName",
                "habla $normName",
                "te habla $normName",
                "soy $normRole",
                "habla $normRole",
            ).any { normalizedTranscript.contains(it) }
            if (matchesSelf) {
                return SpeakerClassification(
                    profileId = profile.id,
                    displayName = profile.name,
                    confidencePercent = 97,
                    method = SpeakerClassificationMethod.NAME_CUE,
                    pitchHz = pitchHz,
                    registerLabel = registerLabel,
                )
            }
        }

        // 2. Acoustic voiceprint classification if audio features + enrolled prints exist
        if (features != null && voicePrints.isNotEmpty()) {
            val scored = voicePrints.map { print ->
                val melSim = AudioFeatureExtractor.cosineSimilarity(features.melBands, print.melBands)
                    .coerceIn(0f, 1f)
                val f0Sim = AudioFeatureExtractor.pitchSimilarity(features.f0Hz, print.f0Hz)
                val centroidSim = AudioFeatureExtractor.centroidSimilarity(
                    features.spectralCentroidHz,
                    print.spectralCentroidHz
                )
                val combined = (0.55f * melSim) + (0.30f * f0Sim) + (0.15f * centroidSim)
                print to combined
            }.sortedByDescending { it.second }

            val best = scored.first()
            val secondScore = scored.getOrNull(1)?.second ?: 0f
            val margin = best.second - secondScore

            if (best.second >= 0.70f && (scored.size == 1 || margin >= 0.04f)) {
                val matchedProfile = profiles.firstOrNull { it.id == best.first.profileId }
                return SpeakerClassification(
                    profileId = matchedProfile?.id ?: best.first.profileId,
                    displayName = matchedProfile?.name ?: best.first.profileName,
                    confidencePercent = (best.second * 100f).roundToInt().coerceIn(55, 99),
                    method = SpeakerClassificationMethod.ACOUSTIC_VOICEPRINT,
                    pitchHz = pitchHz,
                    registerLabel = registerLabel,
                )
            }

            return SpeakerClassification(
                profileId = null,
                displayName = "Voz externa / Invitado",
                confidencePercent = (best.second * 100f).roundToInt().coerceIn(20, 68),
                method = SpeakerClassificationMethod.UNKNOWN_GUEST,
                pitchHz = pitchHz,
                registerLabel = registerLabel,
            )
        }

        // 3. Acoustic pitch register classification when no voiceprints are enrolled yet
        if (features != null && pitchHz != null) {
            return SpeakerClassification(
                profileId = null,
                displayName = "$registerLabel ($pitchHz Hz)",
                confidencePercent = 74,
                method = SpeakerClassificationMethod.PITCH_ESTIMATE,
                pitchHz = pitchHz,
                registerLabel = registerLabel,
            )
        }

        return null
    }

    private fun pitchRegister(f0Hz: Float): String = when {
        f0Hz < 145f -> "Voz grave"
        f0Hz < 205f -> "Voz media"
        else -> "Voz aguda"
    }

    private fun stripAccents(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
