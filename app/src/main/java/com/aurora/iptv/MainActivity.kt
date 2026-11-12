package com.aurora.iptv

import android.Manifest
import android.app.PictureInPictureParams
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.iptv.data.settings.LocaleManager
import com.aurora.iptv.playback.PlaybackConnection
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.AuroraRoot
import com.aurora.iptv.ui.AuroraViewModelFactory
import com.aurora.iptv.ui.theme.AuroraTheme

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private lateinit var connection: PlaybackConnection

    private var pendingRoute by mutableStateOf<String?>(null)
    private var pendingSearch by mutableStateOf<String?>(null)
    private var inPip by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = (application as AuroraApp).container
        connection = appContainer.playbackConnection
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        handleIntent(intent)
        requestNotificationPermission()

        setContent {
            AuroraTheme {
                val factory = AuroraViewModelFactory(appContainer)
                val appViewModel: AppViewModel = viewModel(factory = factory)
                AuroraRoot(
                    container = appContainer,
                    appViewModel = appViewModel,
                    connection = connection,
                    pendingRoute = pendingRoute,
                    pendingSearch = pendingSearch,
                    inPip = inPip,
                    onIntentConsumed = {
                        pendingRoute = null
                        pendingSearch = null
                    },
                    onEnterPip = ::enterPip
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEARCH -> {
                pendingSearch = intent.getStringExtra(SearchManager.QUERY).orEmpty()
                pendingRoute = "search"
            }

            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == "aurora") {
                    pendingRoute = data.host ?: data.pathSegments.firstOrNull() ?: "live"
                }
            }

            else -> {
                intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
                intent.getStringExtra(SearchManager.QUERY)?.let {
                    pendingSearch = it
                    pendingRoute = "search"
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Called by the player's PiP button. */
    fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            setPictureInPictureParams(params)
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        connection.connect()
    }

    override fun onStop() {
        if (!isInPictureInPictureMode) connection.release()
        super.onStop()
    }

    override fun onDestroy() {
        connection.release()
        super.onDestroy()
    }

    /**
     * Many Bluetooth remotes report gamepad-style buttons. Translate them once at the
     * activity edge so every Compose component sees ordinary D-pad OK / back events.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_UP) {
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_BUTTON_A ||
            code == KeyEvent.KEYCODE_BUTTON_START ||
            code == KeyEvent.KEYCODE_BUTTON_SELECT
        ) {
            val mapped = KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                KeyEvent.KEYCODE_DPAD_CENTER,
                0,
                event.metaState,
                event.deviceId,
                event.scanCode
            )
            return super.dispatchKeyEvent(mapped)
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_ROUTE = "aurora.route"
    }
}
