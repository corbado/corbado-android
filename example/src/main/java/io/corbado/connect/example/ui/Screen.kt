package com.corbado.connect.example.ui

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SignUp : Screen("signUp")
    object Profile : Screen("profile")
    object PostLogin : Screen("postLogin")
    object TotpSetup : Screen("totpSetup")
} 