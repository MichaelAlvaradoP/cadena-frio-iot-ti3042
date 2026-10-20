package cl.inacap.ti3042.cadenafrio

import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Fuente de datos simulada. Imita al Arduino del contenedor: entrega una
 * lectura cada 2 segundos y acepta los mismos comandos de texto.
 *
 * Modelo simple, pensado para la demostración en clases:
 * - Viales (tv): oscilan alrededor de 4,5 °C. Si bajan cerca del umbral
 *   inferior se enciende el calefactor, que los entibia hasta 1 °C más arriba
 *   (histéresis). El contenedor no fabrica frío: el actuador solo calienta.
 * - Refrigerante (tr): alrededor de −2 °C. Exterior (te): alrededor de 26 °C.
 * - La tapa solo puede abrirse con la cerradura liberada; abierta, entra calor.
 * - DEMO ON estrecha los umbrales a 4–6 °C y aumenta la variación, para que
 *   los cambios de estado se vean rápido.
 */
class SimuladorDatos : FuenteDatos {

    private val handler = Handler(Looper.getMainLooper())
    private val observadores = mutableListOf<ObservadorEstado>()
    private var ejecutando = false

    private var estadoActual = EstadoSistema()

    // Datos internos del simulador que no viajan en la trama
    private var segundosFuera = 0L
    private var lecturasTapaAbierta = 0

    // Tarea periódica: una lectura cada INTERVALO_MS
    private val tarea = object : Runnable {
        override fun run() {
            if (!ejecutando) return
            generarLectura()
            handler.postDelayed(this, INTERVALO_MS)
        }
    }

    override fun iniciar() {
        if (!ejecutando) {
            ejecutando = true
            handler.post(tarea)
        }
    }

    override fun detener() {
        ejecutando = false
        handler.removeCallbacks(tarea)
    }

    override fun registrarObservador(observador: ObservadorEstado) {
        if (!observadores.contains(observador)) {
            observadores.add(observador)
            observador(estadoActual)
        }
    }

    override fun quitarObservador(observador: ObservadorEstado) {
        observadores.remove(observador)
    }

    override fun estaConectada(): Boolean = ejecutando

    override fun nombreFuente(): String = "Simulador"

    /**
     * Interpreta un comando del protocolo, como lo haría el Arduino.
     * El cambio se refleja en la siguiente lectura (máximo 2 s).
     */
    override fun enviarComando(comando: String) {
        estadoActual = when (Protocolo.normalizar(comando)) {
            "LOCK" -> estadoActual.copy(bloqueado = true)
            "UNLOCK" -> estadoActual.copy(bloqueado = false)
            "DEMO ON" -> estadoActual.copy(modoDemo = true)
            "DEMO OFF" -> estadoActual.copy(modoDemo = false)
            // SIL silencia el zumbador del Arduino; no cambia ningún dato de la trama
            else -> Protocolo.aplicarSet(comando, estadoActual)
        }
    }

    /** Calcula una lectura nueva y la entrega a los observadores. */
    private fun generarLectura() {
        val e = estadoActual

        // Umbrales efectivos: en modo demostración se estrechan
        val inferior = if (e.modoDemo) DEMO_INFERIOR else e.umbralInferior
        val superior = if (e.modoDemo) DEMO_SUPERIOR else e.umbralSuperior

        // Tapa: solo se abre si la cerradura está liberada; queda abierta 3 lecturas
        var aperturas = e.aperturas
        if (lecturasTapaAbierta > 0) {
            lecturasTapaAbierta--
        } else if (!e.bloqueado && Random.nextDouble() < 0.1) {
            lecturasTapaAbierta = 3
            aperturas++
        }
        val tapaAbierta = lecturasTapaAbierta > 0

        // Calefactor con histéresis: enciende cerca del umbral inferior y apaga 1 °C más arriba
        val calefactor = when {
            e.tempViales < inferior + 0.3 -> true
            e.tempViales > inferior + 1.0 -> false
            else -> e.calefactorEncendido
        }

        // Viales: tienden a 4,5 °C con variación aleatoria; el calefactor y la tapa abierta los calientan
        val variacion = if (e.modoDemo) 0.8 else 0.3
        var tv = e.tempViales + 0.1 * (TEMP_VIALES_NORMAL - e.tempViales) + aleatorio(variacion)
        if (calefactor) tv += 0.4
        if (tapaAbierta) tv += 0.5

        val tr = -2.0 + aleatorio(0.3)
        val te = 26.0 + aleatorio(0.5)
        val humedad = 40.0 + aleatorio(3.0)

        // Ventilador: circula el aire cuando hay mucha diferencia entre viales y refrigerante
        val ventilador = abs(tv - tr) > GRADIENTE_MAXIMO

        // Estado general
        val estado = when {
            tv < inferior || tv > superior -> Estado.EXCURSION
            tv < inferior + 0.5 || tv > superior - 0.5 || tapaAbierta -> Estado.MARGINAL
            else -> Estado.CONFORME
        }
        if (estado == Estado.EXCURSION) segundosFuera += INTERVALO_MS / 1000

        estadoActual = e.copy(
            tempViales = redondear(tv),
            tempRefrigerante = redondear(tr),
            tempExterior = redondear(te),
            humedad = Math.round(humedad).toDouble(),
            estadoGeneral = estado,
            calefactorEncendido = calefactor,
            ventiladorEncendido = ventilador,
            tapaAbierta = tapaAbierta,
            minutosFuera = (segundosFuera / 60).toInt(),
            aperturas = aperturas,
            secuencia = e.secuencia + INTERVALO_MS / 1000,
            timestamp = System.currentTimeMillis()
        )
        observadores.toList().forEach { it(estadoActual) }
    }

    // Número aleatorio entre -amplitud y +amplitud
    private fun aleatorio(amplitud: Double): Double = (Random.nextDouble() * 2.0 - 1.0) * amplitud

    // Dos decimales, como la trama del Arduino
    private fun redondear(valor: Double): Double = (valor * 100.0).roundToLong() / 100.0

    companion object {
        private const val INTERVALO_MS = 2000L
        private const val TEMP_VIALES_NORMAL = 4.5
        private const val GRADIENTE_MAXIMO = 3.0
        private const val DEMO_INFERIOR = 4.0
        private const val DEMO_SUPERIOR = 6.0
    }
}
