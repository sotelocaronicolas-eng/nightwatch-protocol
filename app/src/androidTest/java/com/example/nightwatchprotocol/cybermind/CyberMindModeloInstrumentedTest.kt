package com.example.nightwatchprotocol.cybermind

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Carga el .onnx real y evalúa el ejemplo de `src/probar_modelo_onnx.py` del repo de IA
 * (criptografía, 44,4 %), que debe dar REFUERZO con probabilidad 0,90.
 */
@RunWith(AndroidJUnit4::class)
class CyberMindModeloInstrumentedTest {

    @Test
    fun ejemploDelRepoDeIa_daRefuerzoConProbabilidad090() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CyberMindModelo(context).use { modelo ->
            val decision = modelo.decidir(
                Diagnostico("criptografia", "cursos", 3, 1, 0, 44.4f, 61.0f, 38f),
            )
            assertEquals("modelo_gbdt", decision.origen)
            assertEquals(Ruta.REFUERZO, decision.ruta)
            assertTrue(
                "probabilidad ${decision.probabilidadRefuerzo}",
                kotlin.math.abs(decision.probabilidadRefuerzo - 0.90f) < 0.01f,
            )
        }
    }
}
