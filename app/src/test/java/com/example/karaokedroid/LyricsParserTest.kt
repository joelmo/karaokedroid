package com.example.karaokedroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LyricsParserTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testFormatTime() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:05", formatTime(5000L))
        assertEquals("01:05", formatTime(65000L))
        assertEquals("10:00", formatTime(600000L))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("500 B", formatFileSize(500L))
        assertEquals("2 KB", formatFileSize(2048L))
        assertEquals("1 MB", formatFileSize(1048576L))
    }

    @Test
    fun testActiveLyricIndexMatching() {
        val lyrics = listOf(
            LyricLine(0L, 800L, "Twinkle,"),
            LyricLine(800L, 1600L, "twinkle,"),
            LyricLine(1600L, 2400L, "little"),
            LyricLine(2400L, 3200L, "star,")
        )

        fun getActiveIndex(timeMs: Long): Int {
            return lyrics.indexOfFirst { lyric ->
                timeMs >= lyric.startTimeMs && timeMs < lyric.endTimeMs
            }
        }

        assertEquals(0, getActiveIndex(0L))
        assertEquals(0, getActiveIndex(400L))
        assertEquals(1, getActiveIndex(800L))
        assertEquals(1, getActiveIndex(1500L))
        assertEquals(2, getActiveIndex(1600L))
        assertEquals(3, getActiveIndex(3000L))
        assertEquals(-1, getActiveIndex(3200L))
    }

    @Test
    fun testVocalSeparatorDSP() {
        // Create a temporary stereo 16-bit PCM WAV file
        val inputWav = tempFolder.newFile("test_input.wav")
        val sampleRate = 8000
        val numSamples = 2
        val inputDataSize = numSamples * 2 * 2 // 2 samples * 2 channels * 2 bytes = 8 bytes

        FileOutputStream(inputWav).use { fos ->
            // Write 44-byte stereo WAV header
            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray())
            buffer.putInt(36 + inputDataSize)
            buffer.put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16) // Subchunk1Size
            buffer.putShort(1.toShort()) // AudioFormat (PCM)
            buffer.putShort(2.toShort()) // NumChannels (Stereo)
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 2 * 16 / 8) // ByteRate
            buffer.putShort((2 * 16 / 8).toShort()) // BlockAlign
            buffer.putShort(16.toShort()) // BitsPerSample
            buffer.put("data".toByteArray())
            buffer.putInt(inputDataSize)
            fos.write(header)

            // Write test stereo samples:
            // Sample 1: Center panned (vocal) -> L = 2000, R = 2000
            // Sample 2: Out of phase (vocal cancelled) -> L = 5000, R = -5000
            val dataBuffer = ByteBuffer.allocate(inputDataSize).order(ByteOrder.LITTLE_ENDIAN)
            dataBuffer.putShort(2000.toShort()) // Sample 1 L
            dataBuffer.putShort(2000.toShort()) // Sample 1 R
            dataBuffer.putShort(5000.toShort()) // Sample 2 L
            dataBuffer.putShort((-5000).toShort()) // Sample 2 R
            fos.write(dataBuffer.array())
        }

        // Run separation
        val outputDir = tempFolder.newFolder("output")
        val result = VocalSeparator.separate(inputWav, outputDir)

        assertTrue(result.instrumentalFile.exists())
        assertTrue(result.vocalFile.exists())

        // Verify instrumental output (Mono, Vocal subtracted: L - R)
        FileInputStream(result.instrumentalFile).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(1, buffer.getShort(22).toInt()) // NumChannels should be 1 (mono)

            val samples = ByteArray(numSamples * 2)
            fis.read(samples)
            val sampleBuffer = ByteBuffer.wrap(samples).order(ByteOrder.LITTLE_ENDIAN)
            // Sample 1 should be: 2000 - 2000 = 0
            assertEquals(0.toShort(), sampleBuffer.getShort())
            // Sample 2 should be: 5000 - (-5000) = 10000
            assertEquals(10000.toShort(), sampleBuffer.getShort())
        }

        // Verify vocal output (Mono, Vocal extracted: (L + R) / 2)
        FileInputStream(result.vocalFile).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(1, buffer.getShort(22).toInt()) // NumChannels should be 1 (mono)

            val samples = ByteArray(numSamples * 2)
            fis.read(samples)
            val sampleBuffer = ByteBuffer.wrap(samples).order(ByteOrder.LITTLE_ENDIAN)
            // Sample 1 should be: (2000 + 2000) / 2 = 2000
            assertEquals(2000.toShort(), sampleBuffer.getShort())
            // Sample 2 should be: (5000 + (-5000)) / 2 = 0
            assertEquals(0.toShort(), sampleBuffer.getShort())
        }
    }
}
