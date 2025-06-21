package io.corbado.connect.example

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.kotlin.core.Amplify
import io.corbado.connect.example.di.CorbadoService
import io.corbado.connect.example.ui.Screen
import io.corbado.connect.example.ui.login.LoginScreen
import io.corbado.connect.example.ui.postlogin.PostLoginScreen
import io.corbado.connect.example.ui.profile.ProfileScreen
import io.corbado.connect.example.ui.signup.SignUpScreen
import io.corbado.connect.example.ui.totpscreen.TotpSetupScreen
import io.corbado.connect.example.ui.theme.ConnectExampleTheme

class MainActivity : ComponentActivity() {
    companion object {
        // Static field for virtual authenticator injection during UI tests
        @JvmStatic
        var virtualAuthorizationController: Any? = null
        
        // Test mode flags
        @JvmStatic
        var isUITestMode: Boolean = false
        
        @JvmStatic
        var controlServerURL: String? = null
    }

    fun setGradualRollout(allowedByGradualRollout: Boolean = true) {
        val corbado = CorbadoService.getInstance(application)
        if (allowedByGradualRollout) {
            corbado.setInvitationToken("inv-token-correct")
        } else {
            corbado.setInvitationToken("inv-token-negative")
        }
    }

    fun blockCorbadoEndpoints(urls: List<String> = listOf()) {
        val corbado = CorbadoService.getInstance(application)
        corbado.setBlockedUrls(urls)
    }

    fun resetCorbadoInstance() {
        CorbadoService.getInstance(application).clearLocalState()
        CorbadoService.resetInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as? MainApplication)?.initAmplify()

        setContent {
            ConnectExampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        startDestination = try {
                            val session = Amplify.Auth.fetchAuthSession()
                            if (session.isSignedIn && isUITestMode) {
                                Amplify.Auth.signOut()
                                Screen.Login.route
                            } else if (session.isSignedIn) {
                                Screen.Profile.route
                            } else {
                                Screen.Login.route
                            }
                        } catch (e: Exception) {
                            Screen.Login.route
                        }
                    }

                    if (startDestination != null) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination!!
                        ) {
                            composable(Screen.Login.route) {
                                LoginScreen(navController)
                            }
                            composable(Screen.SignUp.route) {
                                SignUpScreen(navController)
                            }
                            composable(Screen.Profile.route) {
                                ProfileScreen(navController)
                            }
                            composable(Screen.PostLogin.route) {
                                PostLoginScreen(navController)
                            }
                            composable(Screen.TotpSetup.route) {
                                TotpSetupScreen(navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        initAmplify()
    }

    fun initAmplify() {
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(applicationContext)
            Log.i("MainApplication", "Initialized Amplify")
        } catch (error: AmplifyException) {
            Log.e("MainApplication", "Could not initialize Amplify", error)
        } catch (e: Exception) {
            if (e.message?.contains("Amplify has already been configured") == true) {
                return
            }
            Log.e("MainApplication", "Could not initialize Amplify", e)
        }
    }
} 