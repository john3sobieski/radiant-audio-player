package com.example.localplayer

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Символ, которым скрыт вводимый код. */
private const val MASK_CHAR = '*'

private const val RECOVERY_FILE_NAME = "recovery-code.txt"

// =====================================================================
// Экран блокировки
// =====================================================================

/**
 * Экран блокировки: чисто белый (светлая тема) или чисто чёрный (тёмная) фон и поле ввода.
 * Для кода из цифр — своя минималистичная клавиатура, для своего кода — системная.
 * Под полем «Забыл пароль»: выбор recovery-файла, который сохранён при создании кода.
 */
@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val type = remember { AppLock.type }
    val check = rememberCodeCheck { AppLock.unlock() }
    var recoveryBusy by remember { mutableStateOf(false) }

    val recoveryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                recoveryBusy = true
                val text = withContext(Dispatchers.IO) { readSmallText(ctx, uri) }
                val ok = text != null && AppLock.verifyRecovery(ctx, text)
                recoveryBusy = false
                if (ok) {
                    AppLock.disable(ctx)
                    Toast.makeText(ctx, R.string.lock_recovery_ok, Toast.LENGTH_LONG).show()
                } else {
                    check.message = R.string.lock_recovery_wrong
                    check.resetKey++
                }
            }
        }
    }

    LockTheme {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CodeInput(
                    type = type,
                    resetKey = check.resetKey,
                    busy = check.disabled || recoveryBusy,
                    error = check.errorText(),
                    onEdit = { check.edit() },
                    onSubmit = { check.submit(it) },
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    footer = {
                        // «Забыл пароль» доступно и во время паузы после неверных попыток
                        TextButton(
                            onClick = { recoveryPicker.launch(arrayOf("text/plain", "application/octet-stream")) },
                            enabled = !check.busy && !recoveryBusy,
                        ) { Text(stringResource(R.string.lock_forgot)) }
                    },
                )
            }
        }
    }
}

/**
 * Проверка кода вместе с защитой от перебора: состояние ввода, пауза после серии ошибок и её обратный отсчёт.
 * [onSuccess] вызывается, когда код верный.
 */
@Stable
internal class CodeCheck(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onSuccess: () -> Unit,
) {
    var resetKey by mutableIntStateOf(0)
    var message by mutableStateOf<Int?>(null)
    var busy by mutableStateOf(false)
    var leftMs by mutableLongStateOf(AppLock.lockoutLeftMs(ctx))

    /** Ввод закрыт: идёт проверка или действует пауза. */
    val disabled: Boolean get() = busy || leftMs > 0

    fun edit() {
        message = null
    }

    fun refreshLockout() {
        leftMs = AppLock.lockoutLeftMs(ctx)
    }

    fun submit(code: String) {
        if (disabled) return
        scope.launch {
            busy = true
            when (AppLock.attempt(ctx, code)) {
                Attempt.OK -> onSuccess()
                Attempt.WRONG -> {
                    message = R.string.lock_wrong
                    resetKey++
                }
                Attempt.BLOCKED -> resetKey++
            }
            refreshLockout()
            busy = false
        }
    }
}

@Composable
internal fun rememberCodeCheck(onSuccess: () -> Unit): CodeCheck {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val check = remember { CodeCheck(ctx, scope, onSuccess) }
    val blocked = check.leftMs > 0
    // Пока идёт пауза, раз в полсекунды обновляем остаток; когда он дошёл до нуля, отсчёт сам останавливается
    LaunchedEffect(blocked) {
        while (check.leftMs > 0) {
            delay(500)
            check.refreshLockout()
        }
    }
    return check
}

/** Текст под полем: во время паузы остаток времени, иначе «Неверный код» и подобное. */
@Composable
internal fun CodeCheck.errorText(): String? {
    if (leftMs > 0) {
        val seconds = ceil(leftMs / 1000.0).toLong()
        return stringResource(R.string.lock_wait, "%d:%02d".format(seconds / 60, seconds % 60))
    }
    return message?.let { stringResource(it) }
}

/** Белая или чёрная схема под текущую тему: фон экрана блокировки всегда чистый, без оттенков. */
@Composable
private fun LockTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = base.background.luminance() < 0.5f
    val bg = if (dark) Color.Black else Color.White
    val fg = if (dark) Color.White else Color.Black
    MaterialTheme(
        colorScheme = base.copy(
            background = bg,
            surface = bg,
            surfaceTint = bg,
            onBackground = fg,
            onSurface = fg,
            onSurfaceVariant = fg.copy(alpha = 0.65f),
            primary = fg,
            onPrimary = bg,
            outline = fg.copy(alpha = 0.4f),
            outlineVariant = fg.copy(alpha = 0.2f),
        ),
        content = content,
    )
}

/** Читает небольшой текстовый файл (recovery-код — одна строка). Слишком большие файлы отбрасываются. */
private fun readSmallText(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(512)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > 4096) return@runCatching null
            out.write(buf, 0, n)
        }
        out.toString(Charsets.UTF_8.name())
    }
}.getOrNull()

// =====================================================================
// Ввод кода (общий для экрана блокировки, создания кода и отключения)
// =====================================================================

/**
 * Поле ввода кода.
 *  - Код из цифр ([LockType.isPin]): поле с кнопкой-глазом и своя цифровая клавиатура; код уходит в [onSubmit] сам,
 *    как только набрана последняя цифра.
 *  - Свой код: только поле (латиница и цифры, без пробелов) и системная клавиатура; код уходит по кнопке «Готово».
 *
 * Символы в поле появляются с анимацией. Изменение [resetKey] очищает поле и трясёт его (неверный код).
 * Ввод очищает ошибку через [onEdit].
 */
@Composable
internal fun CodeInput(
    type: LockType,
    resetKey: Int,
    busy: Boolean,
    error: String?,
    onEdit: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    var code by remember(resetKey) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    val startKey = remember { resetKey }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(resetKey) {
        if (resetKey != startKey && !PowerSave.reduceMotion) {
            for (x in floatArrayOf(-18f, 18f, -12f, 12f, -6f, 0f)) shake.animateTo(x, tween(45))
        }
    }
    val fieldModifier = Modifier
        .fillMaxWidth()
        .offset { IntOffset(shake.value.dp.roundToPx(), 0) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (type.isPin) {
            PinField(code, visible, error != null, { visible = !visible }, fieldModifier)
        } else {
            CustomField(
                code = code,
                visible = visible,
                isError = error != null,
                onChange = { clean ->
                    if (!busy && clean != code) {
                        code = clean
                        onEdit()
                    }
                },
                onDone = { if (code.isNotEmpty() && !busy) onSubmit(code) },
                onToggle = { visible = !visible },
                modifier = fieldModifier,
            )
        }

        // Текст ошибки плавно проявляется и гаснет (последний текст запоминаем, чтобы он не пропадал раньше времени)
        val lastError = remember { mutableStateOf("") }
        if (error != null) lastError.value = error
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = error != null,
                enter = calmEnter(fadeIn(tween(180))),
                exit = calmExit(fadeOut(tween(180))),
            ) {
                Text(lastError.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        if (type.isPin) {
            Spacer(Modifier.height(8.dp))
            PinPad(
                enabled = !busy,
                onDigit = { d ->
                    if (code.length < type.length) {
                        val next = code + d
                        code = next
                        onEdit()
                        if (next.length == type.length) onSubmit(next)
                    }
                },
                onBackspace = {
                    if (code.isNotEmpty()) {
                        code = code.dropLast(1)
                        onEdit()
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        footer()
    }
}

@Composable
private fun EyeButton(visible: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Crossfade(targetState = visible, animationSpec = tween(if (PowerSave.reduceMotion) 0 else 180), label = "eye") { v ->
            Icon(
                if (v) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                stringResource(if (v) R.string.lock_hide_code else R.string.lock_show_code),
            )
        }
    }
}

/** Поле кода из цифр: звёздочки (или сам код, если нажат глаз) по центру, глаз справа. Символы появляются с анимацией. */
@Composable
private fun PinField(code: String, visible: Boolean, isError: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val borderColor by animateColorAsState(
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        mtween(150),
        label = "pinBorder",
    )
    Row(
        modifier
            .height(56.dp)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Пустышка такой же ширины, как кнопка глаза: символы остаются ровно по центру поля
        Spacer(Modifier.width(48.dp))
        AnimatedChars(
            code = code,
            visible = visible,
            style = MaterialTheme.typography.headlineSmall,
            centered = true,
            showCursor = false,
            modifier = Modifier.weight(1f),
        )
        EyeButton(visible, onToggle)
    }
}

/**
 * Поле для своего кода. Ввод принимает скрытый BasicTextField (он нужен для системной клавиатуры),
 * а символы рисуются поверх с анимацией и мигающим курсором. Рамка плавно меняет цвет при фокусе и ошибке.
 */
@Composable
private fun CustomField(
    code: String,
    visible: Boolean,
    isError: Boolean,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val scheme = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        when {
            isError -> scheme.error
            focused -> scheme.primary
            else -> scheme.outline
        },
        mtween(150),
        label = "fieldBorder",
    )
    val borderWidth by animateDpAsState(if (focused || isError) 2.dp else 1.dp, mtween(150), label = "fieldBorderWidth")
    val style = MaterialTheme.typography.titleLarge

    Row(
        modifier
            .height(56.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                // Курсор всегда в конце: символы в поле дописываются, а не вставляются в середину
                value = TextFieldValue(code, TextRange(code.length)),
                onValueChange = { v -> onChange(v.text.filter { AppLock.isAllowedChar(it) }.take(AppLock.CUSTOM_MAX)) },
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent, fontSize = 22.sp),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused },
            )
            AnimatedChars(
                code = code,
                visible = visible,
                style = style,
                centered = false,
                showCursor = focused,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        EyeButton(visible, onToggle)
    }
}

/**
 * Введённые символы: каждый новый «всплывает» (масштаб, прозрачность, небольшой сдвиг с пружиной).
 * При нажатии на глаз «*» и символ плавно перетекают друг в друга.
 */
@Composable
private fun AnimatedChars(
    code: String,
    visible: Boolean,
    style: TextStyle,
    centered: Boolean,
    showCursor: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    if (!centered) {
        // Длинный код прокручивается, чтобы конец (и курсор) всегда был виден
        LaunchedEffect(code.length) {
            delay(32)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }
    Row(
        // enabled = false: пальцем не прокручивается, касания достаются полю под ним
        if (centered) modifier else modifier.horizontalScroll(scroll, enabled = false),
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        code.forEachIndexed { index, c ->
            key(index) { CodeChar(if (visible) c else MASK_CHAR, style) }
        }
        if (showCursor) BlinkingCursor()
    }
}

@Composable
private fun CodeChar(ch: Char, style: TextStyle) {
    val reduce = PowerSave.reduceMotion
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 450f)) }
    AnimatedContent(
        targetState = ch,
        transitionSpec = {
            if (reduce) EnterTransition.None togetherWith ExitTransition.None
            else fadeIn(tween(160)) togetherWith fadeOut(tween(120))
        },
        modifier = Modifier.graphicsLayer {
            val v = enter.value
            alpha = v.coerceIn(0f, 1f)
            scaleX = 0.4f + 0.6f * v
            scaleY = 0.4f + 0.6f * v
            translationY = (1f - v) * 12.dp.toPx()
        },
        label = "codeChar",
    ) { c -> Text(c.toString(), style = style) }
}

@Composable
private fun BlinkingCursor() {
    val color = MaterialTheme.colorScheme.primary
    val blink = rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Box(
        Modifier
            .size(width = 2.dp, height = 24.dp)
            .graphicsLayer { alpha = if (PowerSave.reduceMotion) 1f else blink.value }
            .background(color),
    )
}

/** Минималистичная цифровая клавиатура: цифры без подложек, внизу ноль и «удалить». */
@Composable
private fun PinPad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { c -> DigitKey(c, enabled) { onDigit(c) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.size(PAD_KEY))
            DigitKey('0', enabled) { onDigit('0') }
            PadKey(enabled, onBackspace) {
                Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.lock_backspace))
            }
        }
    }
}

private val PAD_KEY = 72.dp

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    PadKey(enabled, onClick) {
        Text(digit.toString(), fontSize = 28.sp, fontWeight = FontWeight.Light)
    }
}

/**
 * Клавиша без стандартной «ряби»: при нажатии пружинно «вдавливается» и на миг подсвечивается мягким кругом,
 * после отпускания возвращается с лёгким отскоком.
 */
@Composable
private fun PadKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = PowerSave.reduceMotion
    val scaleSpec: AnimationSpec<Float> = if (reduce) snap() else spring(dampingRatio = 0.45f, stiffness = 700f)
    val glowSpec: AnimationSpec<Float> = if (reduce) snap() else tween(if (pressed) 50 else 320)
    val scale = animateFloatAsState(if (pressed) 0.82f else 1f, scaleSpec, label = "keyScale")
    val glow = animateFloatAsState(if (pressed) 0.16f else 0f, glowSpec, label = "keyGlow")
    val tint = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(PAD_KEY)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .drawBehind { drawCircle(tint.copy(alpha = glow.value)) }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// =====================================================================
// Включение: выбор вида блокировки, создание кода, recovery-файл
// =====================================================================

/** Окно выбора вида блокировки (появляется при включении). */
@Composable
internal fun LockTypeDialog(onSelect: (LockType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_type_title)) },
        text = {
            Column {
                LockTypeRow(R.string.lock_type_pin4, R.string.lock_type_digits_desc) { onSelect(LockType.PIN4) }
                LockTypeRow(R.string.lock_type_pin6, R.string.lock_type_digits_desc) { onSelect(LockType.PIN6) }
                LockTypeRow(R.string.lock_type_custom, R.string.lock_type_custom_desc) { onSelect(LockType.CUSTOM) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LockTypeRow(title: Int, subtitle: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = false, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Полноэкранный слой поверх приложения: создание кода или подтверждение кодом перед отключением. */
@Composable
fun LockFlowOverlay(flow: LockFlow) {
    BackHandler { AppLock.endFlow() }
    val enter = remember { Animatable(if (PowerSave.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) enter.animateTo(1f, tween(240))
    }
    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 40.dp.toPx()
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            when (flow) {
                is LockFlow.Setup -> SetupContent(flow.type)
                LockFlow.Disable -> DisableContent()
                LockFlow.Change -> ChangeContent()
            }
            IconButton(onClick = { AppLock.endFlow() }, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun FlowColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * Шаги: 0 — ввод кода, 1 — повтор, 2 — сохранение recovery-файла.
 * Блокировка включается только после того, как файл действительно записан: пока файла нет, ничего не меняется,
 * и закрытие экрана просто отменяет включение.
 */
@Composable
private fun SetupContent(type: LockType) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var words by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                            it.write(AppLock.recoveryFileText(words).toByteArray(Charsets.UTF_8))
                        }
                    }.isSuccess
                }
                if (written) {
                    AppLock.enable(ctx, type, first, words)
                    AppLock.endFlow()
                } else {
                    error = R.string.lock_recovery_failed
                }
                busy = false
            }
        }
    }

    FlowColumn {
      // Шаги плавно сменяют друг друга: вперёд — вправо-налево, назад (код не совпал) — наоборот
      AnimatedContent(
        targetState = step,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            if (PowerSave.reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (slideInHorizontally(tween(280)) { it / 3 * dir } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 3 * dir } + fadeOut(tween(160)))
            }
        },
        label = "setupStep",
      ) { s ->
       Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (s < 2) {
            Text(
                stringResource(if (s == 0) R.string.lock_set_title else R.string.lock_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (!type.isPin) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lock_custom_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            key(s) {
                CodeInput(
                    type = type,
                    resetKey = 0,
                    busy = false,
                    error = error?.let { stringResource(it) },
                    onEdit = { error = null },
                    onSubmit = { code ->
                        if (s == 0) {
                            if (!type.isPin && code.length < AppLock.CUSTOM_MIN) {
                                error = R.string.lock_too_short
                            } else {
                                first = code
                                error = null
                                step = 1
                            }
                        } else if (code == first) {
                            words = AppLock.newRecoveryWords()
                            error = null
                            step = 2
                        } else {
                            first = ""
                            error = R.string.lock_mismatch
                            step = 0
                        }
                    },
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                )
            }
        } else {
            Icon(Icons.Default.Description, null, Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.lock_recovery_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.lock_recovery_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    error = null
                    // При смене кода блокировка уже включена: системное окно сохранения не должно её сработать
                    AppLock.skipNextLock()
                    saver.launch(RECOVERY_FILE_NAME)
                },
                enabled = !busy,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            ) { Text(stringResource(R.string.lock_recovery_save)) }
        }
       }
      }
    }
}

/** Чтобы выключить блокировку, нужно ввести текущий код. */
@Composable
private fun DisableContent() {
    val ctx = LocalContext.current
    val type = remember { AppLock.type }
    val check = rememberCodeCheck {
        AppLock.disable(ctx)
        AppLock.endFlow()
    }
    FlowColumn {
        Text(
            stringResource(R.string.lock_disable_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CheckedInput(type, check)
    }
}

/** Смена кода: текущий код, затем выбор вида и создание нового кода (и нового recovery-файла). Старый код действует, пока новый не сохранён. */
@Composable
private fun ChangeContent() {
    val type = remember { AppLock.type }
    var verified by remember { mutableStateOf(false) }
    val check = rememberCodeCheck { verified = true }
    FlowColumn {
        Text(
            stringResource(R.string.lock_current_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CheckedInput(type, check)
    }
    if (verified) {
        LockTypeDialog(onSelect = { AppLock.startSetup(it) }, onDismiss = { AppLock.endFlow() })
    }
}

@Composable
private fun CheckedInput(type: LockType, check: CodeCheck) {
    CodeInput(
        type = type,
        resetKey = check.resetKey,
        busy = check.disabled,
        error = check.errorText(),
        onEdit = { check.edit() },
        onSubmit = { check.submit(it) },
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    )
}
