package com.everygo.musicplayer

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.everygo.musicplayer.databinding.ActivityEditMetadataBinding
import androidx.core.net.toUri

class EditMetadataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditMetadataBinding
    private lateinit var songUri: Uri
    private lateinit var songId: String

    // Launcher per il dialog di sistema di Android 10+ che chiede il permesso di scrittura
    private lateinit var writeRequestLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditMetadataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // Recupera i dati passati dall'intent
        songId  = intent.getStringExtra(EXTRA_SONG_ID)  ?: run { finish(); return }
        songUri = (intent.getStringExtra(EXTRA_SONG_URI) ?: run { finish(); return }).toUri()

        populateFields()
        setupWriteRequestLauncher()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { requestWriteAndSave() }
    }

    // ---- Popola i campi con i valori attuali ----

    private fun populateFields() {
        binding.etTitle.setText(intent.getStringExtra(EXTRA_TITLE))
        binding.etArtist.setText(intent.getStringExtra(EXTRA_ARTIST))
        binding.etAlbumArtist.setText(intent.getStringExtra(EXTRA_ALBUM_ARTIST))
        binding.etAlbum.setText(intent.getStringExtra(EXTRA_ALBUM))
        binding.etYear.setText(intent.getStringExtra(EXTRA_YEAR))
        binding.etTrackNumber.setText(intent.getStringExtra(EXTRA_TRACK_NUMBER))
        binding.etGenre.setText(intent.getStringExtra(EXTRA_GENRE))
    }

    // ---- Scrittura su MediaStore ----

    private fun setupWriteRequestLauncher() {
        writeRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            android.util.Log.d("MusicPlayer", "writeRequest result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                writeTagsDirectly()
            } else {
                Toast.makeText(this, getString(R.string.error_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestWriteAndSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pi = MediaStore.createWriteRequest(contentResolver, listOf(songUri))
                writeRequestLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.error_write_request), Toast.LENGTH_SHORT).show()
            }
        } else {
            writeTagsDirectly()
        }
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return contentResolver.query(uri, projection, null, null, null)?.use {
            val col = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            if (it.moveToFirst()) it.getString(col) else null
        }
    }

    private fun writeTagsDirectly() {
        try {
            val fileName = contentResolver.query(
                songUri,
                arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
                else null
            } ?: run {
                Toast.makeText(this, getString(R.string.error_metadata_save), Toast.LENGTH_SHORT).show()
                return
            }

            val pfd = contentResolver.openFileDescriptor(songUri, "rw") ?: run {
                Toast.makeText(this, getString(R.string.error_metadata_save), Toast.LENGTH_SHORT).show()
                return
            }

            pfd.use {
                val tempFile = java.io.File(cacheDir, fileName)
                java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                val mp3File = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
                        as org.jaudiotagger.audio.mp3.MP3File

                // Crea un tag ID3v2.4 fresco
                val newTag = org.jaudiotagger.tag.id3.ID3v24Tag()

                // Copia la copertina dal tag vecchio se esiste
                mp3File.iD3v2Tag?.let { oldTag ->
                    val artworks = oldTag.getFields(org.jaudiotagger.tag.FieldKey.COVER_ART)
                        artworks?.forEach { field -> newTag.setField(field) }
                }

                mp3File.iD3v2Tag = newTag

                fun setFieldSafe(key: org.jaudiotagger.tag.FieldKey, value: String) {
                    if (value.isBlank()) {
                        newTag.deleteField(key)
                        return
                    }
                    try {
                        newTag.setField(key, value)
                        android.util.Log.d("MusicPlayer", "OK: $key = $value")
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayer", "FAILED: $key = $value — ${e.message}")
                    }
                }

                setFieldSafe(org.jaudiotagger.tag.FieldKey.TITLE,        binding.etTitle.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.ARTIST,       binding.etArtist.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, binding.etAlbumArtist.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.ALBUM,        binding.etAlbum.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.YEAR,         binding.etYear.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.TRACK,        binding.etTrackNumber.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.GENRE,        binding.etGenre.text.toString().trim())

                mp3File.commit()

                val cachedSongs = SongCache.load(this)?.toMutableList()
                if (cachedSongs != null) {
                    val index = cachedSongs.indexOfFirst { it.id == songId.toLong() }
                    if (index != -1) {
                        val old = cachedSongs[index]
                        cachedSongs[index] = old.copy(
                            title       = binding.etTitle.text.toString().trim().ifBlank { old.title },
                            artist      = binding.etArtist.text.toString().trim().ifBlank { old.artist },
                            albumArtist = binding.etAlbumArtist.text.toString().trim().ifBlank { old.albumArtist },
                            album       = binding.etAlbum.text.toString().trim().ifBlank { old.album },
                            year        = binding.etYear.text.toString().trim().ifBlank { old.year },
                            trackNumber = binding.etTrackNumber.text.toString().trim().toIntOrNull() ?: old.trackNumber,
                            genre       = binding.etGenre.text.toString().trim().ifBlank { old.genre }
                        )
                        SongCache.save(this, cachedSongs)
                    }
                }

                val pfdWrite = contentResolver.openFileDescriptor(songUri, "rwt")!!
                pfdWrite.use {
                    java.io.FileOutputStream(pfdWrite.fileDescriptor).use { out ->
                        tempFile.inputStream().use { input -> input.copyTo(out) }
                    }
                }

                tempFile.delete()
            }

            val path = getRealPathFromUri(songUri)
            if (path != null) {
                android.media.MediaScannerConnection.scanFile(
                    this,
                    arrayOf(path),
                    null,
                    null
                )
            }

            setResult(RESULT_OK)
            Toast.makeText(this, getString(R.string.success_metadata_saved), Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayer", "JAudioTagger error: ${e::class.simpleName}: ${e.message}")
            Toast.makeText(this, getString(R.string.error_metadata_save), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_SONG_ID      = "song_id"
        const val EXTRA_SONG_URI     = "song_uri"
        const val EXTRA_TITLE        = "title"
        const val EXTRA_ARTIST       = "artist"
        const val EXTRA_ALBUM_ARTIST = "album_artist"
        const val EXTRA_ALBUM        = "album"
        const val EXTRA_YEAR         = "year"
        const val EXTRA_TRACK_NUMBER = "track_number"
        const val EXTRA_GENRE        = "genre"
    }
}
