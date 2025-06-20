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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse launch arguments for test mode
        parseLaunchArguments()

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
                        try {
                            val session = Amplify.Auth.fetchAuthSession()
                            if (session.isSignedIn) {
                                startDestination = Screen.Profile.route
                            } else {
                                startDestination = Screen.Login.route
                            }
                        } catch (e: Exception) {
                            startDestination = Screen.Login.route
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
    
    private fun parseLaunchArguments() {
        val extras = intent.extras
        if (extras != null) {
            for (key in extras.keySet()) {
                val value = extras.getString(key)
                Log.d("MainActivity", "Launch argument: $key = $value")
                
                when (key) {
                    "-UITestMode" -> {
                        isUITestMode = true
                        Log.i("MainActivity", "UI Test Mode enabled")
                    }
                    "-ControlServerURL" -> {
                        controlServerURL = value
                        Log.i("MainActivity", "Control Server URL: $value")
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