package com.example.karaokedroid

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LlmVocalSeparatorTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testAudioDecoderFallback() {
        // Create dummy source file
        val dummyInput = tempFolder.newFile("dummy_input.wav")
        FileOutputStream(dummyInput).use { fos ->
            fos.write("RIFFdummyWAVEdummydata".toByteArray())
        }

        val decodedWav = tempFolder.newFile("decoded_output.wav")

        // Mock Android class dependencies to avoid "Method not mocked" issues on standard JVM
        val mockContext = mock(Context::class.java)
        val mockContentResolver = mock(ContentResolver::class.java)
        val mockUri = mock(Uri::class.java)

        `when`(mockContext.contentResolver).thenReturn(mockContentResolver)
        `when`(mockContentResolver.openInputStream(mockUri)).thenAnswer {
            FileInputStream(dummyInput)
        }

        val resultFile = AudioDecoder.decodeToWav(
            context = mockContext,
            inputUri = mockUri,
            outputFile = decodedWav
        )

        assertTrue(resultFile.exists())
        assertTrue(resultFile.length() > 0)
    }

    @Test
    fun testLlmVocalSeparatorCorrectness() {
        // Create standard WAV file to separate
        val inputFile = tempFolder.newFile("input_standard.wav")
        val sampleRate = 8000
        val numSamples = 100
        val inputDataSize = numSamples * 2 // Mono 16-bit PCM (1 channel * 2 bytes = 2 bytes)

        FileOutputStream(inputFile).use { fos ->
            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray())
            buffer.putInt(36 + inputDataSize)
            buffer.put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16)
            buffer.putShort(1.toShort()) // PCM
            buffer.putShort(1.toShort()) // Mono
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 2)
            buffer.putShort(2.toShort())
            buffer.putShort(16.toShort())
            buffer.put("data".toByteArray())
            buffer.putInt(inputDataSize)
            fos.write(header)

            val samples = ByteBuffer.allocate(inputDataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numSamples) {
                samples.putShort((i * 10).toShort())
            }
            fos.write(samples.array())
        }

        val outputDir = tempFolder.newFolder("llm_out")
        val updates = mutableListOf<LlmVocalSeparator.ProgressUpdate>()

        val separationResult = LlmVocalSeparator.separateWithLlm(inputFile, outputDir) { progress ->
            updates.add(progress)
        }

        assertTrue(separationResult.instrumentalFile.exists())
        assertTrue(separationResult.vocalFile.exists())

        // Ensure we received progress updates and logs from the LLM model
        assertTrue(updates.isNotEmpty())
        assertEquals("Initializing", updates.first().step)
        assertEquals("Completed", updates.last().step)
        assertEquals(1.0f, updates.last().progress, 0.01f)

        // Read separated vocal WAV to ensure validity
        FileInputStream(separationResult.vocalFile).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(header, 0, 4))
            assertEquals("WAVE", String(header, 8, 4))
            assertEquals(1, buffer.getShort(22).toInt()) // Mono output channel check
        }
    }
}
