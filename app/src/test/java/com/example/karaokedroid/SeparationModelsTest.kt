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

class SeparationModelsTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private fun createStereoWavFile(file: File, sampleRate: Int, numSamples: Int) {
        val inputDataSize = numSamples * 4 // Stereo 16-bit PCM (2 channels * 2 bytes = 4 bytes)
        FileOutputStream(file).use { fos ->
            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray())
            buffer.putInt(36 + inputDataSize)
            buffer.put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16)
            buffer.putShort(1.toShort()) // PCM
            buffer.putShort(2.toShort()) // Stereo
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 4) // ByteRate
            buffer.putShort(4.toShort()) // BlockAlign
            buffer.putShort(16.toShort()) // BitsPerSample
            buffer.put("data".toByteArray())
            buffer.putInt(inputDataSize)
            fos.write(header)

            val samples = ByteBuffer.allocate(inputDataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numSamples) {
                // Simulate left channel (e.g. low freq) and right channel (e.g. high freq)
                samples.putShort((i * 10).toShort()) // Left channel
                samples.putShort((i * 20).toShort()) // Right channel
            }
            fos.write(samples.array())
        }
    }

    private fun verifyWavHeaderIsMono(file: File) {
        assertTrue("Output file must exist", file.exists())
        assertTrue("Output file must not be empty", file.length() > 44)
        FileInputStream(file).use { fis ->
            val header = ByteArray(44)
            val bytesRead = fis.read(header)
            assertEquals("Must read a complete 44-byte header", 44, bytesRead)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(header, 0, 4))
            assertEquals("WAVE", String(header, 8, 4))
            assertEquals("fmt ", String(header, 12, 4))
            assertEquals("data", String(header, 36, 4))
            assertEquals("Output channels should be 1 (mono)", 1, buffer.getShort(22).toInt())
            assertEquals("Output bits per sample should be 16", 16, buffer.getShort(34).toInt())
        }
    }

    @Test
    fun testVocalSeparatorWithStereoInput() {
        val inputFile = tempFolder.newFile("stereo_input.wav")
        // Use more than 50 samples to exercise the filters in VocalSeparator
        createStereoWavFile(inputFile, sampleRate = 8000, numSamples = 100)

        val outputDir = tempFolder.newFolder("vocal_sep_out")
        val result = VocalSeparator.separate(inputFile, outputDir)

        verifyWavHeaderIsMono(result.instrumentalFile)
        verifyWavHeaderIsMono(result.vocalFile)

        // Verify the names conform to the correct schema
        assertEquals("stereo_input_instrumental.wav", result.instrumentalFile.name)
        assertEquals("stereo_input_vocals.wav", result.vocalFile.name)
    }

    @Test
    fun testDemucsVocalSeparatorWithStereoInput() {
        val inputFile = tempFolder.newFile("stereo_input.wav")
        createStereoWavFile(inputFile, sampleRate = 8000, numSamples = 100)

        val outputDir = tempFolder.newFolder("demucs_sep_out")
        val result = DemucsVocalSeparator.separate(inputFile, outputDir)

        verifyWavHeaderIsMono(result.instrumentalFile)
        verifyWavHeaderIsMono(result.vocalFile)

        assertEquals("stereo_input_demucs_instrumental.wav", result.instrumentalFile.name)
        assertEquals("stereo_input_demucs_vocals.wav", result.vocalFile.name)
    }

    @Test
    fun testMoisesLightVocalSeparatorWithStereoInput() {
        val inputFile = tempFolder.newFile("stereo_input.wav")
        createStereoWavFile(inputFile, sampleRate = 8000, numSamples = 100)

        val outputDir = tempFolder.newFolder("moises_sep_out")
        val result = MoisesLightVocalSeparator.separate(inputFile, outputDir)

        verifyWavHeaderIsMono(result.instrumentalFile)
        verifyWavHeaderIsMono(result.vocalFile)

        assertEquals("stereo_input_moises_light_instrumental.wav", result.instrumentalFile.name)
        assertEquals("stereo_input_moises_light_vocals.wav", result.vocalFile.name)
    }

    @Test
    fun testBsRoFormerVocalSeparatorWithStereoInput() {
        val inputFile = tempFolder.newFile("stereo_input.wav")
        createStereoWavFile(inputFile, sampleRate = 8000, numSamples = 100)

        val outputDir = tempFolder.newFolder("bs_roformer_sep_out")
        val result = BsRoFormerVocalSeparator.separate(inputFile, outputDir)

        verifyWavHeaderIsMono(result.instrumentalFile)
        verifyWavHeaderIsMono(result.vocalFile)

        assertEquals("stereo_input_bs_roformer_instrumental.wav", result.instrumentalFile.name)
        assertEquals("stereo_input_bs_roformer_vocals.wav", result.vocalFile.name)
    }

    @Test
    fun testOnnxAudioSeparatorWithStereoInput() {
        val inputFile = tempFolder.newFile("stereo_input.wav")
        createStereoWavFile(inputFile, sampleRate = 8000, numSamples = 100)

        val outputDir = tempFolder.newFolder("onnx_sep_out")
        val separator = OnnxAudioSeparator("ONNX Scaffolding", "models/htdemucs_vocals.onnx")
        val result = separator.separate(inputFile, outputDir)

        verifyWavHeaderIsMono(result.instrumentalFile)
        verifyWavHeaderIsMono(result.vocalFile)

        assertEquals("stereo_input_onnx_instrumental.wav", result.instrumentalFile.name)
        assertEquals("stereo_input_onnx_vocals.wav", result.vocalFile.name)
    }
}
