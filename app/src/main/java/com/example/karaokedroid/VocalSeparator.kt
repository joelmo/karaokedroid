package com.example.karaokedroid

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object VocalSeparator {

    data class SeparationResult(val instrumentalFile: File, val vocalFile: File)

    /**
     * Reads a stereo 16-bit PCM WAV file, separates vocals and instrumentals,
     * and saves them as two mono 16-bit PCM WAV files.
     */
    fun separate(inputFile: File, outputDir: File): SeparationResult {
        val instrumentalFile = File(outputDir, "${inputFile.nameWithoutExtension}_instrumental.wav")
        val vocalFile = File(outputDir, "${inputFile.nameWithoutExtension}_vocals.wav")

        FileInputStream(inputFile).use { fis ->
            val header = ByteArray(44)
            val bytesRead = fis.read(header)
            if (bytesRead < 44) {
                // If it's too short, just copy or throw
                throw IllegalArgumentException("Invalid WAV file: too short")
            }

            // Verify RIFF and WAVE signatures
            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") {
                throw IllegalArgumentException("Not a valid RIFF/WAVE file")
            }

            // Read header details
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val channels = buffer.getShort(22).toInt()
            val sampleRate = buffer.getInt(24)
            val bitsPerSample = buffer.getShort(34).toInt()

            if (bitsPerSample != 16) {
                throw IllegalArgumentException("Only 16-bit PCM WAV files are supported")
            }

            if (channels != 2) {
                // If it is already mono, we cannot do L-R channel subtraction.
                // We will just copy it for both to handle gracefully.
                copyAsMono(inputFile, instrumentalFile)
                copyAsMono(inputFile, vocalFile)
                return SeparationResult(instrumentalFile, vocalFile)
            }

            // Stereo 16-bit PCM: we process
            val rawDataBytes = fis.readBytes()
            val totalFrames = rawDataBytes.size / 4 // 2 channels * 2 bytes = 4 bytes per frame

            val instData = ByteArray(totalFrames * 2) // 1 channel * 2 bytes = 2 bytes per frame
            val vocalData = ByteArray(totalFrames * 2)

            val instBuffer = ByteBuffer.wrap(instData).order(ByteOrder.LITTLE_ENDIAN)
            val vocalBuffer = ByteBuffer.wrap(vocalData).order(ByteOrder.LITTLE_ENDIAN)
            val inputBuffer = ByteBuffer.wrap(rawDataBytes).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until totalFrames) {
                val left = inputBuffer.getShort().toInt()
                val right = inputBuffer.getShort().toInt()

                // Instrumental (Vocal subtraction): L - R
                // We clip the value to fit inside a signed 16-bit short [-32768, 32767]
                val instSample = (left - right).coerceIn(-32768, 32767).toShort()
                instBuffer.putShort(instSample)

                // Vocal (Vocal extraction): (L + R) / 2
                val vocalSample = ((left + right) / 2).coerceIn(-32768, 32767).toShort()
                vocalBuffer.putShort(vocalSample)
            }

            // Write mono WAV files
            writeWavFile(instrumentalFile, instData, sampleRate)
            writeWavFile(vocalFile, vocalData, sampleRate)
        }

        return SeparationResult(instrumentalFile, vocalFile)
    }

    private fun copyAsMono(srcFile: File, destFile: File) {
        srcFile.copyTo(destFile, overwrite = true)
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int) {
        FileOutputStream(file).use { fos ->
            val header = createWavHeader(pcmData.size, sampleRate)
            fos.write(header)
            fos.write(pcmData)
        }
    }

    private fun createWavHeader(pcmDataSize: Int, sampleRate: Int): ByteArray {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // "RIFF"
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())

        // ChunkSize (PCM data size + 36)
        buffer.putInt(36 + pcmDataSize)

        // "WAVE"
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // "fmt "
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())

        // Subchunk1Size (16 for PCM)
        buffer.putInt(16)

        // AudioFormat (1 for PCM)
        buffer.putShort(1.toShort())

        // NumChannels (1 for mono)
        buffer.putShort(1.toShort())

        // SampleRate
        buffer.putInt(sampleRate)

        // ByteRate = SampleRate * NumChannels * BitsPerSample / 8
        buffer.putInt(sampleRate * 1 * 16 / 8)

        // BlockAlign = NumChannels * BitsPerSample / 8
        buffer.putShort((1 * 16 / 8).toShort())

        // BitsPerSample (16)
        buffer.putShort(16.toShort())

        // "data"
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())

        // Subchunk2Size (PCM data size)
        buffer.putInt(pcmDataSize)

        return header
    }
}
