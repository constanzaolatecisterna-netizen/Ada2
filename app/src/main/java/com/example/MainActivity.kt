package com.example

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.GoogleAuthHelper
import com.example.auth.GoogleAuthOutcome
import com.example.data.AlianzaRepository
import com.example.firebase.configureAppCheck
import com.example.runtime.AdaExternalAction
import com.example.runtime.AdaRuntimeHub
import com.example.ui.AdaMainScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.AdaAlianzaTheme
import com.example.ui.theme.AdaColors
import com.example.viewmodel.AlianzaViewModel
import com.example.widget.AdaWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure Firebase App Check with launch intent BEFORE setContent or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            AdaAlianzaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AdaColors.Background,
                ) {
                    AppNavigation(initialIntentAction = intent?.getStringExtra(AdaWidgetProvider.EXTRA_WIDGET_ACTION))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")) {
            configureAppCheck(this, intent)
        }
        dispatchWidgetAction(intent.getStringExtra(AdaWidgetProvider.EXTRA_WIDGET_ACTION))
    }

    private fun dispatchWidgetAction(action: String?) {
        when (action) {
            AdaWidgetProvider.ACTION_OPEN_VOICE ->
                AdaRuntimeHub.dispatchAction(this, AdaExternalAction.StartVoice)
            AdaWidgetProvider.ACTION_READ_MESSAGES ->
                AdaRuntimeHub.dispatchAction(this, AdaExternalAction.ReadMessages)
            AdaWidgetProvider.ACTION_READ_ALERTS ->
                AdaRuntimeHub.dispatchAction(this, AdaExternalAction.ReadAlerts)
        }
    }
}

@Composable
private fun AppNavigation(initialIntentAction: String? = null) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    val auth = remember { Firebase.auth }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var checkingAutoSignIn by remember { mutableStateOf(auth.currentUser == null) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            val signedIn = GoogleAuthHelper.attemptAutoSignIn(context)
            if (signedIn != null) {
                currentUser = signedIn
            }
        }
        checkingAutoSignIn = false
    }

    val user = currentUser
    if (checkingAutoSignIn) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = AdaColors.SkyPrimary)
        }
    } else if (user == null) {
        AuthScreen(
            isLoading = isSigningIn,
            errorMessage = authError,
            onGoogleSignInClick = {
                scope.launch {
                    isSigningIn = true
                    authError = null
                    when (val outcome = GoogleAuthHelper.signInWithGoogle(context)) {
                        is GoogleAuthOutcome.Success -> currentUser = outcome.user
                        is GoogleAuthOutcome.Cancelled -> Unit
                        is GoogleAuthOutcome.Failure -> authError = outcome.message
                    }
                    isSigningIn = false
                }
            },
        )
    } else {
        key(user.uid) {
            val repository = remember(user.uid) { AlianzaRepository.create(application) }
            val viewModel: AlianzaViewModel = viewModel(
                key = user.uid,
                factory = AlianzaViewModel.Factory(
                    application = application,
                    uid = user.uid,
                    repository = repository,
                ),
            )
            LaunchedEffect(user.uid, initialIntentAction) {
                when (initialIntentAction) {
                    AdaWidgetProvider.ACTION_OPEN_VOICE -> viewModel.startVoiceListening()
                    AdaWidgetProvider.ACTION_READ_MESSAGES -> viewModel.readAllianceMessagesAloud()
                    AdaWidgetProvider.ACTION_READ_ALERTS -> viewModel.readNotificationsAloud()
                }
            }
            AdaMainScreen(
                viewModel = viewModel,
                userEmail = user.email,
                onSignOut = {
                    scope.launch {
                        GoogleAuthHelper.signOut(context)
                    }
                },
            )
        }
    }
}
