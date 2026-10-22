# AGENTS.md — NotizPro

Guía de contexto y convenciones para agentes de IA que trabajen en el repositorio **NotizPro**.

## 1. Descripción del Proyecto
**NotizPro** (`com.tridente.notizpro`) es una aplicación Android escrita en **Kotlin** con **Jetpack Compose (Material 3)** que actúa como cliente para una API REST de notas en JSON (backend basado en Axum). Permite consultar y crear notas, así como configurar dinámicamente la **URL Base** del servidor (evitando depender exclusivamente de `localhost`).

---

## 2. Stack Tecnológico y Configuración
- **Lenguaje**: Kotlin (JVM target 11)
- **UI**: Jetpack Compose + Material 3 (Compose BOM `2024.09.00`)
- **SDK**: `compileSdk = 36`, `minSdk = 36`, `targetSdk = 36`
- **Red y Serialización**: `java.net.HttpURLConnection` + `org.json` (`JSONObject`, `JSONArray`) ejecutados en `Dispatchers.IO` mediante corrutinas de Kotlin (`kotlinx.coroutines`).
- **Persistencia local**: `SharedPreferences` (`notizpro_prefs`) para almacenar la URL base seleccionada bajo la clave `base_url` (valor por defecto: `http://10.0.2.2:3000` para el emulador Android).

---

## 3. Estructura Principal del Proyecto
- `app/src/main/AndroidManifest.xml`:
  - Declara `<uses-permission android:name="android.permission.INTERNET"/>`.
  - Habilita `android:usesCleartextTraffic="true"` para permitir peticiones `http://` a servidores en red local o emulador.
- `app/src/main/java/com/tridente/notizpro/MainActivity.kt`:
  - `Note`: Modelo de datos (`id`, `title`, `content`, `tags`, `createdAt`).
  - `NotesApiClient`: Cliente HTTP con normalización de URL base y llamadas `getNotes` (`GET /api/notes`) y `createNote` (`POST /api/notes`).
  - `NotesApp`: Pantalla principal en Compose con barra superior interactiva, manejo de estados de carga/error, listado en `LazyColumn` y diálogos modales.
  - `BaseUrlDialog`: Selector de URL base con accesos rápidos (`http://10.0.2.2:3000`, `http://127.0.0.1:3000`, IP LAN) y entrada libre.
  - `CreateNoteDialog`: Formulario de creación de notas con validación de campos obligatorios y parseo de etiquetas separadas por coma.
- `app/src/main/java/com/tridente/notizpro/ui/theme/`: Definición de tema, colores y tipografía Material 3 (`NotizproTheme`).

---

## 4. Contrato de la API Backend

- **Headers requeridos**: `Content-Type: application/json`, `Accept: application/json`.
- **Autenticación**: Ninguna.
- **URL Base**: Configurable en la app (por ejemplo `http://10.0.2.2:3000` desde emulador Android hacia el host, `http://127.0.0.1:3000` con `adb reverse`, o `http://192.168.x.x:3000` en red local).

### `GET /api/notes`
- Devuelve `200 OK` con un arreglo JSON de notas ordenadas por `created_at DESC, id DESC` (o `[]` si no hay notas):
  ```json
  [
    {
      "id": 1,
      "title": "Compras",
      "content": "Leche y pan",
      "tags": ["casa"],
      "created_at": "2026-10-22T14:12:47.144226Z"
    }
  ]
  ```

### `POST /api/notes`
- **Body JSON**:
  - `title` (`string`, obligatorio)
  - `content` (`string`, obligatorio)
  - `tags` (`array<string>`, opcional, por defecto `[]`)
- **Respuestas**:
  - `201 Created`: Devuelve el objeto JSON de la nota creada.
  - `4xx` (`415` / `422`): Error de deserialización en Axum (JSON inválido, falta `title` o `content`, o falta `Content-Type: application/json`).
  - `500 Internal Server Error`: Fallo de base de datos en el servidor.

---

## 5. Reglas y Buenas Prácticas para Agentes
1. **No bloquear el hilo principal (Main Thread)**: Toda operación de red debe ejecutarse en `withContext(Dispatchers.IO)`.
2. **Respetar la URL Base dinámica**: Nunca codificar (`hardcode`) `127.0.0.1` o `localhost` en las llamadas de red; todas las rutas deben construirse a partir de la URL base configurada por el usuario y normalizada sin `/` duplicadas al final.
3. **Mantener dependencias ligeras**: Antes de añadir librerías externas en `app/build.gradle.kts`, verificar si el SDK de Android y las dependencias actuales de Compose/Coroutines cubren la necesidad.
4. **Verificación tras cambios**:
   - Inspeccionar problemas estáticos en los archivos modificados (`get_file_problems`).
   - Compilar el proyecto (`build_project` o `./gradlew assembleDebug`) para confirmar que no existan errores de Kotlin ni de recursos AAPT.
