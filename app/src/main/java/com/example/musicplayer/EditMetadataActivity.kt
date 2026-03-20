package com.example.musicplayer

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.musicplayer.databinding.ActivityEditMetadataBinding
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
                writeTagsDirectly()   // ← cambiato
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

    private fun writeTagsDirectly() {
        try {
            // Ricava il nome file reale con estensione da MediaStore
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
                // Copia il file descriptor in un file temporaneo con il nome corretto
                val tempFile = java.io.File(cacheDir, fileName)
                java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
                val tag = audioFile.tagOrCreateAndSetDefault

                fun setFieldSafe(key: org.jaudiotagger.tag.FieldKey, value: String) {
                    if (value.isBlank()) tag.deleteField(key)
                    else tag.setField(key, value)
                }

                setFieldSafe(org.jaudiotagger.tag.FieldKey.TITLE,        binding.etTitle.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.ARTIST,       binding.etArtist.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, binding.etAlbumArtist.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.ALBUM,        binding.etAlbum.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.YEAR,         binding.etYear.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.TRACK,        binding.etTrackNumber.text.toString().trim())
                setFieldSafe(org.jaudiotagger.tag.FieldKey.GENRE,        binding.etGenre.text.toString().trim())

                audioFile.commit()

                // Riscrivi il file modificato tramite il descriptor originale
                val pfdWrite = contentResolver.openFileDescriptor(songUri, "rwt")!!
                pfdWrite.use {
                    java.io.FileOutputStream(pfdWrite.fileDescriptor).use { out ->
                        tempFile.inputStream().use { input -> input.copyTo(out) }
                    }
                }

                tempFile.delete()
            }

            contentResolver.notifyChange(songUri, null)
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
