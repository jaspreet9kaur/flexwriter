package io.github.jaspreet9kaur.flexwriter.typewriter

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Typewriter sounds, synthesized once into small WAV files in the app cache. No assets needed. */
class TypewriterSound(context: Context) {
    enum class Kind { Key, Space, Back, Return }

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<Kind, Int>

    init {
        val dir = File(context.cacheDir, "tw_sounds_v1").apply { mkdirs() }
        ids = mapOf(
            Kind.Key to load(File(dir, "key.wav")) { synthKey(1f) },
            Kind.Space to load(File(dir, "space.wav")) { synthKey(0.62f) },
            Kind.Back to load(File(dir, "back.wav")) { synthKey(1.35f) },
            Kind.Return to load(File(dir, "return.wav")) { synthReturn() },
        )
    }

    fun play(kind: Kind) {
        val id = ids[kind] ?: return
        val rate = 0.93f + Random.nextFloat() * 0.14f
        val vol = if (kind == Kind.Return) 0.8f else 0.55f + Random.nextFloat() * 0.15f
        pool.play(id, vol, vol, 1, 0, if (kind == Kind.Return) 1f else rate)
    }

    fun release() = pool.release()

    private fun load(file: File, synth: () -> ShortArray): Int {
        if (!file.exists() || file.length() < 64) writeWav(file, synth())
        return pool.load(file.path, 1)
    }

    private companion object {
        const val SR = 44100

        /** Type-bar strike: a metallic tick plus a soft platen thump. */
        fun synthKey(pitch: Float): ShortArray {
            val n = (SR * 0.075).toInt()
            val rnd = Random(7)
            var lp = 0.0
            return ShortArray(n) { i ->
                val t = i / SR.toDouble()
                val noise = rnd.nextDouble(-1.0, 1.0)
                lp += (noise - lp) * 0.35
                val tick = (noise - lp) * exp(-t / 0.0045) * 0.85
                val t2 = t - 0.009
                val clack = if (t2 > 0) noise * exp(-t2 / 0.006) * 0.35 else 0.0
                val thump = sin(2 * PI * 140 * pitch * t) * exp(-t / 0.018) * 0.55
                val ring = sin(2 * PI * 2900 * pitch * t) * exp(-t / 0.012) * 0.10
                ((tick + clack + thump + ring).coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
            }
        }

        /** Carriage return: ratchet zip, then the bell. */
        fun synthReturn(): ShortArray {
            val n = (SR * 1.0).toInt()
            val rnd = Random(11)
            return ShortArray(n) { i ->
                val t = i / SR.toDouble()
                var s = 0.0
                if (t < 0.26) {
                    val period = 0.011 + t * 0.02
                    val local = t % period
                    s += rnd.nextDouble(-1.0, 1.0) * exp(-local / 0.0018) * (0.55 - t)
                }
                val tb = t - 0.28
                if (tb > 0) {
                    val env = exp(-tb / 0.32)
                    s += (sin(2 * PI * 2350 * tb) + 0.45 * sin(2 * PI * 4720 * tb) + 0.25 * sin(2 * PI * 3110 * tb)) * env * 0.33
                }
                (s.coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
            }
        }

        fun writeWav(file: File, pcm: ShortArray) {
            val dataBytes = pcm.size * 2
            val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray()); buf.putInt(36 + dataBytes); buf.put("WAVE".toByteArray())
            buf.put("fmt ".toByteArray()); buf.putInt(16); buf.putShort(1); buf.putShort(1)
            buf.putInt(SR); buf.putInt(SR * 2); buf.putShort(2); buf.putShort(16)
            buf.put("data".toByteArray()); buf.putInt(dataBytes)
            pcm.forEach { buf.putShort(it) }
            FileOutputStream(file).use { it.write(buf.array()) }
        }
    }
}
