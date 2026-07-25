package com.example.karaokedroid

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Common data structure to report progress updates of neural or DSP separation algorithms.
 */
data class ProgressUpdate(
    val step: String,
    val progress: Float,
    val logLine: String
)

/**
 * Unified interface for on-device and simulated vocal separators.
 */
interface AudioSeparator {
    val name: String

    fun separate(
        inputFile: File,
        outputDir: File,
        onProgress: ((ProgressUpdate) -> Unit)? = null
    ): VocalSeparator.SeparationResult
}

/**
 * Abstract base class that provides common helper DSP utilities for reading WAV files,
 * extracting raw PCM bytes, and formatting/writing standardized mono WAV stem files.
 */
abstract class BaseAudioSeparator : AudioSeparator {

    /**
     * Helper to read channels and sample rate from a 16-bit PCM WAV header.
     * Returns a Pair of (channels, sampleRate). Defaults to (1, 44100) if invalid or mono.
     */
    protected fun readWavInfo(inputFile: File): Pair<Int, Int> {
        var sampleRate = 44100
        var channels = 1
        try {
            FileInputStream(inputFile).use { fis ->
                val header = ByteArray(44)
                val bytesRead = fis.read(header)
                if (bytesRead >= 44) {
                    val riff = String(header, 0, 4)
                    val wave = String(header, 8, 4)
                    if (riff == "RIFF" && wave == "WAVE") {
                        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                        channels = buffer.getShort(22).toInt()
                        sampleRate = buffer.getInt(24)
                    }
                }
            }
        } catch (ignored: Exception) {}
        return Pair(channels, sampleRate)
    }

    /**
     * Reads all raw PCM bytes after the 44-byte WAV header.
     */
    protected fun readRawDataBytes(inputFile: File): ByteArray {
        return try {
            FileInputStream(inputFile).use { fis ->
                val header = ByteArray(44)
                fis.read(header) // skip header
                fis.readBytes()
            }
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    /**
     * Writes mono 16-bit PCM data to a valid WAV file.
     */
    protected fun writeMonoWavFile(file: File, pcmData: ByteArray, sampleRate: Int) {
        FileOutputStream(file).use { fos ->
            val header = createWavHeader(pcmData.size, sampleRate)
            fos.write(header)
            fos.write(pcmData)
        }
    }

    /**
     * Generates a 44-byte WAV header for mono 16-bit PCM.
     */
    private fun createWavHeader(pcmDataSize: Int, sampleRate: Int): ByteArray {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())

        buffer.putInt(36 + pcmDataSize)

        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())

        buffer.putInt(16)
        buffer.putShort(1.toShort()) // PCM
        buffer.putShort(1.toShort()) // Mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 1 * 16 / 8) // ByteRate
        buffer.putShort((1 * 16 / 8).toShort()) // BlockAlign
        buffer.putShort(16.toShort()) // BitsPerSample

        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())

        buffer.putInt(pcmDataSize)

        return header
    }
}
