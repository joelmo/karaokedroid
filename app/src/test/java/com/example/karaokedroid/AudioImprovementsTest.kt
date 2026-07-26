package com.example.karaokedroid

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioImprovementsTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testFirstOrderIirFilter() {
        val filter = FirstOrderIirFilter(100.0, 8000.0)

        // Processing some samples
        val output1 = filter.process(1000.0)
        val output2 = filter.process(1000.0)

        // Ensure values are numbers and bounded
        assertTrue(!output1.isNaN())
        assertTrue(!output2.isNaN())

        filter.reset()
    }

    @Test
    fun testStemCacheManagerMatching() {
        val dummyFile = tempFolder.newFile("dummy_input.wav")
        FileOutputStream(dummyFile).use { fos ->
            fos.write("RIFF_test_WAVE_data_bytes_for_hashing_to_verify_correct_cache_retrieval".toByteArray())
        }

        val cacheDir = tempFolder.newFolder("stem_cache")

        // First lookup should miss
        val cachedBefore = StemCacheManager.getCachedStems(dummyFile, "meta_demucs", cacheDir)
        assertNull(cachedBefore)

        // Create separated stems
        val instFile = tempFolder.newFile("inst.wav")
        val vocalFile = tempFolder.newFile("vocal.wav")
        FileOutputStream(instFile).use { it.write("instrumental".toByteArray()) }
        FileOutputStream(vocalFile).use { it.write("vocals".toByteArray()) }

        val separationResult = VocalSeparator.SeparationResult(instFile, vocalFile)

        // Cache the stems
        val cachedResult = StemCacheManager.cacheStems(dummyFile, "meta_demucs", separationResult, cacheDir)
        assertNotNull(cachedResult)
        assertTrue(cachedResult.instrumentalFile.exists())
        assertTrue(cachedResult.vocalFile.exists())

        // Second lookup should hit
        val cachedAfter = StemCacheManager.getCachedStems(dummyFile, "meta_demucs", cacheDir)
        assertNotNull(cachedAfter)
        assertEquals(cachedResult.instrumentalFile.absolutePath, cachedAfter!!.instrumentalFile.absolutePath)
        assertEquals(cachedResult.vocalFile.absolutePath, cachedAfter.vocalFile.absolutePath)
    }

    @Test
    fun testOnnxAudioSeparatorScaffold() {
        val inputFile = tempFolder.newFile("input_standard.wav")
        val sampleRate = 8000
        val numSamples = 100
        val inputDataSize = numSamples * 2

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

        val outputDir = tempFolder.newFolder("onnx_out")
        val separator = OnnxAudioSeparator("ONNX Scaffolding", "models/demucs.onnx")

        val progressUpdates = mutableListOf<ProgressUpdate>()
        val result = separator.separate(inputFile, outputDir) { progress ->
            progressUpdates.add(progress)
        }

        assertNotNull(result)
        assertTrue(result.instrumentalFile.exists())
        assertTrue(result.vocalFile.exists())
        assertTrue(progressUpdates.isNotEmpty())
        assertEquals("Completed", progressUpdates.last().step)
    }
}
