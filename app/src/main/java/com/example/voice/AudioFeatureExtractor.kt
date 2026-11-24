package com.example.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class VoiceFeatureVector(
    val f0Hz: Float,
    val spectralCentroidHz: Float,
    val zcr: Float,
    val melBands: List<Float>,
    val voicedDurationMs: Int,
)

object AudioFeatureExtractor {
    const val SAMPLE_RATE = 16_000
    private const val FRAME_SIZE = 512 // 32 ms at 16 kHz
    private const val HOP_SIZE = 256   // 16 ms hop
    private const val MEL_BANDS = 12

    private val hammingWindow = FloatArray(FRAME_SIZE) { i ->
        (0.54 - 0.46 * cos(2.0 * PI * i / (FRAME_SIZE - 1))).toFloat()
    }

    private val melCenterBins: IntArray by lazy {
        val minMel = hzToMel(120f)
        val maxMel = hzToMel(4200f)
        IntArray(MEL_BANDS + 2) { idx ->
            val mel = minMel + (maxMel - minMel) * idx / (MEL_BANDS + 1)
            val hz = melToHz(mel)
            ((FRAME_SIZE + 1) * hz / SAMPLE_RATE).toInt().coerceIn(1, FRAME_SIZE / 2 - 1)
        }
    }

    fun computeRms(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Float {
        if (length <= 0 || offset >= samples.size) return 0f
        val end = min(samples.size, offset + length)
        var sumSq = 0.0
        for (i in offset until end) {
            val v = samples[i].toDouble()
            sumSq += v * v
        }
        return sqrt(sumSq / (end - offset)).toFloat()
    }

    fun computeZcr(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Float {
        if (length <= 1 || offset >= samples.size - 1) return 0f
        val end = min(samples.size, offset + length)
        var crossings = 0
        var prevPositive = samples[offset] >= 0
        for (i in (offset + 1) until end) {
            val curPositive = samples[i] >= 0
            if (curPositive != prevPositive) {
                crossings++
                prevPositive = curPositive
            }
        }
        return crossings.toFloat() / (end - offset - 1).toFloat()
    }

    fun extractFeatures(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): VoiceFeatureVector? {
        if (samples.size < FRAME_SIZE * 2) return null

        val overallRms = computeRms(samples)
        if (overallRms < 180f) return null

        val frameThreshold = max(220f, overallRms * 0.42f)
        val f0Samples = mutableListOf<Float>()
        val melAccum = FloatArray(MEL_BANDS)
        var centroidSum = 0.0
        var zcrSum = 0.0
        var voicedFrames = 0

        val real = FloatArray(FRAME_SIZE)
        val imag = FloatArray(FRAME_SIZE)

        var start = 0
        while (start + FRAME_SIZE <= samples.size) {
            val rms = computeRms(samples, start, FRAME_SIZE)
            val zcr = computeZcr(samples, start, FRAME_SIZE)
            if (rms >= frameThreshold && zcr in 0.012f..0.34f) {
                voicedFrames++
                zcrSum += zcr

                estimatePitchF0(samples, start, FRAME_SIZE, sampleRate)?.let { f0 ->
                    f0Samples.add(f0)
                }

                for (i in 0 until FRAME_SIZE) {
                    real[i] = (samples[start + i] / 32768f) * hammingWindow[i]
                    imag[i] = 0f
                }
                fftInPlace(real, imag)

                val half = FRAME_SIZE / 2
                val mag = FloatArray(half)
                var magSum = 0.0
                var weightedFreqSum = 0.0
                for (bin in 1 until half) {
                    val m = sqrt((real[bin] * real[bin] + imag[bin] * imag[bin]).toDouble()).toFloat()
                    mag[bin] = m
                    val freq = bin.toDouble() * sampleRate / FRAME_SIZE
                    if (freq in 100.0..5000.0) {
                        magSum += m
                        weightedFreqSum += freq * m
                    }
                }
                if (magSum > 1e-6) {
                    centroidSum += weightedFreqSum / magSum
                }

                val bins = melCenterBins
                for (mIdx in 0 until MEL_BANDS) {
                    val left = bins[mIdx]
                    val center = bins[mIdx + 1]
                    val right = bins[mIdx + 2]
                    var bandEnergy = 0.0
                    for (b in left..right) {
                        val weight = when {
                            b <= center && center > left -> (b - left).toDouble() / (center - left)
                            b > center && right > center -> (right - b).toDouble() / (right - center)
                            else -> 1.0
                        }.coerceAtLeast(0.0)
                        bandEnergy += mag[b] * mag[b] * weight
                    }
                    melAccum[mIdx] += ln(1.0 + bandEnergy * 100.0).toFloat()
                }
            }
            start += HOP_SIZE
        }

        val voicedDurationMs = (voicedFrames * HOP_SIZE * 1000) / sampleRate
        if (voicedFrames < 6 || voicedDurationMs < 110) return null

        val avgMel = FloatArray(MEL_BANDS) { melAccum[it] / voicedFrames.toFloat() }
        // Cepstral-like mean normalization + L2 normalization to isolate timbre from volume
        val meanMel = avgMel.average().toFloat()
        var normSq = 0.0
        for (i in 0 until MEL_BANDS) {
            avgMel[i] -= meanMel
            normSq += avgMel[i] * avgMel[i]
        }
        val norm = sqrt(normSq).toFloat().coerceAtLeast(1e-5f)
        val normalizedMel = List(MEL_BANDS) { avgMel[it] / norm }

        val medianF0 = if (f0Samples.isNotEmpty()) {
            val sorted = f0Samples.sorted()
            sorted[sorted.size / 2]
        } else {
            165f
        }

        val avgCentroid = (centroidSum / voicedFrames.toDouble()).toFloat().coerceIn(300f, 4500f)
        val avgZcr = (zcrSum / voicedFrames.toDouble()).toFloat()

        return VoiceFeatureVector(
            f0Hz = medianF0,
            spectralCentroidHz = avgCentroid,
            zcr = avgZcr,
            melBands = normalizedMel,
            voicedDurationMs = voicedDurationMs,
        )
    }

    private fun estimatePitchF0(
        samples: ShortArray,
        offset: Int,
        length: Int,
        sampleRate: Int,
    ): Float? {
        val minLag = sampleRate / 380 // ~42 samples
        val maxLag = min(length - 2, sampleRate / 75) // ~213 samples
        if (maxLag <= minLag) return null

        val normalized = FloatArray(length)
        var mean = 0.0
        for (i in 0 until length) mean += samples[offset + i]
        mean /= length
        var zeroLagEnergy = 0.0
        for (i in 0 until length) {
            val v = (samples[offset + i] - mean).toFloat()
            normalized[i] = v
            zeroLagEnergy += v * v
        }
        if (zeroLagEnergy < 1e-3) return null

        var bestLag = -1
        var bestCorr = 0.0
        for (lag in minLag..maxLag) {
            var num = 0.0
            var denomA = 0.0
            var denomB = 0.0
            val count = length - lag
            for (i in 0 until count) {
                val a = normalized[i].toDouble()
                val b = normalized[i + lag].toDouble()
                num += a * b
                denomA += a * a
                denomB += b * b
            }
            val denom = sqrt(denomA * denomB)
            if (denom > 1e-6) {
                val corr = num / denom
                if (corr > bestCorr) {
                    bestCorr = corr
                    bestLag = lag
                }
            }
        }
        if (bestLag <= 0 || bestCorr < 0.48) return null
        return sampleRate.toFloat() / bestLag.toFloat()
    }

    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle).toFloat()
            val wLenI = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wR = 1f
                var wI = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + half] * wR - imag[i + k + half] * wI
                    val vI = real[i + k + half] * wI + imag[i + k + half] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + half] = uR - vR
                    imag[i + k + half] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun hzToMel(hz: Float): Float = (2595.0 * ln(1.0 + hz / 700.0) / ln(10.0)).toFloat()
    private fun melToHz(mel: Float): Float = (700.0 * (10.0.pow(mel / 2595.0) - 1.0)).toFloat()

    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA * normB)
        return if (denom > 1e-6) (dot / denom).toFloat().coerceIn(-1f, 1f) else 0f
    }

    fun pitchSimilarity(f0A: Float, f0B: Float): Float {
        val diff = abs(f0A - f0B)
        // Gaussian decay over ~55 Hz
        return kotlin.math.exp(-(diff * diff) / (2.0 * 55.0 * 55.0)).toFloat()
    }

    fun centroidSimilarity(cA: Float, cB: Float): Float {
        val diff = abs(cA - cB)
        return kotlin.math.exp(-(diff * diff) / (2.0 * 650.0 * 650.0)).toFloat()
    }
}
