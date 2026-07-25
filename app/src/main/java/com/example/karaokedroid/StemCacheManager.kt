package com.example.karaokedroid

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Manages SHA-256 content-based caching of separated audio stems (instrumental and vocals)
 * to avoid duplicate execution of expensive offline and deep-learning separation processes.
 */
object StemCacheManager {

    /**
     * Computes the SHA-256 hash of a file to uniquely identify it.
     */
    fun computeFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Gets the stem cache directory under the given base directory.
     */
    fun getCacheDir(baseDir: File): File {
        val cacheDir = File(baseDir, "stem_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * Determines if cached stems exist for the given input file and separation method.
     * Returns a [VocalSeparator.SeparationResult] if cached, or null otherwise.
     */
    fun getCachedStems(inputFile: File, method: String, cacheDir: File): VocalSeparator.SeparationResult? {
        val hash = try {
            computeFileHash(inputFile)
        } catch (e: Exception) {
            return null
        }
        val safeMethod = method.lowercase().replace(" ", "_").replace("-", "_")
        val cachedInst = File(cacheDir, "${hash}_${safeMethod}_instrumental.wav")
        val cachedVocal = File(cacheDir, "${hash}_${safeMethod}_vocals.wav")

        return if (cachedInst.exists() && cachedVocal.exists() && cachedInst.length() > 0 && cachedVocal.length() > 0) {
            VocalSeparator.SeparationResult(cachedInst, cachedVocal)
        } else {
            null
        }
    }

    /**
     * Copies the separated stems to the stem cache directory.
     */
    fun cacheStems(
        inputFile: File,
        method: String,
        result: VocalSeparator.SeparationResult,
        cacheDir: File
    ): VocalSeparator.SeparationResult {
        val hash = try {
            computeFileHash(inputFile)
        } catch (e: Exception) {
            return result
        }
        val safeMethod = method.lowercase().replace(" ", "_").replace("-", "_")
        val cachedInst = File(cacheDir, "${hash}_${safeMethod}_instrumental.wav")
        val cachedVocal = File(cacheDir, "${hash}_${safeMethod}_vocals.wav")

        try {
            if (result.instrumentalFile.exists() && result.instrumentalFile.absolutePath != cachedInst.absolutePath) {
                result.instrumentalFile.copyTo(cachedInst, overwrite = true)
            }
            if (result.vocalFile.exists() && result.vocalFile.absolutePath != cachedVocal.absolutePath) {
                result.vocalFile.copyTo(cachedVocal, overwrite = true)
            }
            return VocalSeparator.SeparationResult(cachedInst, cachedVocal)
        } catch (e: Exception) {
            e.printStackTrace()
            return result
        }
    }
}
