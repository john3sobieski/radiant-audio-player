package com.example.localplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
 * Загрузка музыки из облака (Dropbox).
 *
 * Только в одну сторону: файлы скачиваются в отдельную папку внутри выбранной папки с музыкой.
 * В облако ничего не отправляется, ничего не меняется и не удаляется ни там, ни на устройстве.
 *
 * Вход через OAuth 2.0 (код + PKCE) в обычном браузере, без секретов в приложении и без сторонних библиотек.
 * Ключ приложения (app key) не секретен, они подставляются при сборке (см. app/build.gradle.kts).
 */

private const val FORM = "application/x-www-form-urlencoded"

/** Те же расширения, что ищет Library при сканировании папки. */
private val AUDIO_EXT = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav", "wma", "mka", "amr")

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

private fun query(vararg p: Pair<String, String>): String = p.joinToString("&") { "${enc(it.first)}=${enc(it.second)}" }

private fun b64url(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

enum class CloudId(val title: String, val folderName: String) {
    DROPBOX("Dropbox", "Dropbox");

    /** Ключ приложения для этого сервиса задан при сборке. Без него сервис в настройках не показывается. */
    val configured: Boolean
        get() = BuildConfig.DROPBOX_APP_KEY.isNotEmpty()

    val clientId: String
        get() = BuildConfig.DROPBOX_APP_KEY

    /** Схема адреса, на который сервис возвращает пользователя после входа (перехватывается MainActivity). */
    val redirectScheme: String
        get() = "db-${BuildConfig.DROPBOX_APP_KEY}"

    val redirectUri: String
        get() = "$redirectScheme://oauth"
}

class CloudException(message: String, val code: Int = 0) : IOException(message)

class CloudFolder(val id: String, val name: String)

/** [ref] нужен провайдеру для скачивания, [path] это путь относительно выбранной облачной папки (с именем файла), [size] в байтах или -1. */
class CloudFile(val ref: String, val path: List<String>, val size: Long)

/** Выбранная облачная папка: [id] пустой для корня. */
class CloudPick(val id: String, val name: String)

interface CloudProvider {
    /** Подпапки папки [parent] (пустая строка: корень). */
    fun listFolders(parent: String): List<CloudFolder>

    /** Все файлы внутри [root] (пустая строка: корень), включая вложенные папки. */
    fun listFiles(root: String): List<CloudFile>

    fun open(file: CloudFile): InputStream
}

object CloudProviders {
    fun create(ctx: Context, id: CloudId): CloudProvider = when (id) {
        CloudId.DROPBOX -> DropboxProvider(ctx.applicationContext)
    }
}

// =====================================================================
// HTTP
// =====================================================================

private object Http {
    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null,
    ): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        for ((k, v) in headers) c.setRequestProperty(k, v)
        if (body != null) {
            c.doOutput = true
            if (contentType != null) c.setRequestProperty("Content-Type", contentType)
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
        }
        return c
    }

    /** Ответ как текст. Любой код кроме 2xx становится [CloudException] с кодом. */
    fun text(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val s = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw CloudException(describe(code, s), code)
        return s
    }

    /** Тело ответа как поток (для скачивания файлов). */
    fun stream(c: HttpURLConnection): InputStream {
        val code = c.responseCode
        if (code in 200..299) return c.inputStream
        val s = c.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        c.disconnect()
        throw CloudException(describe(code, s), code)
    }

    private fun describe(code: Int, body: String): String {
        val o = runCatching { JSONObject(body) }.getOrNull()
        var text = ""
        if (o != null) {
            text = o.optString("error_description")
            if (text.isEmpty()) text = o.optString("error_summary")
            if (text.isEmpty()) text = o.optJSONObject("error")?.optString("message") ?: o.optString("error")
        } else if (body.isNotBlank()) {
            text = body.take(200)
        }
        return if (text.isEmpty()) "HTTP $code" else "HTTP $code: $text"
    }
}

// =====================================================================
// Хранилище токена: AES-GCM, ключ в Android Keystore
// =====================================================================

private object Vault {
    private const val ALIAS = "radiant_cloud_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    /** null, если расшифровать нельзя (например, данные восстановлены из резервной копии на другом устройстве). */
    fun open(sealed: String): String? = try {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

// =====================================================================
// Вход (OAuth 2.0, код + PKCE)
// =====================================================================

object CloudAuth {
    private const val PREFS = "cloud"
    private val random = SecureRandom()
    private val access = HashMap<CloudId, Pair<String, Long>>() // токен и момент истечения (мс)

    /** К каким сервисам выполнен вход (читается в Compose). */
    var connected by mutableStateOf(emptySet<CloudId>())
        private set

    /** Выбранные облачные папки (читается в Compose). */
    val folders = mutableStateMapOf<CloudId, CloudPick>()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun tokenUrl(@Suppress("UNUSED_PARAMETER") id: CloudId) = "https://api.dropboxapi.com/oauth2/token"

    private fun form(vararg p: Pair<String, String>): ByteArray = query(*p).toByteArray(Charsets.UTF_8)

    private fun readRefresh(ctx: Context, id: CloudId): String? {
        val sealed = prefs(ctx).getString("rt_${id.name}", null) ?: return null
        val plain = Vault.open(sealed)
        if (plain == null) prefs(ctx).edit().remove("rt_${id.name}").apply()
        return plain
    }

    /** Перечитывает состояние (вход и выбранные папки) из хранилища. */
    fun syncState(ctx: Context) {
        val p = prefs(ctx)
        val on = CloudId.entries.filter { readRefresh(ctx, it) != null }.toSet()
        connected = on
        for (id in CloudId.entries) {
            if (id in on) {
                folders[id] = CloudPick(
                    p.getString("folder_id_${id.name}", "") ?: "",
                    p.getString("folder_name_${id.name}", "") ?: "",
                )
            } else {
                folders.remove(id)
            }
        }
    }

    fun setFolder(ctx: Context, id: CloudId, folderId: String, name: String) {
        prefs(ctx).edit().putString("folder_id_${id.name}", folderId).putString("folder_name_${id.name}", name).apply()
        folders[id] = CloudPick(folderId, name)
    }

    /** Открывает страницу входа в браузере. false, если браузера нет. */
    fun begin(ctx: Context, id: CloudId): Boolean {
        val verifier = b64url(ByteArray(64).also { random.nextBytes(it) })
        val state = b64url(ByteArray(16).also { random.nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        prefs(ctx).edit()
            .putString("pkce_verifier", verifier).putString("pkce_state", state).putString("pkce_provider", id.name)
            .apply()
        val url = "https://www.dropbox.com/oauth2/authorize?" + query(
            "client_id" to id.clientId,
            "response_type" to "code",
            "redirect_uri" to id.redirectUri,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "token_access_type" to "offline",
            "scope" to "files.metadata.read files.content.read",
            "state" to state,
        )
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            // Уход в браузер не должен закрывать приложение блокировкой (как и системное окно выбора папки)
            AppLock.skipNextLock()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Вызывается из MainActivity для каждого входящего Intent. true, если это возврат из браузера после входа. */
    fun handleRedirect(ctx: Context, intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        val scheme = data.scheme ?: return false
        val id = CloudId.entries.firstOrNull { it.configured && scheme == it.redirectScheme } ?: return false
        intent.data = null // повторный вызов (поворот экрана) не должен обрабатывать тот же адрес второй раз

        val app = ctx.applicationContext
        val p = prefs(app)
        val verifier = p.getString("pkce_verifier", null)
        val state = p.getString("pkce_state", null)
        val pending = p.getString("pkce_provider", null)
        p.edit().remove("pkce_verifier").remove("pkce_state").remove("pkce_provider").apply()

        val code = data.getQueryParameter("code")
        if (code == null || verifier == null || state == null || pending != id.name ||
            data.getQueryParameter("state") != state
        ) {
            val reason = data.getQueryParameter("error_description") ?: data.getQueryParameter("error") ?: "no code"
            CloudSync.notice = CloudNotice(CloudNotice.Kind.CONNECT_FAILED, id, reason)
            return true
        }
        CloudSync.scope.launch {
            try {
                val json = JSONObject(
                    Http.text(
                        Http.request(
                            url = tokenUrl(id),
                            method = "POST",
                            body = form(
                                "grant_type" to "authorization_code",
                                "code" to code,
                                "code_verifier" to verifier,
                                "client_id" to id.clientId,
                                "redirect_uri" to id.redirectUri,
                            ),
                            contentType = FORM,
                        ),
                    ),
                )
                val refresh = json.optString("refresh_token")
                if (refresh.isEmpty()) throw CloudException("no refresh token")
                p.edit().putString("rt_${id.name}", Vault.seal(refresh)).apply()
                synchronized(this@CloudAuth) {
                    access[id] = json.getString("access_token") to
                        (System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
                }
                syncState(app)
            } catch (e: Exception) {
                CloudSync.notice = CloudNotice(CloudNotice.Kind.CONNECT_FAILED, id, e.message ?: e.toString())
            }
        }
        return true
    }

    private fun refreshWith(id: CloudId, refreshToken: String): JSONObject = JSONObject(
        Http.text(
            Http.request(
                url = tokenUrl(id),
                method = "POST",
                body = form(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to id.clientId,
                ),
                contentType = FORM,
            ),
        ),
    )

    /** Действующий access token; при необходимости обновляется по refresh token. Вызывать не из главного потока. */
    fun accessToken(ctx: Context, id: CloudId, force: Boolean = false): String = synchronized(this) {
        val cached = access[id]
        if (!force && cached != null && cached.second - 60_000L > System.currentTimeMillis()) {
            return@synchronized cached.first
        }
        val refresh = readRefresh(ctx, id) ?: throw CloudException("Not connected")
        val json = try {
            refreshWith(id, refresh)
        } catch (e: CloudException) {
            // Доступ отозван или токен недействителен: считаем, что вход потерян
            if (e.code == 400 || e.code == 401) disconnect(ctx, id)
            throw e
        }
        val token = json.getString("access_token")
        access[id] = token to (System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
        token
    }

    /** Выполняет [block] с токеном; при ответе 401 один раз обновляет токен и повторяет. */
    fun <T> withToken(ctx: Context, id: CloudId, block: (String) -> T): T {
        try {
            return block(accessToken(ctx, id))
        } catch (e: CloudException) {
            if (e.code != 401) throw e
        }
        return block(accessToken(ctx, id, force = true))
    }

    /** Забывает вход на устройстве и по возможности отзывает доступ на стороне сервиса. */
    fun disconnect(ctx: Context, id: CloudId) {
        val app = ctx.applicationContext
        val refresh = readRefresh(app, id)
        prefs(app).edit()
            .remove("rt_${id.name}").remove("folder_id_${id.name}").remove("folder_name_${id.name}")
            .apply()
        synchronized(this) { access.remove(id) }
        syncState(app)
        if (refresh != null) {
            CloudSync.scope.launch {
                runCatching {
                    val token = refreshWith(id, refresh).getString("access_token")
                    Http.text(
                        Http.request(
                            url = "https://api.dropboxapi.com/2/auth/token/revoke",
                            method = "POST",
                            headers = mapOf("Authorization" to "Bearer $token"),
                            body = ByteArray(0),
                            contentType = "",
                        ),
                    )
                }
            }
        }
    }
}

// =====================================================================
// Dropbox
// =====================================================================

private class DropboxProvider(private val ctx: Context) : CloudProvider {

    private fun rpc(path: String, body: JSONObject): JSONObject = CloudAuth.withToken(ctx, CloudId.DROPBOX) { token ->
        JSONObject(
            Http.text(
                Http.request(
                    url = "https://api.dropboxapi.com/2/$path",
                    method = "POST",
                    headers = mapOf("Authorization" to "Bearer $token"),
                    body = body.toString().toByteArray(Charsets.UTF_8),
                    contentType = "application/json; charset=utf-8",
                ),
            ),
        )
    }

    private fun listAll(first: JSONObject, onEntry: (JSONObject) -> Unit) {
        var page = rpc("files/list_folder", first)
        while (true) {
            val entries = page.getJSONArray("entries")
            for (i in 0 until entries.length()) onEntry(entries.getJSONObject(i))
            if (!page.optBoolean("has_more")) break
            page = rpc("files/list_folder/continue", JSONObject().put("cursor", page.getString("cursor")))
        }
    }

    override fun listFolders(parent: String): List<CloudFolder> {
        val out = ArrayList<CloudFolder>()
        listAll(JSONObject().put("path", parent).put("recursive", false).put("limit", 2000)) { e ->
            if (e.optString(".tag") == "folder") out += CloudFolder(e.getString("path_display"), e.getString("name"))
        }
        return out.sortedBy { it.name.lowercase() }
    }

    override fun listFiles(root: String): List<CloudFile> {
        val skip = root.split('/').count { it.isNotEmpty() }
        val out = ArrayList<CloudFile>()
        listAll(JSONObject().put("path", root).put("recursive", true).put("limit", 2000)) { e ->
            if (e.optString(".tag") == "file") {
                val segs = e.getString("path_display").split('/').filter { it.isNotEmpty() }.drop(skip)
                if (segs.isNotEmpty()) out += CloudFile(e.getString("id"), segs, e.optLong("size", -1L))
            }
        }
        return out
    }

    override fun open(file: CloudFile): InputStream = CloudAuth.withToken(ctx, CloudId.DROPBOX) { token ->
        // Как у официального SDK: POST с пустым телом и пустым Content-Type, параметры в заголовке
        Http.stream(
            Http.request(
                url = "https://content.dropboxapi.com/2/files/download",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Dropbox-API-Arg" to JSONObject().put("path", file.ref).toString(),
                ),
                body = ByteArray(0),
                contentType = "",
            ),
        )
    }
}

// =====================================================================
// Запись на устройство (Storage Access Framework)
// =====================================================================

/** Имя файла или папки, допустимое на любой файловой системе. */
private fun safeName(s: String): String {
    var n = s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trimEnd('.')
    if (n.isEmpty()) n = "_"
    if (n.length > 180) {
        val ext = n.substringAfterLast('.', "")
        n = if (ext.length in 1..10) n.take(170) + "." + ext else n.take(180)
    }
    return n
}

/**
 * Отдельная папка облака ([rootName]) внутри выбранной пользователем папки с музыкой.
 * Структура вложенных папок в облаке повторяется. Ничего не удаляется.
 */
private class LocalMirror(ctx: Context, private val tree: Uri, private val rootName: String) {

    class Child(val docId: String, val size: Long, val dir: Boolean)

    class Target(val uri: Uri, val dirId: String, val name: String)

    private val cr = ctx.contentResolver
    private val treeRoot = DocumentsContract.getTreeDocumentId(tree)
    private val listing = HashMap<String, HashMap<String, Child>>()

    private fun children(docId: String): HashMap<String, Child> = listing.getOrPut(docId) {
        val m = HashMap<String, Child>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        try {
            cr.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val dir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    m[name] = Child(id, if (c.isNull(3)) Long.MAX_VALUE else c.getLong(3), dir)
                }
            }
        } catch (_: Exception) {
            // папки ещё нет или нет доступа: считаем пустой
        }
        m
    }

    /** Размер уже скачанного файла или null, если его нет. Папки не создаёт. */
    fun existingSize(rel: List<String>): Long? {
        var dir = children(treeRoot)[rootName]?.takeIf { it.dir }?.docId ?: return null
        for (i in 0 until rel.size - 1) {
            dir = children(dir)[rel[i]]?.takeIf { it.dir }?.docId ?: return null
        }
        val f = children(dir)[rel.last()] ?: return null
        return if (f.dir) null else f.size
    }

    private fun ensureDir(parent: String, name: String): String {
        val existing = children(parent)[name]
        if (existing != null && existing.dir) return existing.docId
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parent)
        val created = DocumentsContract.createDocument(cr, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IOException("Cannot create folder $name")
        val id = DocumentsContract.getDocumentId(created)
        children(parent)[name] = Child(id, 0L, true)
        return id
    }

    /** Готовит файл к записи: создаёт недостающие папки и сам файл (или берёт существующий, если [overwrite]). */
    fun target(rel: List<String>, overwrite: Boolean): Target {
        var dir = ensureDir(treeRoot, rootName)
        for (i in 0 until rel.size - 1) dir = ensureDir(dir, rel[i])
        val name = rel.last()
        val existing = children(dir)[name]
        if (overwrite && existing != null && !existing.dir) {
            return Target(DocumentsContract.buildDocumentUriUsingTree(tree, existing.docId), dir, name)
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, dir)
        val created = DocumentsContract.createDocument(cr, parentUri, mime, name)
            ?: throw IOException("Cannot create file $name")
        val id = DocumentsContract.getDocumentId(created)
        children(dir)[name] = Child(id, 0L, false)
        return Target(DocumentsContract.buildDocumentUriUsingTree(tree, id), dir, name)
    }

    fun openOut(t: Target, truncate: Boolean): OutputStream =
        cr.openOutputStream(t.uri, if (truncate) "wt" else "w") ?: throw IOException("Cannot open ${t.name}")

    /** Убирает недокачанный файл. */
    fun discard(t: Target) {
        runCatching { DocumentsContract.deleteDocument(cr, t.uri) }
        children(t.dirId).remove(t.name)
    }
}

// =====================================================================
// Результаты для пользователя
// =====================================================================

class CloudNotice(
    val kind: Kind,
    val provider: CloudId? = null,
    val detail: String = "",
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
) {
    enum class Kind { DONE, CANCELLED, NOTHING_NEW, EMPTY, ERROR, CONNECT_FAILED, NO_BROWSER, NEED_WRITE }

    fun title(ctx: Context): String {
        val c = AppLocale.localized(ctx)
        return when (kind) {
            Kind.DONE -> c.getString(R.string.cloud_done_title)
            Kind.CANCELLED -> c.getString(R.string.cloud_cancelled_title)
            else -> provider?.title ?: c.getString(R.string.cloud_title)
        }
    }

    fun message(ctx: Context): String {
        val c = AppLocale.localized(ctx)
        return when (kind) {
            Kind.DONE, Kind.CANCELLED -> c.getString(R.string.cloud_result, downloaded, skipped, failed)
            Kind.NOTHING_NEW -> c.getString(R.string.cloud_nothing_new)
            Kind.EMPTY -> c.getString(R.string.cloud_empty)
            Kind.ERROR -> c.getString(R.string.cloud_error, detail)
            Kind.CONNECT_FAILED -> c.getString(R.string.cloud_connect_failed, detail)
            Kind.NO_BROWSER -> c.getString(R.string.cloud_no_browser)
            Kind.NEED_WRITE -> c.getString(R.string.cloud_need_write)
        }
    }
}

// =====================================================================
// Загрузка
// =====================================================================

object CloudSync {

    enum class Phase { IDLE, LISTING, CONFIRM, DOWNLOADING }

    class PlanItem(val file: CloudFile, val rel: List<String>, val overwrite: Boolean)

    class Plan(val provider: CloudId, val tree: Uri, val items: List<PlanItem>, val skipped: Int, val bytes: Long)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    var phase by mutableStateOf(Phase.IDLE)
        private set
    val busy: Boolean get() = phase != Phase.IDLE

    var provider by mutableStateOf<CloudId?>(null)
        private set
    var plan by mutableStateOf<Plan?>(null)
        private set
    var filesDone by mutableIntStateOf(0)
        private set
    var filesTotal by mutableIntStateOf(0)
        private set
    var current by mutableStateOf("")
        private set

    /** Что показать пользователю (диалог). Сбрасывается интерфейсом. */
    var notice by mutableStateOf<CloudNotice?>(null)

    /** Приложение сейчас на экране: по окончании загрузки отдельное уведомление не нужно. */
    @Volatile
    var appVisible = false

    /** Вызывается после загрузки хотя бы одного файла: плеер пересканирует папку. */
    @Volatile
    var onDownloaded: (() -> Unit)? = null

    private fun keyOf(path: List<String>): String =
        (path.dropLast(1) + path.last().substringBeforeLast('.')).joinToString("/").lowercase()

    /**
     * Шаг 1. Смотрит, что есть в облачной папке и чего ещё нет на устройстве. Ничего не скачивает:
     * результат ([plan]) пользователь подтверждает в диалоге.
     */
    fun prepare(ctx: Context, tree: Uri, id: CloudId) {
        if (busy) return
        val app = ctx.applicationContext
        val canWrite = app.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!canWrite) {
            notice = CloudNotice(CloudNotice.Kind.NEED_WRITE, id)
            return
        }
        val pick = CloudAuth.folders[id] ?: CloudPick("", "")
        provider = id
        plan = null
        phase = Phase.LISTING
        job = scope.launch {
            var next = Phase.IDLE
            try {
                val remote = CloudProviders.create(app, id).listFiles(pick.id)
                ensureActive()
                val mirror = LocalMirror(app, tree, id.folderName)

                val audioKeys = HashSet<String>()
                for (f in remote) {
                    val n = f.path.last()
                    if (!n.startsWith(".") && n.substringAfterLast('.', "").lowercase() in AUDIO_EXT) audioKeys += keyOf(f.path)
                }
                val seen = HashSet<String>()
                val items = ArrayList<PlanItem>()
                var candidates = 0
                var skipped = 0
                var bytes = 0L
                for (f in remote.sortedBy { it.path.joinToString("/").lowercase() }) {
                    ensureActive()
                    val name = f.path.last()
                    if (name.startsWith(".")) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    // Аудио, тексты .lrc и .txt с тем же именем, что у трека (как их ищет Library)
                    val wanted = ext in AUDIO_EXT || ext == "lrc" || (ext == "txt" && keyOf(f.path) in audioKeys)
                    if (!wanted) continue
                    val rel = f.path.map { safeName(it) }
                    if (!seen.add(rel.joinToString("/").lowercase())) continue
                    candidates++
                    val have = mirror.existingSize(rel)
                    // Уже есть и не меньше облачного: пропускаем. Меньше: похоже на недокачанный файл, качаем заново.
                    if (have != null && (f.size < 0 || have >= f.size)) {
                        skipped++
                        continue
                    }
                    items += PlanItem(f, rel, overwrite = have != null)
                    if (f.size > 0) bytes += f.size
                }
                when {
                    candidates == 0 -> notice = CloudNotice(CloudNotice.Kind.EMPTY, id)
                    items.isEmpty() -> notice = CloudNotice(CloudNotice.Kind.NOTHING_NEW, id, skipped = skipped)
                    else -> {
                        plan = Plan(id, tree, items, skipped, bytes)
                        next = Phase.CONFIRM
                    }
                }
            } catch (e: CancellationException) {
                // отмена пользователем
            } catch (e: Exception) {
                notice = CloudNotice(CloudNotice.Kind.ERROR, id, e.message ?: e.toString())
            } finally {
                phase = next
                if (next == Phase.IDLE) provider = null
            }
        }
    }

    /** Пользователь отказался от скачивания после проверки. */
    fun discard() {
        if (phase != Phase.CONFIRM) return
        plan = null
        phase = Phase.IDLE
        provider = null
    }

    /** Шаг 2. Пользователь подтвердил: скачивание идёт в сервисе переднего плана, чтобы не прерываться в фоне. */
    fun confirm(ctx: Context) {
        if (phase == Phase.CONFIRM && plan != null) CloudSyncService.start(ctx)
    }

    fun cancel() {
        if (phase == Phase.CONFIRM) discard() else job?.cancel()
    }

    /** Вызывается сервисом. false, если скачивать нечего или оно уже идёт. */
    fun download(ctx: Context, onProgress: () -> Unit, onEnd: () -> Unit): Boolean {
        val p = plan ?: return false
        if (phase != Phase.CONFIRM) return false
        val app = ctx.applicationContext
        plan = null
        provider = p.provider
        filesDone = 0
        filesTotal = p.items.size
        current = ""
        phase = Phase.DOWNLOADING
        job = scope.launch {
            var ok = 0
            var failed = 0
            var lastError = ""
            try {
                val prov = CloudProviders.create(app, p.provider)
                val mirror = LocalMirror(app, p.tree, p.provider.folderName)
                for (item in p.items) {
                    ensureActive()
                    current = item.rel.last()
                    onProgress()
                    try {
                        downloadOne(prov, mirror, item)
                        ok++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        lastError = e.message ?: e.toString()
                    }
                    filesDone++
                }
                notice = if (ok == 0 && failed > 0) {
                    CloudNotice(CloudNotice.Kind.ERROR, p.provider, lastError)
                } else {
                    CloudNotice(CloudNotice.Kind.DONE, p.provider, "", ok, p.skipped, failed)
                }
            } catch (e: CancellationException) {
                notice = CloudNotice(CloudNotice.Kind.CANCELLED, p.provider, "", ok, p.skipped, failed)
            } catch (e: Exception) {
                notice = CloudNotice(CloudNotice.Kind.ERROR, p.provider, e.message ?: e.toString())
            } finally {
                current = ""
                phase = Phase.IDLE
                provider = null
                onEnd()
                if (ok > 0) onDownloaded?.invoke()
            }
        }
        return true
    }

    /** Один файл: до трёх попыток; недокачанный файл удаляется. */
    private suspend fun downloadOne(prov: CloudProvider, mirror: LocalMirror, item: PlanItem) {
        var attempt = 0
        while (true) {
            attempt++
            try {
                val target = mirror.target(item.rel, item.overwrite)
                try {
                    prov.open(item.file).use { input ->
                        mirror.openOut(target, item.overwrite).use { out -> copy(input, out) }
                    }
                    return
                } catch (e: Exception) {
                    mirror.discard(target)
                    throw e
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val permanent = e is CloudException && e.code in 400..499 && e.code != 408 && e.code != 429
                if (attempt >= 3 || permanent) throw e
                delay(1500L * attempt)
            }
        }
    }

    private suspend fun copy(input: InputStream, out: OutputStream) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            currentCoroutineContext().ensureActive()
        }
        out.flush()
    }
}
