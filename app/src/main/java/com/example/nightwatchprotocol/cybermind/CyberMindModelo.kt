// Ejemplo de integración del modelo de IA de CyberMind en la app Android (Kotlin).
//
// 1. Copiar modelo/cybermind_gbdt.onnx a  app/src/main/assets/cybermind_gbdt.onnx
// 2. En app/build.gradle.kts:
//        dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0") }
//    (usar el paquete completo "onnxruntime-android": el modelo usa operadores de ML
//     como OneHotEncoder y TreeEnsembleClassifier que el paquete reducido "mobile" no trae)
// 3. Crear CyberMindModelo una sola vez (por ejemplo en el ViewModel) y llamar a decidir()
//    cuando el estudiante termina el diagnóstico de un tema. Todo se ejecuta en el celular, sin internet.
//
// Cambia el nombre del paquete por el de la app.
package com.example.nightwatchprotocol.cybermind

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

/** Resultado del diagnóstico de UN tema, con los mismos nombres que usa el modelo. */
data class Diagnostico(
    val tema: String,               // criptografia | fundamentos_redes | gestion_vulnerabilidades | ingenieria_social | seguridad_web
    val experienciaPrevia: String,  // ninguna | cursos | laboral
    val aciertosBasico: Int,        // 0..3
    val aciertosIntermedio: Int,    // 0..3
    val aciertosAvanzado: Int,      // 0..3
    val puntajeTema: Float,         // 0..100
    val puntajeGlobal: Float,       // 0..100
    val tiempoPromedioSeg: Float,
)

enum class Ruta { REFUERZO, AVANCE }

data class Decision(
    val ruta: Ruta,
    val probabilidadRefuerzo: Float,
    val origen: String,             // "modelo_gbdt" o "regla_por_defecto"
)

class CyberMindModelo(context: Context) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    // Si el archivo no carga, sesion queda en null y se usa la ruta por defecto.
    private val sesion: OrtSession? = runCatching {
        val bytes = context.assets.open(ARCHIVO).use { it.readBytes() }
        env.createSession(bytes, OrtSession.SessionOptions())
    }.getOrNull()

    /** Versión guardada dentro del .onnx (útil para registrarla en Supabase junto a la decisión). */
    val version: String? = runCatching { sesion?.metadata?.customMetadata?.get("version") }.getOrNull()

    fun decidir(d: Diagnostico): Decision {
        val s = sesion ?: return reglaPorDefecto(d)
        return runCatching {
            val entradas = mapOf(
                "tema" to texto(d.tema),
                "experiencia_previa" to texto(d.experienciaPrevia),
                "aciertos_basico" to numero(d.aciertosBasico.toFloat()),
                "aciertos_intermedio" to numero(d.aciertosIntermedio.toFloat()),
                "aciertos_avanzado" to numero(d.aciertosAvanzado.toFloat()),
                "puntaje_tema" to numero(d.puntajeTema),
                "puntaje_global" to numero(d.puntajeGlobal),
                "tiempo_promedio_seg" to numero(d.tiempoPromedioSeg),
            )
            try {
                s.run(entradas).use { salida ->
                    // Salida "probabilities": float32 [1, 2] = [prob. AVANCE, prob. REFUERZO]
                    @Suppress("UNCHECKED_CAST")
                    val prob = (salida.get("probabilities").get().value as Array<FloatArray>)[0][1]
                    Decision(if (prob >= UMBRAL) Ruta.REFUERZO else Ruta.AVANCE, prob, "modelo_gbdt")
                }
            } finally {
                entradas.values.forEach { it.close() }
            }
        }.getOrElse { reglaPorDefecto(d) }
    }

    // Cada entrada del modelo tiene forma [1, 1]
    private fun texto(v: String) = OnnxTensor.createTensor(env, arrayOf(v), longArrayOf(1, 1))
    private fun numero(v: Float) = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(v)), longArrayOf(1, 1))

    /** Ruta por defecto si el modelo no está disponible: refuerzo si el puntaje es menor a 70 %. */
    private fun reglaPorDefecto(d: Diagnostico) = Decision(
        if (d.puntajeTema < 70f) Ruta.REFUERZO else Ruta.AVANCE,
        if (d.puntajeTema < 70f) 1f else 0f,
        "regla_por_defecto",
    )

    override fun close() { sesion?.close() }

    companion object {
        const val ARCHIVO = "cybermind_gbdt.onnx"
        const val UMBRAL = 0.5f
    }
}

/*
Uso (por ejemplo en un ViewModel con Jetpack Compose):

    private val modelo = CyberMindModelo(application)

    fun alTerminarDiagnostico(d: Diagnostico) {
        val decision = modelo.decidir(d)
        _uiState.update { it.copy(ruta = decision.ruta) }   // la pantalla muestra la ruta
        // Opcional: guardar en Supabase la decisión, la probabilidad, el origen y modelo.version
    }

Ejemplo: Diagnostico("criptografia", "cursos", 3, 1, 0, 44.4f, 61.0f, 38f)
         -> REFUERZO con probabilidad 0,90 (mismo resultado que src/probar_modelo_onnx.py)
*/
