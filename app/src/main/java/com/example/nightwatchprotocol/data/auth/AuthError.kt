package com.example.nightwatchprotocol.data.auth

sealed class AuthError(message: String) : Exception(message) {
    data object WrongPassword : AuthError("Contraseña incorrecta")
    data object UserNotFound : AuthError("Correo no encontrado")
    data object InvalidEmail : AuthError("Correo electrónico inválido")
    data object EmailAlreadyInUse : AuthError("Ya tienes una cuenta con este correo")
    data object WeakPassword : AuthError("La contraseña no cumple con los requerimientos")
    data object NetworkError : AuthError("Sin conexión a internet")
    data object Unknown : AuthError("Se produjo un error inesperado. Intenta de nuevo.")
}
