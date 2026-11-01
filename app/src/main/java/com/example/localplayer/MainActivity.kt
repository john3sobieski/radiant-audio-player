package com.example.localplayer

import android.Manifest
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    /** Подставляет выбранный в настройках язык: все `stringResource` и `plurals` берут перевод из этих ресурсов. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* не критично */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerSave.load(this)
        // Холодный старт при включённой блокировке: приложение сразу закрыто экраном ввода кода
        AppLock.init(this)
        applySecure(AppLock.locked)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // Защита от скриншотов и записи экрана только на экране ввода кода
            val secure = AppLock.locked
            SideEffect { applySecure(secure) }
            AppTheme(vm.themeMode) {
                if (AppLock.locked) {
                    LockScreen()
                } else {
                    Box(Modifier.fillMaxSize()) {
                        App(vm)
                        // Создание кода / подтверждение перед отключением блокировки: слой поверх всего приложения
                        AppLock.flow?.let { LockFlowOverlay(it) }
                    }
                }
            }
        }
    }

    private fun applySecure(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        // Каждый вход в приложение (запуск или возврат из фона): проверяем папку на новые и удалённые файлы
        vm.refresh()
    }

    override fun onStop() {
        super.onStop()
        CoverStore.flush()
        // Каждый уход в фон закрывает приложение блокировкой. Пересоздание активности (смена языка, поворот)
        // и системное окно выбора папки, которое мы сами открыли, уходом не считаются.
        if (!isChangingConfigurations && !AppLock.consumeSkip()) AppLock.lockNow()
    }
}

/** Чисто чёрная тёмная схема (как OLED-темы): фон и поверхности #000000, а карточки и диалоги чуть светлее, чтобы читались на чёрном. */
private fun ColorScheme.asOled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2A2A2A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424),
    // Тональное затемнение (tonalElevation) не должно подкрашивать чёрный в серый
    surfaceTint = Color.Black,
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    // Интенсивное энергосбережение: тема всегда чёрная, выбранная пользователем при этом не теряется
    val dark = if (PowerSave.intensive) true else when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val target = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx).asOled() else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme().asOled()
        else -> lightColorScheme()
    }
    // При смене темы цвета плавно перетекают, а не меняются скачком
    val scheme = target.animated()

    // Значки статус-бара и панели навигации и фон окна должны следовать теме приложения, а не системной
    // (иначе при «Ясный» на тёмном телефоне значки были бы белыми на светлом фоне, и наоборот).
    val activity = ctx as? ComponentActivity
    val windowBg = scheme.background.toArgb()
    SideEffect {
        activity?.apply {
            enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
                navigationBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), 0x80_1B_1B_1B.toInt()),
            )
            window.setBackgroundDrawable(ColorDrawable(windowBg))
        }
    }

    MaterialTheme(colorScheme = scheme) {
        // Энергосбережение: у нажатий нет анимированной «ряби»
        CompositionLocalProvider(LocalIndication provides (if (PowerSave.reduceMotion) NoIndication else LocalIndication.current)) {
            content()
        }
    }
}
