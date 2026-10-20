package cl.inacap.ti3042.cadenafrio

/**
 * Estado general del contenedor, igual al campo "st" de la telemetría del Arduino.
 */
enum class Estado(val codigo: Int) {
    CONFORME(0),
    MARGINAL(1),
    EXCURSION(2);

    companion object {
        /** Convierte el código numérico "st" de la trama en el enum. */
        fun desdeCodigo(codigo: Int): Estado = entries.firstOrNull { it.codigo == codigo } ?: EXCURSION
    }
}

/**
 * Fotografía del sistema en un instante. Cada campo de telemetría indica
 * entre paréntesis la clave de la trama JSON que envía el Arduino por el HC-06:
 * {"tv":4.25,"tr":-1.80,"te":28.40,"h":35,"st":0,"cal":0,"ven":1,"cer":1,
 *  "tap":0,"mf":0,"ap":2,"dm":0,"sq":123}
 */
data class EstadoSistema(
    // --- Lecturas de sensores ---
    val tempViales: Double = 5.0,          // tv  DS18B20 en la zona de viales (°C)
    val tempRefrigerante: Double = -2.0,   // tr  DS18B20 junto a la unidad refrigerante (°C)
    val tempExterior: Double = 26.0,       // te  DS18B20 exterior (°C)
    val humedad: Double = 40.0,            // h   DHT22, humedad exterior (%)

    // --- Estado y actuadores ---
    val estadoGeneral: Estado = Estado.CONFORME, // st
    val calefactorEncendido: Boolean = false,    // cal resistencia calefactora
    val ventiladorEncendido: Boolean = false,    // ven ventilador de circulación
    val bloqueado: Boolean = true,               // cer cerradura con servo MG90S
    val tapaAbierta: Boolean = false,            // tap
    val modoDemo: Boolean = false,               // dm

    // --- Acumulados que lleva el propio Arduino ---
    val minutosFuera: Int = 0,             // mf  minutos acumulados fuera de rango
    val aperturas: Int = 0,                // ap  aperturas de tapa

    // --- Número de trama ---
    val secuencia: Long = 0L,              // sq  contador que avanza cada segundo

    // --- Configuración enviada desde la app (no viaja en la telemetría) ---
    val umbralInferior: Double = 2.0,      // SET INF
    val umbralSuperior: Double = 8.0,      // SET SUP

    // Momento en que la app recibió la lectura
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /** Valor que el Arduino envía cuando una sonda DS18B20 está desconectada. */
        const val SONDA_DESCONECTADA = -99.9

        /** Indica si una temperatura corresponde a una sonda conectada. */
        fun sondaConectada(temperatura: Double): Boolean = temperatura > SONDA_DESCONECTADA + 0.05
    }
}
