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

    private val golden: List<Pair<Int, String>> = listOf(
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
    }

    @Test
    fun theActiveDevicesPlural_isSingularForOneAndPluralOtherwise() {
        fun text(count: Int) = UiText.plural(R.plurals.settings_privacy_active_devices, count, count).text()
        assertEquals("1 dispositivo activo", text(1))
        assertEquals("2 dispositivos activos", text(2))
        assertEquals("0 dispositivos activos", text(0))
    }
}
