package cl.inacap.ti3042.cadenafrio

import java.util.Locale
import java.util.UUID

/**
 * Protocolo de texto del contenedor (el mismo del firmware del Arduino).
 *
 * - Telemetría: una línea JSON por trama, terminada en '\n'.
 *   {"tv":4.25,"tr":-1.80,"te":28.40,"h":35,"st":0,"cal":0,"ven":1,"cer":1,
 *    "tap":0,"mf":0,"ap":2,"dm":0,"sq":123}
 * - Comandos: texto plano terminado en '\n' (LOCK, UNLOCK, SIL, DEMO ON,
 *   DEMO OFF, SET INF <centésimas>, SET SUP <centésimas>).
 *
 * Lo usan BluetoothDatos (lado monitor), ServidorContenedor (teléfono que hace
 * de contenedor) y SimuladorDatos.
 */
object Protocolo {

    /** UUID estándar del perfil serie SPP, el mismo que anuncia el HC-06. */
    val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Rangos aceptados para los umbrales (en °C), los mismos de los sliders
    private val RANGO_INF = -5.0..10.0
    private val RANGO_SUP = 0.0..20.0

    // Pares "clave":número de un objeto JSON plano como el de la trama
    private val patronPar = Regex("\"(\\w+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val clavesObligatorias = listOf("tv", "tr", "te", "h", "st")

    /** Convierte un estado en la línea JSON de telemetría (sin el '\n'). */
    fun aJson(e: EstadoSistema): String = String.format(
        Locale.US,
        "{\"tv\":%.2f,\"tr\":%.2f,\"te\":%.2f,\"h\":%.0f,\"st\":%d,\"cal\":%d,\"ven\":%d," +
            "\"cer\":%d,\"tap\":%d,\"mf\":%d,\"ap\":%d,\"dm\":%d,\"sq\":%d}",
        e.tempViales, e.tempRefrigerante, e.tempExterior, e.humedad, e.estadoGeneral.codigo,
        bit(e.calefactorEncendido), bit(e.ventiladorEncendido), bit(e.bloqueado), bit(e.tapaAbierta),
        e.minutosFuera, e.aperturas, bit(e.modoDemo), e.secuencia
    )

    /**
     * Interpreta una línea de telemetría. Devuelve null si la línea no es una
     * trama JSON válida (por ejemplo, un texto "OK ..." o datos incompletos).
     * Los campos que no viajan en la trama (umbrales) se toman de [base].
     */
    fun desdeJson(linea: String, base: EstadoSistema): EstadoSistema? {
        val texto = linea.trim()
        if (!texto.startsWith("{") || !texto.endsWith("}")) return null
        val valores = patronPar.findAll(texto).associate { it.groupValues[1] to it.groupValues[2].toDouble() }
        if (clavesObligatorias.any { it !in valores }) return null

        // Las banderas llegan como 0 o 1
        fun bandera(clave: String, porDefecto: Boolean) = valores[clave]?.let { it == 1.0 } ?: porDefecto

        return base.copy(
            tempViales = valores.getValue("tv"),
            tempRefrigerante = valores.getValue("tr"),
            tempExterior = valores.getValue("te"),
            humedad = valores.getValue("h"),
            estadoGeneral = Estado.desdeCodigo(valores.getValue("st").toInt()),
            calefactorEncendido = bandera("cal", base.calefactorEncendido),
            ventiladorEncendido = bandera("ven", base.ventiladorEncendido),
            bloqueado = bandera("cer", base.bloqueado),
            tapaAbierta = bandera("tap", base.tapaAbierta),
            minutosFuera = valores["mf"]?.toInt() ?: base.minutosFuera,
            aperturas = valores["ap"]?.toInt() ?: base.aperturas,
            modoDemo = bandera("dm", base.modoDemo),
            secuencia = valores["sq"]?.toLong() ?: base.secuencia,
            timestamp = System.currentTimeMillis()
        )
    }

    /** Deja el comando en mayúsculas y con un solo espacio entre palabras. */
    fun normalizar(comando: String): String =
        comando.trim().uppercase(Locale.ROOT).split(Regex("\\s+")).joinToString(" ")

    /**
     * Aplica un comando "SET INF <centésimas>" o "SET SUP <centésimas>" al estado.
     * Si el comando no es un SET válido o el valor está fuera de rango, devuelve
     * el estado sin cambios. Ejemplo: "SET INF 250" deja el umbral inferior en 2,50 °C.
     */
    fun aplicarSet(comando: String, estado: EstadoSistema): EstadoSistema {
        val partes = normalizar(comando).split(" ")
        if (partes.size != 3 || partes[0] != "SET") return estado
        val valor = partes[2].toIntOrNull()?.div(100.0) ?: return estado
        return when {
            partes[1] == "INF" && valor in RANGO_INF -> estado.copy(umbralInferior = valor)
            partes[1] == "SUP" && valor in RANGO_SUP -> estado.copy(umbralSuperior = valor)
            else -> estado
        }
    }

    private fun bit(valor: Boolean): Int = if (valor) 1 else 0
}
