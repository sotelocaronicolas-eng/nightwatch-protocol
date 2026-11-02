package com.example.nightwatchprotocol

import com.example.nightwatchprotocol.data.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Golden list" of Stage 3, part A (F-09): the text that each migrated key resolves to is EXACTLY the literal that used to be
 * in the code (zero visual change, nothing translated). It reads the real `strings_*.xml` through [TestStrings], so a typo,
 * a lost double space or a badly escaped character in the XML fails here. Part B adds its own keys.
 * The literals on the right are copied from the code before the migration (git history of the stage).
 */
class GoldenTextsTest {

    private val golden: List<Pair<Int, String>> get() = listOf(
        R.string.common_accept to "Aceptar",
        R.string.common_cancel to "Cancelar",
        R.string.common_continue_with_google to "Continuar con Google",
        R.string.common_app_title to "Nightwatch Protocol",
        R.string.common_no_connection to "Sin conexión a internet",
        R.string.common_generic_error to "Se produjo un error inesperado. Intenta de nuevo.",
        R.string.common_level_not_assessed to "Sin evaluar",
        R.string.common_default_user_name to "Usuario",
        R.string.common_month_short_1 to "Ene.",
        R.string.common_month_short_8 to "Ago.",
        R.string.common_month_short_12 to "Dic.",
        R.string.badge_first_steps to "Primeros pasos",
        R.string.badge_streak_7 to "Racha de 7 días",
        R.string.badge_passwords_master to "Maestro de Contraseñas",
        R.string.badge_phishing_hunter to "Cazador de Phishing",
        R.string.badge_network_guardian to "Guardián de Redes",
        R.string.badge_legend to "Leyenda NightWatch",
        R.string.badge_social_engineering_detective to "Detective de Ingeniería Social",
        R.string.badge_streak_30 to "Racha de 30 días",
        R.string.badge_graduate to "Graduado NightWatch",
        R.string.error_auth_google_account_only to "Esta cuenta se creó con Google. Usa «Continuar con Google» para entrar.",
        R.string.error_auth_invalid_code to "El código ingresado no es correcto.",
        R.string.error_auth_code_expired to "El código expiró. Solicita uno nuevo.",
        R.string.error_auth_too_many_attempts to "Demasiados intentos. Espera unos minutos y vuelve a intentarlo.",
        R.string.settings_menu_section_privacy to "PRIVACIDAD Y SEGURIDAD",
        R.string.settings_menu_view_profile to "Ver Perfil",
        R.string.settings_menu_achievements to "Logros e Insignias",
        // The two spaces after the emoji are part of the original text.
        R.string.settings_menu_sign_out to "🚪  Cerrar Sesión",
        R.string.profile_my_profile_title to "Mi Perfil",
        R.string.profile_gender_prefer_not_to_say to "Prefiero no decir",
        R.string.profile_edit_save to "GUARDAR CAMBIOS",
        R.string.profile_error_birth_date_too_old to "Revisa la fecha de nacimiento: la edad máxima es de 120 años",
        R.string.profile_error_same_password to "La nueva contraseña no puede ser igual a la actual",
        R.string.profile_password_rule_upper_digit to "Incluye una mayúscula y un número",
        R.string.profile_updated_title to "¡Perfil actualizado!",
        R.string.profile_password_updated_button to "Listo",
        R.string.devices_title to "Administrar Dispositivos",
        R.string.devices_unnamed to "Dispositivo sin nombre",
        R.string.devices_status_current to "Actual",
        R.string.devices_status_pending_sign_out to "Cierre pendiente",
        R.string.devices_signed_out_title to "¡Sesión cerrada!",
        R.string.settings_notifications_daily_subtitle to "Un aviso a las 7:00 p.m. si no has practicado",
        R.string.settings_notifications_denied to
            "Sin el permiso de notificaciones no podemos enviarte recordatorios. " +
            "Puedes permitirlo en Ajustes del teléfono > Aplicaciones > NightWatch > Notificaciones.",
        R.string.settings_appearance_auto_subtitle to "Sigue el ajuste del sistema",
        R.string.settings_language_spanish to "Español",
        R.string.settings_language_english to "English",
        R.string.settings_privacy_biometric_label to "Bloqueo Biométrico",
        R.string.settings_privacy_prompt_title to "Activar bloqueo",
        R.string.settings_help_faq_path to "¿Cómo funciona la Ruta de Aprendizaje?",
        R.string.settings_about_terms to "Términos y Condiciones",
        R.string.settings_about_made_with to "Hecho con 🛡️ para futuros defensores de la ciberseguridad.",
        R.string.verify_code_title to "Verifica tu Correo",
        R.string.verify_code_confirm_button to "CONFIRMAR CÓDIGO",
        R.string.notif_daily_channel_name to "Recordatorio de lección diaria",
        R.string.notif_daily_title to "Es hora de practicar",
        R.string.notif_daily_text to "Una lección corta mantiene tu ritmo. Entra a NightWatch.",
        R.string.notif_streak_channel_name to "Racha en riesgo",
        R.string.notif_streak_title to "Tu racha está en riesgo",
        R.string.notif_streak_text to "Practica hoy para no perder tu racha.",
    ) + partB1 + partB2

    /** Part B1: authentication, quiz, app lock, navigation and the shared texts that came with them. */
    private val partB1: List<Pair<Int, String>> get() = listOf(
        R.string.common_close to "Cerrar",
        R.string.common_understood to "Entendido",
        R.string.common_level_low to "BAJO",
        R.string.common_level_medium to "MEDIO",
        R.string.common_level_high to "ALTO",
        R.string.applock_unlock to "Desbloquear",
        R.string.applock_prompt_title to "Desbloquear NightWatch",
        R.string.applock_prompt_subtitle to "Usa tu huella para continuar",
        R.string.nav_session_ended_title to "Sesión cerrada",
        R.string.nav_session_ended_message to "Se cerró tu sesión porque lo pediste desde otro dispositivo.",
        R.string.auth_password_label to "Contraseña",
        R.string.auth_google_account_hint to "Esta cuenta se creó con Google. Usa «Continuar con Google»",
        R.string.auth_error_no_google_account to "Agrega una cuenta de Google en tu teléfono para continuar",
        R.string.auth_error_dialog_network to "Sin conexión a internet. Verifica tu conexión e intenta de nuevo.",
        R.string.auth_login_error_title to "Error de inicio de sesión",
        R.string.auth_login_error_unknown to "Se produjo un error inesperado. Intenta iniciar sesión de nuevo.",
        R.string.auth_login_username_label to "Nombre de usuario/Email",
        R.string.auth_login_enter_email to "Ingresa tu correo",
        R.string.auth_login_enter_password to "Ingresa tu contraseña",
        R.string.auth_login_forgot_password to "¿Olvidaste tu contraseña?",
        R.string.auth_login_keep_session to "Mantener sesión iniciada",
        R.string.auth_login_button to "INICIAR SESIÓN",
        R.string.auth_login_or to "O",
        // The space at the end is part of the original text (it separates the link that follows).
        R.string.auth_login_no_account to "¿Aún no tienes cuenta? ",
        R.string.auth_login_register_here to "Regístrate aquí",
        R.string.auth_register_error_title to "Error de registro",
        R.string.auth_register_error_unknown to "Se produjo un error inesperado. Intenta registrarte de nuevo.",
        R.string.auth_register_title to "Registro de Usuario",
        R.string.auth_register_subtitle to "Ingrese los datos requeridos a continuación",
        R.string.auth_register_full_name to "Nombre Completo",
        R.string.auth_register_email_registered_with_google to "Este correo ya está registrado con Google. Usa «Continuar con Google»",
        R.string.auth_register_weak_password to "La contraseña no cumple los requisitos de seguridad",
        R.string.auth_register_verify_password to "Verifica la contraseña",
        R.string.auth_register_password_mismatch to "Contraseña no coincide",
        R.string.auth_register_button to "REGISTRARSE",
        R.string.auth_recovery_title to "Recuperación de contraseña",
        R.string.auth_recovery_intro to
            "Para recuperar su contraseña, ingrese su correo electrónico. Recibirá un código de " +
            "verificación de 6 dígitos para continuar con el proceso.",
        R.string.auth_recovery_email_not_found to "Correo no encontrado",
        R.string.auth_new_password_title to "Nueva Contraseña",
        R.string.quiz_saving to "Guardando…",
        R.string.quiz_finish to "Finalizar",
        R.string.quiz_next to "Siguiente",
        R.string.quiz_category_basics to "🛡️ Conceptos Básicos",
        R.string.quiz_category_passwords to "🔑 Contraseñas y Autenticación",
        R.string.quiz_category_social to "🎣 Phishing / Ingeniería Social",
        R.string.quiz_category_networks to "🌐 Redes y Malware",
        R.string.quiz_result_title to "¡Resultados listos!",
        R.string.quiz_result_plan_title to "Tu plan de estudio recomendado:",
        R.string.quiz_result_save_failed to "No pudimos guardar tu resultado; es posible que veas este quiz de nuevo la próxima vez.",
        R.string.quiz_result_continue to "Continuar",
        R.string.plan_low_basics to "Fundamentos de ciberseguridad",
        R.string.plan_low_passwords to "Higiene básica de contraseñas",
        R.string.plan_low_social to "Cómo reconocer un intento de phishing",
        R.string.plan_low_networks to "Introducción a redes y malware",
        R.string.plan_medium_basics to "Principios de defensa en profundidad",
        R.string.plan_medium_passwords to "Autenticación multifactor y gestión de credenciales",
        R.string.plan_medium_social to "Ingeniería social dirigida (spear phishing y pretexting)",
        R.string.plan_medium_networks to "Fundamentos de redes seguras y tipos de malware",
        R.string.plan_high_basics to "Modelado de amenazas y gestión de vulnerabilidades",
        R.string.plan_high_passwords to "Arquitecturas de autenticación y almacenamiento seguro de credenciales",
        R.string.plan_high_social to "Análisis avanzado de ataques de ingeniería social",
        R.string.plan_high_networks to "Endurecimiento de redes y respuesta a incidentes de malware",
    )

    /**
     * Part B2: learning. Almost all of it was already in `strings_aprendizaje.xml` (module 3 used resources); only the answer of
     * the lab terminal to an unknown command was still a literal in `data/learning/LabTerminal.kt`.
     */
    private val partB2: List<Pair<Int, String>> get() = listOf(
        R.string.learning_lab_unrecognized_command to "Comando no reconocido. Toca un objetivo para usar su comando.",
    )

    @Test
    fun everyMigratedText_resolvesToTheOriginalLiteral() {
        golden.forEach { (id, expected) -> assertEquals(expected, TestStrings.string(id, emptyList())) }
    }

    @Test
    fun theTextsWithArguments_keepTheirOriginalShape() {
        assertEquals("Nightwatch Protocol · v2.2.0", TestStrings.string(R.string.settings_menu_footer, listOf("2.2.0")))
        assertEquals("Versión 2.2.0", TestStrings.string(R.string.settings_about_version, listOf("2.2.0")))
        assertEquals("Ago. 2026", TestStrings.string(R.string.profile_member_since_value, listOf("Ago.", 2026)))
        assertEquals("Espera 37 s antes de pedir otro código.", TestStrings.string(R.string.profile_error_wait_seconds, listOf(37)))
        assertEquals("Reenviar en 45 s", TestStrings.string(R.string.verify_code_resend_in, listOf(45)))
        assertEquals("Reenviar el código de 6 dígitos", TestStrings.string(R.string.verify_code_resend, listOf(6)))
        assertEquals("Código de 6 dígitos", TestStrings.string(R.string.verify_code_field_description, listOf(6)))
        assertEquals("Hace 2 h", TestStrings.string(R.string.devices_time_hours, listOf(2)))
        assertEquals("Mínimo 8 caracteres: cumplido", TestStrings.string(R.string.profile_password_rule_met, listOf("Mínimo 8 caracteres")))
        assertEquals("Pregunta 3 de 10", TestStrings.string(R.string.quiz_progress, listOf(3, 10)))
        assertEquals("Tu nivel: MEDIO", TestStrings.string(R.string.quiz_result_level, listOf("MEDIO")))
    }

    @Test
    fun theActiveDevicesPlural_isSingularForOneAndPluralOtherwise() {
        fun text(count: Int) = UiText.plural(R.plurals.settings_privacy_active_devices, count, count).text()
        assertEquals("1 dispositivo activo", text(1))
        assertEquals("2 dispositivos activos", text(2))
        assertEquals("0 dispositivos activos", text(0))
    }
}
