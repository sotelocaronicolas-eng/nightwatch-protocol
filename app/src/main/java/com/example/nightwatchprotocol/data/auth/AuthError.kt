package com.example.nightwatchprotocol.data.auth

sealed class AuthError(message: String) : Exception(message) {
    data object WrongPassword : AuthError("La contraseña es incorrecta")
    data object UserNotFound : AuthError("El correo no está registrado")
    data object InvalidCredentials : AuthError("Correo o contraseña incorrectos")
    data object GoogleAccountOnly : AuthError("Esta cuenta se creó con Google. Usa «Continuar con Google» para entrar.")
    data object InvalidEmail : AuthError("Correo electrónico inválido")
    data object EmailAlreadyInUse : AuthError("Ya tienes una cuenta con este correo")
    data object WeakPassword : AuthError("La contraseña no cumple con los requerimientos")
    data object NetworkError : AuthError("Sin conexión a internet")
    data object Unknown : AuthError("Se produjo un error inesperado. Intenta de nuevo.")
}
