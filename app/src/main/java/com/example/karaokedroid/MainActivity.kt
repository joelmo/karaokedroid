package com.example.karaokedroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

fun formatTime(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / 60000)
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    return "$mb MB"
}

data class LyricLine(val startTimeMs: Long, val endTimeMs: Long, val text: String)

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val assetPath: String?, // null for custom songs
    var durationMs: Long,
    val lyrics: List<LyricLine>,
    val isCustom: Boolean = false,
    val customFile: File? = null,
    val instrumentalFile: File? = null,
    val vocalFile: File? = null
)

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordingFile: File? = null
    private var voicePlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val builtInSongs = listOf(
            Song(
                id = "twinkle",
                title = "Twinkle Twinkle Little Star",
                artist = "Traditional Nursery Rhyme",
                assetPath = "twinkle.wav",
                durationMs = 12000L,
                lyrics = listOf(
                    LyricLine(0L, 800L, "Twinkle,"),
                    LyricLine(800L, 1600L, "twinkle,"),
                    LyricLine(1600L, 2400L, "little"),
                    LyricLine(2400L, 3200L, "star,"),
                    LyricLine(3200L, 4000L, "How I"),
                    LyricLine(4000L, 4800L, "wonder"),
                    LyricLine(4800L, 5600L, "what you"),
                    LyricLine(5600L, 6400L, "are."),
                    LyricLine(6400L, 7200L, "[Instrumental Solo]"),
                    LyricLine(7200L, 8000L, "Up"),
                    LyricLine(8000L, 8800L, "above the"),
                    LyricLine(8800L, 9600L, "world so"),
                    LyricLine(9600L, 10400L, "high,"),
                    LyricLine(10400L, 11200L, "Like a"),
                    LyricLine(11200L, 12000L, "diamond")
                )
            ),
            Song(
                id = "birthday",
                title = "Happy Birthday to You",
                artist = "Traditional Celebration",
                assetPath = "birthday.wav",
                durationMs = 10400L,
                lyrics = listOf(
                    LyricLine(0L, 800L, "Happy"),
                    LyricLine(800L, 1600L, "birthday"),
                    LyricLine(1600L, 2400L, "to"),
                    LyricLine(2400L, 3200L, "you,"),
                    LyricLine(3200L, 4000L, "Happy"),
                    LyricLine(4000L, 4800L, "birthday"),
                    LyricLine(4800L, 5600L, "to"),
                    LyricLine(5600L, 6400L, "you,"),
                    LyricLine(6400L, 7200L, "[Vocal Solo Part]"),
                    LyricLine(7200L, 8000L, "Happy"),
                    LyricLine(8000L, 8800L, "birthday"),
                    LyricLine(8800L, 9600L, "dear"),
                    LyricLine(9600L, 10400L, "friend!")
                )
            )
        )

        setContent {
            var songs by remember { mutableStateOf(builtInSongs) }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE91E63),
                    secondary = Color(0xFF00E5FF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.White,
                    onSecondary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KaraokeAppScreen(
                        songs = songs,
                        onPlaySong = { song, shouldRecord, trackType -> playSong(song, shouldRecord, trackType) },
                        onStopSong = { stopSong() },
                        onPlayVoiceRecording = { file -> playVoiceRecording(file) },
                        onStopVoiceRecording = { stopVoiceRecording() },
                        onAddCustomSong = { newSong ->
                            songs = songs + newSong
                        }
                    )
                }
            }
        }
    }

    private fun playSong(song: Song, shouldRecord: Boolean, trackType: String) {
        stopSong()
        try {
            val mp = MediaPlayer()
            if (song.isCustom) {
                // Determine which separated file to play
                val fileToPlay = when (trackType) {
                    "Instrumental" -> song.instrumentalFile
                    "Vocals Only" -> song.vocalFile
                    else -> song.customFile
                } ?: song.customFile

                if (fileToPlay != null && fileToPlay.exists()) {
                    mp.setDataSource(fileToPlay.absolutePath)
                } else {
                    Toast.makeText(this, "Error: Selected separation track not found", Toast.LENGTH_SHORT).show()
                    return
                }
            } else {
                val descriptor = assets.openFd(song.assetPath ?: "twinkle.wav")
                mp.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                descriptor.close()
            }
            mp.prepare()
            mediaPlayer = mp
            mp.start()

            if (shouldRecord && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val outputDir = getExternalFilesDir(null) ?: filesDir
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val outFile = File(outputDir, "Karaoke_${song.id}_$timestamp.mp4")
                currentRecordingFile = outFile

                val mr = MediaRecorder(this)
                mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setOutputFile(outFile.absolutePath)
                mr.prepare()
                mr.start()
                mediaRecorder = mr
                isRecording = true
                Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error playing song: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopSong() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null

            if (isRecording) {
                mediaRecorder?.let {
                    it.stop()
                    it.release()
                }
                mediaRecorder = null
                isRecording = false
                Toast.makeText(this, "Recording saved: ${currentRecordingFile?.name}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playVoiceRecording(file: File) {
        stopVoiceRecording()
        try {
            val vp = MediaPlayer()
            vp.setDataSource(file.absolutePath)
            vp.prepare()
            voicePlayer = vp
            vp.start()
            Toast.makeText(this, "Playing recording...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error playing voice recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoiceRecording() {
        try {
            voicePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            voicePlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSong()
        stopVoiceRecording()
    }
}

@Composable
fun KaraokeAppScreen(
    songs: List<Song>,
    onPlaySong: (Song, Boolean, String) -> Unit,
    onStopSong: () -> Unit,
    onPlayVoiceRecording: (File) -> Unit,
    onStopVoiceRecording: () -> Unit,
    onAddCustomSong: (Song) -> Unit
) {
    val context = LocalContext.current
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordSingingEnabled by remember { mutableStateOf(false) }
    var currentPlaybackPosition by remember { mutableStateOf(0L) }
    var recordingsList by remember { mutableStateOf(listOf<File>()) }

    // Custom song track playback toggle: "Full Mix", "Instrumental", "Vocals Only"
    var selectedTrackType by remember { mutableStateOf("Instrumental") }

    // Method selector: "Traditional DSP", "Meta Demucs Model", "Moises-Light Model", or "BS-RoFormer Model"
    var separationMethod by remember { mutableStateOf("Moises-Light Model") }

    // Separation progress state
    var isSeparating by remember { mutableStateOf(false) }
    var separationProgress by remember { mutableStateOf(0.0f) }
    var separationStep by remember { mutableStateOf("") }
    var separationLog by remember { mutableStateOf("") }

    val lyricsListState = rememberLazyListState()

    fun refreshRecordings() {
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val files = outputDir.listFiles { file -> file.name.startsWith("Karaoke_") && file.name.endsWith(".mp4") }
        recordingsList = files?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshRecordings()
    }

    // Permission request launcher
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            if (!granted) {
                Toast.makeText(context, "Microphone permission is needed to record your singing!", Toast.LENGTH_SHORT).show()
                recordSingingEnabled = false
            }
        }
    )

    // Audio File Picker Launcher (Supports any sound file - MP3, WAV, M4A, AAC, FLAC, etc.)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                thread {
                    try {
                        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                        val tempInputFile = File(outputDir, "custom_song_input_${System.currentTimeMillis()}")

                        Handler(Looper.getMainLooper()).post {
                            isSeparating = true
                            separationProgress = 0.02f
                            separationStep = "Importing File"
                            separationLog = "Copying chosen sound file..."
                        }

                        // Copy Uri content to temp input File
                        context.contentResolver.openInputStream(uri).use { input ->
                            tempInputFile.outputStream().use { output ->
                                input?.copyTo(output)
                            }
                        }

                        Handler(Looper.getMainLooper()).post {
                            separationProgress = 0.05f
                            separationStep = "Audio Decoding"
                            separationLog = "Decoding arbitrary sound format to linear 16-bit PCM WAV using AudioDecoder..."
                        }

                        // Standardize file to WAV format using AudioDecoder
                        val decodedWavFile = File(outputDir, "decoded_${System.currentTimeMillis()}.wav")
                        AudioDecoder.decodeToWav(context, Uri.fromFile(tempInputFile), decodedWavFile)

                        // Retrieve duration via retriever
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(decodedWavFile.absolutePath)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val duration = durationStr?.toLong() ?: 15000L
                        retriever.release()

                        // Delete temp un-decoded file
                        if (tempInputFile.exists()) {
                            tempInputFile.delete()
                        }

                        val customTitle = "Loaded Audio (${decodedWavFile.nameWithoutExtension.takeLast(6)})"
                        val cacheDir = StemCacheManager.getCacheDir(outputDir)
                        val cachedResult = StemCacheManager.getCachedStems(decodedWavFile, separationMethod, cacheDir)

                        if (cachedResult != null) {
                            Handler(Looper.getMainLooper()).post {
                                separationProgress = 0.5f
                                separationStep = "Stem Cache Hit"
                                separationLog = "Found pre-separated stems in cache! Loading..."
                            }
                            Thread.sleep(300)
                            Handler(Looper.getMainLooper()).post {
                                separationProgress = 1.0f
                                separationStep = "Completed"
                                separationLog = "Successfully loaded stems from cache!"
                            }
                            Thread.sleep(200)

                            val customSong = Song(
                                id = "custom_${System.currentTimeMillis()}",
                                title = customTitle,
                                artist = "$separationMethod (Cached)",
                                assetPath = null,
                                durationMs = duration,
                                lyrics = listOf(
                                    LyricLine(0L, duration / 3, "🎵 Custom Cached Song Loaded! 🎵"),
                                    LyricLine(duration / 3, 2 * duration / 3, "⚡ Loaded lightning-fast from stem cache! ⚡"),
                                    LyricLine(2 * duration / 3, duration, "✨ Enjoy singing along! ✨")
                                ),
                                isCustom = true,
                                customFile = decodedWavFile,
                                instrumentalFile = cachedResult.instrumentalFile,
                                vocalFile = cachedResult.vocalFile
                            )

                            Handler(Looper.getMainLooper()).post {
                                isSeparating = false
                                onAddCustomSong(customSong)
                                Toast.makeText(context, "Successfully loaded cached $separationMethod stems!", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            if (separationMethod == "BS-RoFormer Model") {
                                // BS-RoFormer vocal separation
                                val separation = BsRoFormerVocalSeparator.separateWithBsRoFormer(decodedWavFile, outputDir) { progressUpdate ->
                                    Handler(Looper.getMainLooper()).post {
                                        separationProgress = progressUpdate.progress
                                        separationStep = progressUpdate.step
                                        separationLog = progressUpdate.logLine
                                    }
                                }
                                val cachedStems = StemCacheManager.cacheStems(decodedWavFile, separationMethod, separation, cacheDir)

                                val customSong = Song(
                                    id = "custom_${System.currentTimeMillis()}",
                                    title = customTitle,
                                    artist = "BS-RoFormer Separated Audio",
                                    assetPath = null,
                                    durationMs = duration,
                                    lyrics = listOf(
                                        LyricLine(0L, duration / 3, "🎵 Custom BS-RoFormer Song Loaded! 🎵"),
                                        LyricLine(duration / 3, 2 * duration / 3, "🤖 Vocal Separation BS-RoFormer model complete! 🤖"),
                                        LyricLine(2 * duration / 3, duration, "✨ Enjoy singing along! ✨")
                                    ),
                                    isCustom = true,
                                    customFile = decodedWavFile,
                                    instrumentalFile = cachedStems.instrumentalFile,
                                    vocalFile = cachedStems.vocalFile
                                )

                                Handler(Looper.getMainLooper()).post {
                                    isSeparating = false
                                    onAddCustomSong(customSong)
                                    Toast.makeText(context, "Successfully separated vocals using BS-RoFormer model!", Toast.LENGTH_LONG).show()
                                }
                            } else if (separationMethod == "Moises-Light Model") {
                                // Moises-Light vocal separation
                                val separation = MoisesLightVocalSeparator.separateWithMoisesLight(decodedWavFile, outputDir) { progressUpdate ->
                                    Handler(Looper.getMainLooper()).post {
                                        separationProgress = progressUpdate.progress
                                        separationStep = progressUpdate.step
                                        separationLog = progressUpdate.logLine
                                    }
                                }
                                val cachedStems = StemCacheManager.cacheStems(decodedWavFile, separationMethod, separation, cacheDir)

                                val customSong = Song(
                                    id = "custom_${System.currentTimeMillis()}",
                                    title = customTitle,
                                    artist = "Moises-Light Separated Audio",
                                    assetPath = null,
                                    durationMs = duration,
                                    lyrics = listOf(
                                        LyricLine(0L, duration / 3, "🎵 Custom Moises-Light Song Loaded! 🎵"),
                                        LyricLine(duration / 3, 2 * duration / 3, "🤖 Vocal Separation Moises-Light model complete! 🤖"),
                                        LyricLine(2 * duration / 3, duration, "✨ Enjoy singing along! ✨"
                                        )
                                    ),
                                    isCustom = true,
                                    customFile = decodedWavFile,
                                    instrumentalFile = cachedStems.instrumentalFile,
                                    vocalFile = cachedStems.vocalFile
                                )

                                Handler(Looper.getMainLooper()).post {
                                    isSeparating = false
                                    onAddCustomSong(customSong)
                                    Toast.makeText(context, "Successfully separated vocals using Moises-Light model!", Toast.LENGTH_LONG).show()
                                }
                            } else if (separationMethod == "Meta Demucs Model") {
                                // Demucs vocal separation
                                val separation = DemucsVocalSeparator.separateWithDemucs(decodedWavFile, outputDir) { progressUpdate ->
                                    Handler(Looper.getMainLooper()).post {
                                        separationProgress = progressUpdate.progress
                                        separationStep = progressUpdate.step
                                        separationLog = progressUpdate.logLine
                                    }
                                }
                                val cachedStems = StemCacheManager.cacheStems(decodedWavFile, separationMethod, separation, cacheDir)

                                val customSong = Song(
                                    id = "custom_${System.currentTimeMillis()}",
                                    title = customTitle,
                                    artist = "Demucs Separated Audio",
                                    assetPath = null,
                                    durationMs = duration,
                                    lyrics = listOf(
                                        LyricLine(0L, duration / 3, "🎵 Custom Demucs Song Loaded! 🎵"),
                                        LyricLine(duration / 3, 2 * duration / 3, "🤖 Vocal Separation Meta Demucs model complete! 🤖"),
                                        LyricLine(2 * duration / 3, duration, "✨ Enjoy singing along! ✨")
                                    ),
                                    isCustom = true,
                                    customFile = decodedWavFile,
                                    instrumentalFile = cachedStems.instrumentalFile,
                                    vocalFile = cachedStems.vocalFile
                                )

                                Handler(Looper.getMainLooper()).post {
                                    isSeparating = false
                                    onAddCustomSong(customSong)
                                    Toast.makeText(context, "Successfully separated vocals using Demucs model!", Toast.LENGTH_LONG).show()
                                }
                            } else if (separationMethod == "ONNX Real Model Scaffold") {
                                // ONNX Vocal separation scaffold
                                val onnxSeparator = OnnxAudioSeparator("ONNX Model", "models/htdemucs_vocals.onnx")
                                val separation = onnxSeparator.separate(decodedWavFile, outputDir) { progressUpdate ->
                                    Handler(Looper.getMainLooper()).post {
                                        separationProgress = progressUpdate.progress
                                        separationStep = progressUpdate.step
                                        separationLog = progressUpdate.logLine
                                    }
                                }
                                val cachedStems = StemCacheManager.cacheStems(decodedWavFile, separationMethod, separation, cacheDir)

                                val customSong = Song(
                                    id = "custom_${System.currentTimeMillis()}",
                                    title = customTitle,
                                    artist = "ONNX Scaffold Separated Audio",
                                    assetPath = null,
                                    durationMs = duration,
                                    lyrics = listOf(
                                        LyricLine(0L, duration / 3, "🎵 Custom ONNX Song Loaded! 🎵"),
                                        LyricLine(duration / 3, 2 * duration / 3, "🤖 ONNX real-model inference pipeline complete! 🤖"),
                                        LyricLine(2 * duration / 3, duration, "✨ Enjoy singing along! ✨")
                                    ),
                                    isCustom = true,
                                    customFile = decodedWavFile,
                                    instrumentalFile = cachedStems.instrumentalFile,
                                    vocalFile = cachedStems.vocalFile
                                )

                                Handler(Looper.getMainLooper()).post {
                                    isSeparating = false
                                    onAddCustomSong(customSong)
                                    Toast.makeText(context, "Successfully separated vocals using ONNX pipeline!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                // Traditional DSP separation
                                Handler(Looper.getMainLooper()).post {
                                    separationProgress = 0.5f
                                    separationStep = "Traditional DSP"
                                    separationLog = "Applying channel cancellation (L - R) / vocal extraction ((L+R)/2)..."
                                }

                                val separation = VocalSeparator.separate(decodedWavFile, outputDir)
                                val cachedStems = StemCacheManager.cacheStems(decodedWavFile, separationMethod, separation, cacheDir)

                                val customSong = Song(
                                    id = "custom_${System.currentTimeMillis()}",
                                    title = customTitle,
                                    artist = "DSP Separated Audio",
                                    assetPath = null,
                                    durationMs = duration,
                                    lyrics = listOf(
                                        LyricLine(0L, duration / 3, "🎵 Custom DSP Song Loaded! 🎵"),
                                        LyricLine(duration / 3, 2 * duration / 3, "🎤 Vocal Separation DSP complete! 🎤"),
                                        LyricLine(2 * duration / 3, duration, "✨ Sing with separated backing track! ✨")
                                    ),
                                    isCustom = true,
                                    customFile = decodedWavFile,
                                    instrumentalFile = cachedStems.instrumentalFile,
                                    vocalFile = cachedStems.vocalFile
                                )

                                Handler(Looper.getMainLooper()).post {
                                    isSeparating = false
                                    onAddCustomSong(customSong)
                                    Toast.makeText(context, "Successfully separated vocals using DSP algorithm!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        Handler(Looper.getMainLooper()).post {
                            isSeparating = false
                            Toast.makeText(context, "Error processing audio: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    )

    LaunchedEffect(isPlaying, selectedSong) {
        if (isPlaying && selectedSong != null) {
            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    currentPlaybackPosition += 50L
                    if (currentPlaybackPosition >= (selectedSong?.durationMs ?: 0L)) {
                        isPlaying = false
                        onStopSong()
                        refreshRecordings()
                    } else {
                        handler.postDelayed(this, 50L)
                    }
                }
            }
            handler.postDelayed(runnable, 50L)
            currentPlaybackPosition = 0L
        } else {
            currentPlaybackPosition = 0L
        }
    }

    val activeIndex = remember(selectedSong, currentPlaybackPosition) {
        selectedSong?.lyrics?.indexOfFirst { lyric ->
            currentPlaybackPosition >= lyric.startTimeMs && currentPlaybackPosition < lyric.endTimeMs
        } ?: -1
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            lyricsListState.animateScrollToItem(activeIndex)
        }
    }

    // LLM Separation Progress Dialog
    if (isSeparating) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Vocal Separator AI",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    CircularProgressIndicator(
                        progress = separationProgress,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(80.dp)
                            .padding(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Step: $separationStep",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Text(
                        text = "${(separationProgress * 100).toInt()}% completed",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = separationLog,
                            color = Color.Green,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎤 KaraokeDroid 🎤",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Sing along with real-time synchronized lyrics!",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        if (selectedSong == null) {
            // Method selection UI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Select Vocal Extraction Model:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("BS-RoFormer", "Moises-Light", "Meta Demucs", "ONNX Model", "Traditional DSP").forEach { method ->
                            val displayMethod = when (method) {
                                "BS-RoFormer" -> "BS-RoFormer Model"
                                "Moises-Light" -> "Moises-Light Model"
                                "Meta Demucs" -> "Meta Demucs Model"
                                "ONNX Model" -> "ONNX Real Model Scaffold"
                                else -> "Traditional DSP"
                            }
                            OutlinedButton(
                                onClick = { separationMethod = displayMethod },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (separationMethod == displayMethod) MaterialTheme.colorScheme.secondary else Color.Transparent
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 1.dp),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text(
                                    text = method,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (separationMethod == displayMethod) Color.Black else Color.LightGray
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select a Song to Sing:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Load custom Audio file button (supports any audio file)
                Button(
                    onClick = { filePickerLauncher.launch("audio/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Load Audio 📂", color = Color.Black, fontSize = 12.sp)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(songs) { _, song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                selectedSong = song
                                isPlaying = false
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (song.isCustom) "📂" else "🎶",
                                fontSize = 24.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = song.title,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 14.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val currentSong = selectedSong!!

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentSong.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "by ${currentSong.artist}",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Custom Song Track Separation Toggle UI
                    if (currentSong.isCustom) {
                        Text(
                            text = "Playback Track Vocal Separation Option:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("Instrumental", "Vocals Only", "Full Mix").forEach { track ->
                                OutlinedButton(
                                    onClick = { selectedTrackType = track },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedTrackType == track) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text(
                                        text = track,
                                        fontSize = 10.sp,
                                        color = if (selectedTrackType == track) Color.White else Color.LightGray
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Slider(
                        value = currentPlaybackPosition.toFloat(),
                        onValueChange = {},
                        valueRange = 0f..(currentSong.durationMs.toFloat()),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatTime(currentPlaybackPosition), fontSize = 12.sp, color = Color.Gray)
                        Text(text = formatTime(currentSong.durationMs), fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    state = lyricsListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    contentPadding = PaddingValues(vertical = 100.dp)
                ) {
                    itemsIndexed(currentSong.lyrics) { index, lyric ->
                        val isActive = index == activeIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                            label = "textColor"
                        )
                        val fontSize by animateDpAsState(
                            targetValue = if (isActive) 24.dp else 16.dp,
                            label = "fontSize"
                        )

                        Text(
                            text = lyric.text,
                            color = textColor,
                            fontSize = fontSize.value.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Checkbox(
                        checked = recordSingingEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (hasMicPermission) {
                                    recordSingingEnabled = true
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                recordSingingEnabled = false
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "Record my voice while singing 🎙️",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            if (isPlaying) {
                                isPlaying = false
                                onStopSong()
                                refreshRecordings()
                            } else {
                                isPlaying = true
                                onPlaySong(currentSong, recordSingingEnabled, selectedTrackType)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying) Color.DarkGray else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(text = if (isPlaying) "Stop" else "Sing!")
                    }

                    Button(
                        onClick = {
                            isPlaying = false
                            onStopSong()
                            selectedSong = null
                            refreshRecordings()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text(text = "Back to Songs")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "My Recordings 🎧",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        if (recordingsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recordings yet. Check the 'Record my voice' box and press Sing!",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(recordingsList) { _, file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = formatFileSize(file.length()),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }

                            Row {
                                Button(
                                    onClick = { onPlayVoiceRecording(file) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.padding(end = 4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Play", color = Color.Black, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        onStopVoiceRecording()
                                        file.delete()
                                        refreshRecordings()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Del", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
