package com.beastygdm.zmusic

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.random.Random

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long)

class MainActivity : ComponentActivity() {
    private var permissionGranted by mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) permissionLauncher.launch(permission)
        setContent { ZMusicApp(permissionGranted, { permissionLauncher.launch(permission) }) }
    }
    fun scan(): List<Track> {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                tracks += Track(id, c.getString(titleCol) ?: "Unknown title", c.getString(artistCol) ?: "Unknown artist", Uri.withAppendedPath(collection, id.toString()), c.getLong(durationCol))
            }
        }
        return tracks
    }
    @Composable private fun ZMusicApp(granted: Boolean, requestPermission: () -> Unit) {
        val tracks = remember(granted) { if (granted) scan() else emptyList() }
        var current by remember { mutableIntStateOf(-1) }
        var playing by remember { mutableStateOf(false) }
        var shuffle by remember { mutableStateOf(false) }
        var player by remember { mutableStateOf<MediaPlayer?>(null) }
        var query by remember { mutableStateOf("") }
        val filtered = tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
        fun play(index: Int) {
            if (tracks.isEmpty()) return
            val safe = index.coerceIn(0, tracks.lastIndex)
            player?.release()
            current = safe
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, tracks[safe].uri)
                setOnCompletionListener {
                    val next = if (shuffle && tracks.size > 1) (tracks.indices.filter { it != current }.random()) else (current + 1) % tracks.size
                    play(next)
                }
                setOnPreparedListener { it.start(); playing = true }
                prepareAsync()
            }
        }
        DisposableEffect(Unit) { onDispose { player?.release() } }
        val bg = Color(0xFF101010); val panel = Color(0xFF202020); val green = Color(0xFF1ED760)
        MaterialTheme(colorScheme = darkColorScheme(primary = green, background = bg, surface = panel)) {
            Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("z", color = green, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(" music", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.GraphicEq, null, tint = green)
                }
                Text("ТВОЯ МУЗЫКА", color = green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Библиотека", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp, bottom = 15.dp))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Поиск трека или исполнителя") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = green, unfocusedBorderColor = Color.DarkGray))
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${tracks.size} треков", color = Color.LightGray, modifier = Modifier.weight(1f))
                    FilterChip(selected = shuffle, onClick = { shuffle = !shuffle }, label = { Text("Перемешать") }, leadingIcon = { Icon(Icons.Default.Shuffle, null) })
                    if (!granted) TextButton(onClick = requestPermission) { Text("Дать доступ") }
                }
                if (!granted) {
                    Text("Разреши доступ к аудио, чтобы найти музыку на телефоне.", color = Color.LightGray)
                } else if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Музыка пока не найдена", color = Color.LightGray) }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                        itemsIndexed(filtered) { _, track ->
                            val original = tracks.indexOf(track)
                            Row(Modifier.fillMaxWidth().clickable { play(original) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).background(panel, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = if (original == current) green else Color.LightGray) }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(track.title, color = if (original == current) green else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(track.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
                                }
                                Text("%d:%02d".format(track.duration / 60000, (track.duration / 1000) % 60), color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (current >= 0) {
                    Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tracks[current].title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(tracks[current].artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = { if (tracks.isNotEmpty()) play(if (shuffle && tracks.size > 1) Random.nextInt(tracks.size) else (current - 1 + tracks.size) % tracks.size) }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                        IconButton(onClick = { if (playing) { player?.pause(); playing = false } else { player?.start(); playing = true } }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = green) }
                        IconButton(onClick = { if (tracks.isNotEmpty()) play(if (shuffle && tracks.size > 1) Random.nextInt(tracks.size) else (current + 1) % tracks.size) }) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
