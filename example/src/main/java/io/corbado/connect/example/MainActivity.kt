package io.corbado.connect.example

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import io.corbado.connect.example.ui.Screen
import io.corbado.connect.example.ui.login.LoginScreen
import io.corbado.connect.example.ui.postlogin.PostLoginScreen
import io.corbado.connect.example.ui.profile.ProfileScreen
import io.corbado.connect.example.ui.signup.SignUpScreen
import io.corbado.connect.example.ui.totpscreen.TotpSetupScreen
import io.corbado.connect.example.ui.theme.ConnectExampleTheme

class MainActivity : ComponentActivity() {
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

                    NavHost(navController = navController, startDestination = Screen.Login.route) {
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