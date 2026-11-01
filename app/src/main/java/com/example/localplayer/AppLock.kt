package com.example.localplayer

import android.content.Context
import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Вид блокировки приложения. [NONE] — блокировка выключена (по умолчанию). */
enum class LockType(@StringRes val label: Int, val length: Int) {
    NONE(R.string.lock_sub_off, 0),
    PIN4(R.string.lock_type_pin4, 4),
    PIN6(R.string.lock_type_pin6, 6),
    CUSTOM(R.string.lock_type_custom, 0);

    /** Код из цифр фиксированной длины (вводится своей клавиатурой). */
    val isPin: Boolean get() = length > 0
}

/** Что сейчас показано поверх приложения: создание кода или подтверждение кодом перед отключением блокировки. */
sealed interface LockFlow {
    data class Setup(val type: LockType) : LockFlow
    data object Disable : LockFlow

    /** Смена кода: сначала текущий код, затем выбор вида и создание нового (с новым recovery-файлом). */
    data object Change : LockFlow
}

/** Итог попытки ввода кода. [BLOCKED] — включена пауза после серии ошибок, код даже не проверялся. */
enum class Attempt { OK, WRONG, BLOCKED }

/**
 * Опциональная блокировка приложения кодом.
 *
 *  - Сам код и recovery-код нигде не хранятся: только соль и PBKDF2-хэш.
 *  - [locked] — читается в Compose; живёт, пока жив процесс. После смерти процесса приложение снова стартует заблокированным.
 *  - Recovery-код — 12 случайных слов. Они записываются в .txt, который пользователь сам сохраняет на телефоне;
 *    на экране блокировки этот файл выбирается через «Забыл пароль» и снимает блокировку.
 */
object AppLock {

    const val CUSTOM_MIN = 4
    const val CUSTOM_MAX = 64
    const val RECOVERY_WORDS = 12

    private const val PREFS = "app_lock"
    private const val K_TYPE = "type"
    private const val K_SALT = "salt"
    private const val K_HASH = "hash"
    private const val K_R_SALT = "r_salt"
    private const val K_R_HASH = "r_hash"
    private const val K_FAILS = "fails"
    private const val K_UNTIL = "blocked_until"
    private const val ITERATIONS = 30_000

    /** Защита от перебора: после каждых [FREE_ATTEMPTS] подряд неверных кодов пауза 30 с, дальше вдвое дольше, но не более 15 минут. */
    private const val FREE_ATTEMPTS = 5
    private const val BASE_DELAY_MS = 30_000L
    private const val MAX_DELAY_MS = 15 * 60_000L

    private val typeState = mutableStateOf(LockType.NONE)
    private val lockedState = mutableStateOf(false)
    private var initialized = false

    @Volatile
    private var skipNext = false

    /** Текущий вид блокировки (читается в Compose). */
    val type: LockType get() = typeState.value

    /** Приложение сейчас закрыто экраном блокировки (читается в Compose). */
    val locked: Boolean get() = lockedState.value

    /** Незавершённое создание кода / отключение (читается в Compose). */
    var flow by mutableStateOf<LockFlow?>(null)
        private set

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Вызывается из onCreate. Первый вызов за жизнь процесса (холодный старт) закрывает приложение, если блокировка включена. */
    fun init(ctx: Context) {
        if (initialized) return
        initialized = true
        val saved = runCatching { LockType.valueOf(prefs(ctx).getString(K_TYPE, null) ?: "NONE") }.getOrDefault(LockType.NONE)
        val valid = saved != LockType.NONE && prefs(ctx).getString(K_HASH, null) != null
        typeState.value = if (valid) saved else LockType.NONE
        lockedState.value = valid
    }

    /** Закрывает приложение (уход в фон). Если блокировка выключена, ничего не делает. */
    fun lockNow() {
        if (typeState.value != LockType.NONE) {
            lockedState.value = true
            flow = null
        }
    }

    /**
     * Следующий уход в фон не блокирует приложение. Нужно перед системными окнами выбора (папка с музыкой):
     * на время такого окна наша активность уходит в onStop.
     */
    fun skipNextLock() {
        skipNext = true
    }

    fun consumeSkip(): Boolean {
        val s = skipNext
        skipNext = false
        return s
    }

    fun unlock() {
        lockedState.value = false
    }

    // ---------- Сценарии поверх приложения ----------

    fun startSetup(type: LockType) {
        if (type != LockType.NONE) flow = LockFlow.Setup(type)
    }

    fun startDisable() {
        if (typeState.value != LockType.NONE) flow = LockFlow.Disable
    }

    fun startChange() {
        if (typeState.value != LockType.NONE) flow = LockFlow.Change
    }

    fun endFlow() {
        flow = null
    }

    // ---------- Хранение и проверка ----------

    /** Включает блокировку. Вызывать только когда recovery-файл уже сохранён. */
    suspend fun enable(ctx: Context, type: LockType, code: String, recoveryWords: List<String>) {
        val codeSalt = randomBytes(16)
        val codeHash = withContext(Dispatchers.Default) { hash(code, codeSalt) }
        val recSalt = randomBytes(16)
        val recHash = withContext(Dispatchers.Default) { hash(normalizeRecovery(recoveryWords.joinToString(" ")), recSalt) }
        prefs(ctx).edit()
            .putString(K_TYPE, type.name)
            .putString(K_SALT, b64(codeSalt)).putString(K_HASH, b64(codeHash))
            .putString(K_R_SALT, b64(recSalt)).putString(K_R_HASH, b64(recHash))
            .remove(K_FAILS).remove(K_UNTIL)
            .apply()
        typeState.value = type
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        typeState.value = LockType.NONE
        lockedState.value = false
    }

    /** Сколько ещё миллисекунд действует пауза после неверных попыток (0 — можно вводить). Пауза переживает перезапуск приложения. */
    fun lockoutLeftMs(ctx: Context): Long {
        val until = prefs(ctx).getLong(K_UNTIL, 0L)
        // coerceAtMost: если часы перевели назад, пауза всё равно не дольше максимальной
        return (until - System.currentTimeMillis()).coerceIn(0L, MAX_DELAY_MS)
    }

    /** Проверка кода с учётом защиты от перебора. */
    suspend fun attempt(ctx: Context, code: String): Attempt {
        if (lockoutLeftMs(ctx) > 0) return Attempt.BLOCKED
        val p = prefs(ctx)
        if (matches(ctx, K_SALT, K_HASH, code)) {
            p.edit().remove(K_FAILS).remove(K_UNTIL).apply()
            return Attempt.OK
        }
        val fails = p.getInt(K_FAILS, 0) + 1
        val e = p.edit().putInt(K_FAILS, fails)
        if (fails % FREE_ATTEMPTS == 0) {
            val step = (fails / FREE_ATTEMPTS - 1).coerceAtMost(10)
            val delay = minOf(BASE_DELAY_MS shl step, MAX_DELAY_MS)
            e.putLong(K_UNTIL, System.currentTimeMillis() + delay)
        }
        e.apply()
        return Attempt.WRONG
    }

    /** [fileText] — содержимое выбранного recovery-файла. */
    suspend fun verifyRecovery(ctx: Context, fileText: String): Boolean {
        val normalized = normalizeRecovery(fileText)
        // Длину не сверяем жёстко: файлы, созданные раньше (6 слов), тоже должны подходить; верность решает хэш
        if (normalized.isEmpty()) return false
        return matches(ctx, K_R_SALT, K_R_HASH, normalized)
    }

    /** Новые 12 случайных слов для recovery-файла. */
    fun newRecoveryWords(): List<String> {
        // Слова в файле не повторяются
        return WORDS.toList().shuffled(SecureRandom()).take(RECOVERY_WORDS)
    }

    /** Содержимое recovery-файла. */
    fun recoveryFileText(words: List<String>): String = words.joinToString(" ") + "\n"

    private suspend fun matches(ctx: Context, saltKey: String, hashKey: String, secret: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString(saltKey, null)?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() } ?: return false
        val expected = p.getString(hashKey, null)?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() } ?: return false
        val actual = withContext(Dispatchers.Default) { hash(secret, salt) }
        return MessageDigest.isEqual(expected, actual)
    }

    private fun normalizeRecovery(text: String): String =
        text.replace("\uFEFF", "").lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    private fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    /** Допустимый символ своего кода: латинская буква или цифра. */
    fun isAllowedChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    private val WORDS = arrayOf(
        "able", "acid", "acorn", "actor", "agent", "alarm", "album", "alert", "alley", "amber", "anchor",
        "angle", "apple", "april", "arrow", "atlas", "attic", "autumn", "avenue", "bacon", "badge", "baker",
        "banjo", "barn", "basil", "basket", "beach", "beacon", "berry", "bicycle", "birch", "bison", "blade",
        "blanket", "blast", "bliss", "bloom", "boat", "bonus", "border", "bottle", "brave", "bread",
        "breeze", "brick", "bridge", "bright", "bronze", "brook", "brush", "bubble", "bucket", "buddy",
        "cabin", "cable", "cactus", "camel", "candle", "canyon", "carbon", "cargo", "carpet", "castle",
        "cedar", "cellar", "chair", "chalk", "charm", "cherry", "chess", "chief", "cinder", "circle",
        "citrus", "clay", "clever", "cliff", "clock", "cloud", "clover", "cobalt", "coffee", "comet",
        "copper", "coral", "cotton", "cousin", "crane", "crayon", "cricket", "crown", "crystal", "curtain",
        "cycle", "daisy", "dancer", "dawn", "delta", "desert", "diamond", "dinner", "dolphin", "donkey",
        "dragon", "drift", "drum", "eagle", "earth", "echo", "effort", "ember", "engine", "entry", "falcon",
        "famous", "feather", "fence", "ferry", "fiber", "field", "finger", "flame", "flint", "flower",
        "forest", "fossil", "fox", "frame", "frost", "galaxy", "garden", "garlic", "gentle", "ginger",
        "glacier", "globe", "golden", "goose", "granite", "grape", "gravel", "guitar", "hammer", "harbor",
        "harvest", "hazel", "heart", "helmet", "heron", "hockey", "honey", "horizon", "hunter", "island",
        "ivory", "jacket", "jaguar", "jasmine", "jelly", "jungle", "kettle", "kitten", "ladder", "lagoon",
        "lantern", "laser", "lemon", "lesson", "library", "light", "lily", "lizard", "lobster", "locket",
        "lunar", "magnet", "mango", "maple", "marble", "meadow", "melon", "mirror", "monkey", "moose",
        "mosaic", "mountain", "muffin", "nectar", "needle", "noble", "north", "nutmeg", "oasis", "ocean",
        "olive", "onion", "orange", "orbit", "orchid", "otter", "oyster", "paddle", "palace", "panda",
        "paper", "parrot", "peach", "pearl", "pebble", "pepper", "piano", "pigeon", "pillow", "pilot",
        "planet", "plum", "pocket", "pond", "poster", "potato", "prairie", "pumpkin", "puzzle", "quartz",
        "rabbit", "radar", "rainbow", "raven", "river", "rocket", "saddle", "salmon", "sandal", "scarlet",
        "shadow", "shell", "silver", "sketch", "slate", "sleeve", "smile", "snow", "spark", "spider",
        "spirit", "spoon", "spring", "square", "squirrel", "stable", "star", "stone", "storm", "sugar",
        "summer", "sunset", "swan", "table", "temple", "tennis", "thunder", "ticket", "tiger", "timber",
        "tomato", "torch", "tower", "trail", "tulip", "tunnel", "turtle", "umbrella", "valley", "velvet",
        "violet", "voyage", "walnut", "wander", "water", "whale", "willow", "window", "winter", "wizard",
        "wolf", "yacht", "yellow", "zebra", "zephyr", "abbey", "acre", "adobe", "almond", "aloe", "anvil",
        "apron", "aqua", "arch", "aspen", "aster", "avocado", "azure", "badger", "bagel", "bamboo", "banner",
        "barley", "barrel", "beam", "beetle", "bell", "biscuit", "blossom", "bluebird", "bolt", "boulder",
        "bowl", "branch", "brass", "broom", "buffalo", "butter", "button", "cabbage", "cake", "canal",
        "candy", "canoe", "canvas", "cape", "caramel", "carrot", "cashew", "cave", "celery", "cello",
        "chapel", "cheese", "chestnut", "chime", "chimney", "cider", "cinnamon", "clam", "clove", "coast",
        "cocoa", "coconut", "compass", "cookie", "cork", "cove", "coyote", "crab", "cradle", "crater",
        "creek", "crest", "crimson", "crisp", "crow", "cube", "cuckoo", "cupcake", "currant", "dandelion",
        "deer", "dew", "dingo", "dock", "dome", "dove", "dune", "dusk", "ebony", "elm", "emerald", "fable",
        "fabric", "fawn", "fern", "festival", "finch", "fjord", "flax", "fleece", "flute", "fog", "fork",
        "fountain", "freckle", "fudge", "gadget", "gale", "gazelle", "gecko", "geyser", "gift", "giraffe",
        "glade", "glider", "goblet", "gopher", "gourd", "grain", "grove", "gull", "gumdrop", "hamlet",
        "hammock", "hare", "hawk", "hedge", "hickory", "hill", "hollow", "hornet", "hummus", "icicle",
        "igloo", "indigo", "iris", "jade", "jam", "jar", "jester", "jewel", "jigsaw", "jolly", "juniper",
        "kayak", "kernel", "kiwi", "knight", "koala", "lace", "lamb", "lark", "latte", "lava", "lawn",
        "leaf", "ledger", "lemur", "lentil", "lilac", "linen", "lime", "llama", "locust", "lotus", "lucky",
        "lumber", "lynx", "magpie", "mallet", "mammoth", "mantle", "marsh", "mason", "meteor", "millet",
        "mint", "mist", "mitten", "mocha", "moss", "moth", "mule", "mustard", "nacho", "nest", "nickel",
        "nimbus", "noodle", "oak", "oat", "obsidian", "octopus", "omega", "opal", "orca", "osprey", "owl",
        "paddock", "pansy", "papaya", "parsley", "pasta", "patch", "peacock", "peanut", "pelican", "pickle",
        "pine", "pistachio", "pixel", "plank", "plateau", "plover", "polar", "pollen", "poppy", "porch",
        "puffin", "pulse", "quail", "quill", "quince", "quiver", "raccoon", "radish", "raft", "rain",
        "ranger", "reef", "ribbon", "ridge", "robin", "rope", "rose", "rowan", "ruby", "rust", "saffron",
        "sage", "sailor", "salsa", "sand", "sapphire", "satin", "sawmill", "scone", "seagull", "sequoia",
        "sesame", "shamrock", "shark", "sheep", "shore", "shrimp", "sierra", "skate", "skylark", "sloth",
        "snail", "sock", "sorbet", "sparrow", "spice", "sponge", "sprout", "spruce", "stag", "starfish",
        "steam", "stork", "straw", "stream", "sundial", "sunrise", "swallow", "swift", "syrup", "tango",
        "tapestry", "teal", "thistle", "thorn", "thyme", "toast", "toffee", "topaz", "trout", "trumpet",
        "tuba", "tundra", "twig", "urchin", "vanilla", "vase", "vine", "violin", "vista", "waffle", "wagon",
        "walrus", "wasp", "wave", "weasel", "wheat", "whisper", "wicker", "wildcat", "windmill", "wren",
        "yarn", "yodel", "yoga", "zenith", "zest", "zinc", "zucchini", "aurora", "basalt", "bishop",
        "blizzard", "bonfire", "bramble", "breezy", "brisk", "cairn", "cobweb", "cosmos", "crescent",
        "cypress", "dagger", "dapper", "dazzle", "dolmen", "dynamo", "eclipse", "ferret", "fiddle",
        "firefly", "fizz", "flicker", "gadfly", "garnet", "gleam", "glimmer", "gondola", "granola", "harp",
        "haven", "hazelnut", "helium", "herald", "hoopoe", "hush", "ignite", "inlet", "jovial", "karma",
        "kestrel", "kindle", "lattice", "legend", "lyric", "mariner", "marmot", "meringue", "mimic", "mirth",
        "monsoon", "mulberry", "nectarine", "nomad", "oriole", "paprika", "parade", "pavilion", "pewter",
        "pinecone", "plume", "pumice", "quasar", "quiet", "rhubarb", "riddle", "rustic", "sailboat",
        "sandbar", "scarab", "seashell", "serene", "shimmer", "sickle", "skiff", "solstice", "sparkle",
        "spindle", "stellar", "sturdy", "sundown", "tambourine", "tidal", "tinsel", "trellis", "twilight",
        "umber", "unicorn", "upland", "velour", "verdant", "vortex", "wharf", "whistle", "wisp", "yonder",
    )
}
