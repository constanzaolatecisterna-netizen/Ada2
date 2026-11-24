package com.example.voice

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

@Serializable
data class EnrolledVoicePrint(
    val profileId: String,
    val profileName: String,
    val f0Hz: Float,
    val spectralCentroidHz: Float,
    val melBands: List<Float>,
    val sampleCount: Int = 1,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

class VoicePrintStore(context: Context, private val uid: String) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "ada_voiceprints_v1",
        Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val storageKey = "prints_$uid"

    fun loadAll(): List<EnrolledVoicePrint> {
        val raw = prefs.getString(storageKey, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<EnrolledVoicePrint>>(raw)
        }.getOrDefault(emptyList())
    }

    fun saveEnrollment(
        profileId: String,
        profileName: String,
        features: VoiceFeatureVector,
    ): List<EnrolledVoicePrint> {
        val current = loadAll().toMutableList()
        val existingIdx = current.indexOfFirst { it.profileId == profileId }
        val updated = if (existingIdx >= 0) {
            val prev = current[existingIdx]
            val n = prev.sampleCount.coerceAtLeast(1)
            val nextN = n + 1
            val blendedMel = prev.melBands.zip(features.melBands) { a, b ->
                (a * n + b) / nextN.toFloat()
            }
            val norm = sqrt(blendedMel.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-5f)
            EnrolledVoicePrint(
                profileId = profileId,
                profileName = profileName,
                f0Hz = (prev.f0Hz * n + features.f0Hz) / nextN.toFloat(),
                spectralCentroidHz = (prev.spectralCentroidHz * n + features.spectralCentroidHz) / nextN.toFloat(),
                melBands = blendedMel.map { it / norm },
                sampleCount = nextN,
                updatedAtMs = System.currentTimeMillis(),
            ).also { current[existingIdx] = it }
        } else {
            EnrolledVoicePrint(
                profileId = profileId,
                profileName = profileName,
                f0Hz = features.f0Hz,
                spectralCentroidHz = features.spectralCentroidHz,
                melBands = features.melBands,
                sampleCount = 1,
                updatedAtMs = System.currentTimeMillis(),
            ).also { current.add(it) }
        }
        persist(current)
        return current
    }

    fun removeEnrollment(profileId: String): List<EnrolledVoicePrint> {
        val next = loadAll().filterNot { it.profileId == profileId }
        persist(next)
        return next
    }

    private fun persist(list: List<EnrolledVoicePrint>) {
        prefs.edit().putString(storageKey, json.encodeToString(list)).apply()
    }
}
