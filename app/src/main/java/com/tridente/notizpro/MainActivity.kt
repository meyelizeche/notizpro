package com.tridente.notizpro

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.tridente.notizpro.ui.theme.NotizproTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val tags: List<String>,
    val createdAt: String
)

class ApiException(message: String) : Exception(message)

object NotesApiClient {
    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    suspend fun getNotes(baseUrl: String): List<Note> = withContext(Dispatchers.IO) {
        val endpoint = "${normalizeBaseUrl(baseUrl)}/api/notes"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 8000
            readTimeout = 8000
        }

        try {
            val code = connection.responseCode
            val responseBody = readStream(connection, code in 200..299)
            if (code != HttpURLConnection.HTTP_OK) {
                throw ApiException("Error HTTP $code al obtener notas: $responseBody")
            }
            parseNotesArray(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun createNote(
        baseUrl: String,
        title: String,
        content: String,
        tags: List<String>
    ): Note = withContext(Dispatchers.IO) {
        val endpoint = "${normalizeBaseUrl(baseUrl)}/api/notes"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 8000
            readTimeout = 8000
        }

        try {
            val payload = JSONObject().apply {
                put("title", title)
                put("content", content)
                put("tags", JSONArray(tags))
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            val responseBody = readStream(connection, code in 200..299)
            when (code) {
                HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_OK -> {
                    parseNoteObject(JSONObject(responseBody))
                }
                in 400..499 -> {
                    throw ApiException(
                        "Error $code: Solicitud inválida (verifica título, contenido o formato JSON). $responseBody"
                    )
                }
                in 500..599 -> {
                    throw ApiException(
                        "Error $code: Fallo interno del servidor / base de datos."
                    )
                }
                else -> {
                    throw ApiException("Respuesta inesperada HTTP $code: $responseBody")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readStream(connection: HttpURLConnection, isSuccess: Boolean): String {
        val stream = if (isSuccess) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }
    }

    private fun parseNotesArray(jsonString: String): List<Note> {
        if (jsonString.isBlank()) return emptyList()
        val jsonArray = JSONArray(jsonString)
        val notes = mutableListOf<Note>()
        for (i in 0 until jsonArray.length()) {
            notes.add(parseNoteObject(jsonArray.getJSONObject(i)))
        }
        return notes
    }

    private fun parseNoteObject(obj: JSONObject): Note {
        val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
        val tags = mutableListOf<String>()
        for (i in 0 until tagsArray.length()) {
            tags.add(tagsArray.optString(i))
        }
        return Note(
            id = obj.getLong("id"),
            title = obj.optString("title", ""),
            content = obj.optString("content", ""),
            tags = tags,
            createdAt = obj.optString("created_at", "")
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotizproTheme {
                NotesApp()
            }
        }
    }
}

private const val PREFS_NAME = "notizpro_prefs"
private const val KEY_BASE_URL = "base_url"
private const val DEFAULT_BASE_URL = "http://10.0.2.2:3000"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var baseUrl by remember {
        mutableStateOf(prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL)
    }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showUrlDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun loadNotes(targetUrl: String = baseUrl) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                notes = NotesApiClient.getNotes(targetUrl)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "No se pudo conectar con $targetUrl"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(baseUrl) {
        loadNotes(baseUrl)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { showUrlDialog = true }
                    ) {
                        Text(
                            text = "NotizPro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showUrlDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurar URL Base"
                        )
                    }
                    IconButton(onClick = { loadNotes() }, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar notas"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Crear nota"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Error de conexión / API",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showUrlDialog = true }) {
                                Text("Cambiar URL Base")
                            }
                            Button(onClick = { loadNotes() }) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when {
                    isLoading && notes.isEmpty() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    notes.isEmpty() && errorMessage == null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No hay notas todavía",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pulsa el botón + para crear tu primera nota.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(notes, key = { it.id }) { note ->
                                NoteCard(note = note)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        BaseUrlDialog(
            currentUrl = baseUrl,
            onDismiss = { showUrlDialog = false },
            onSave = { newUrl ->
                val cleaned = newUrl.trim().trimEnd('/')
                prefs.edit { putString(KEY_BASE_URL, cleaned) }
                baseUrl = cleaned
                showUrlDialog = false
                loadNotes(cleaned)
            }
        )
    }

    if (showCreateDialog) {
        CreateNoteDialog(
            baseUrl = baseUrl,
            onDismiss = { showCreateDialog = false },
            onNoteCreated = { createdNote ->
                notes = listOf(createdNote) + notes.filterNot { it.id == createdNote.id }
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(note: Note) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "#${note.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium
            )

            if (note.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    note.tags.forEach { tag ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (note.createdAt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatTimestamp(note.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BaseUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf(currentUrl) }
    val presets = listOf(
        "Emulador Android" to "http://10.0.2.2:3000",
        "Localhost" to "http://127.0.0.1:3000",
        "LAN (Ejemplo)" to "http://192.168.1.100:3000"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar URL Base") },
        text = {
            Column {
                Text(
                    text = "Introduce la dirección del servidor de notas o selecciona una opción rápida:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("http://192.168.1.10:3000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Opciones rápidas:",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { (label, presetUrl) ->
                        SuggestionChip(
                            onClick = { urlInput = presetUrl },
                            label = { Text("$label ($presetUrl)") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(urlInput) },
                enabled = urlInput.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun CreateNoteDialog(
    baseUrl: String,
    onDismiss: () -> Unit,
    onNoteCreated: (Note) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Nueva Nota") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título *") },
                    placeholder = { Text("Ej. Compras") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Contenido *") },
                    placeholder = { Text("Ej. Leche y pan") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Etiquetas (separadas por coma)") },
                    placeholder = { Text("Ej. casa, urgente") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                if (submitError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = submitError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    val trimmedContent = content.trim()
                    if (trimmedTitle.isEmpty() || trimmedContent.isEmpty()) {
                        submitError = "El título y el contenido son obligatorios."
                        return@Button
                    }
                    val tags = tagsInput
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    scope.launch {
                        isSubmitting = true
                        submitError = null
                        try {
                            val created = NotesApiClient.createNote(
                                baseUrl = baseUrl,
                                title = trimmedTitle,
                                content = trimmedContent,
                                tags = tags
                            )
                            onNoteCreated(created)
                        } catch (e: Exception) {
                            submitError = e.localizedMessage ?: "Error al crear la nota"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancelar")
            }
        }
    )
}

private fun formatTimestamp(raw: String): String {
    return try {
        val parsed = OffsetDateTime.parse(raw)
        parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
    } catch (_: Exception) {
        raw
    }
}
