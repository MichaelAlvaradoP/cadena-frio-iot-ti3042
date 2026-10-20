package cl.inacap.ti3042.cadenafrio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.io.IOException
import java.io.OutputStream

/**
 * Fuente de datos real por Bluetooth clásico (perfil serie SPP).
 *
 * Se conecta a un dispositivo ya emparejado en los ajustes de Android: el
 * módulo HC-06 del Arduino, u otro teléfono con esta app en "modo contenedor"
 * (ServidorContenedor), que habla exactamente el mismo protocolo.
 *
 * - Un hilo de lectura recibe líneas terminadas en '\n' y convierte cada trama
 *   JSON en EstadoSistema con Protocolo.desdeJson.
 * - Los comandos se escriben en otro hilo (HandlerThread) para no bloquear la UI.
 * - Los observadores se notifican siempre en el hilo principal.
 * - Si la conexión se corta, el enlace queda "desconectado" y el usuario vuelve
 *   a conectar desde la pantalla de control.
 *
 * El socket es "seguro" (createRfcommSocketToServiceRecord): exige dispositivos
 * emparejados y cifra el enlace Bluetooth.
 */
class BluetoothDatos(
    contexto: Context,
    private val direccion: String,
    private val alCambiarEnlace: (EstadoEnlace) -> Unit
) : FuenteDatos {

    private val contextoApp = contexto.applicationContext
    private val hiloPrincipal = Handler(Looper.getMainLooper())
    private val observadores = mutableListOf<ObservadorEstado>()

    @Volatile private var ejecutando = false
    @Volatile private var conectada = false
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var salida: OutputStream? = null

    private var hiloEscritura: HandlerThread? = null
    private var escritor: Handler? = null

    // Solo se lee y modifica en el hilo principal
    private var estadoActual = EstadoSistema(timestamp = 0L)
    private var nombreDispositivo = direccion

    override fun iniciar() {
        if (ejecutando) return
        ejecutando = true

        hiloEscritura = HandlerThread("bt-escritura").also {
            it.start()
            escritor = Handler(it.looper)
        }
        Thread({ conectarYLeer() }, "bt-lectura").start()
    }

    override fun detener() {
        ejecutando = false
        conectada = false
        cerrarSocket()
        hiloEscritura?.quitSafely()
        hiloEscritura = null
        escritor = null
    }

    override fun registrarObservador(observador: ObservadorEstado) {
        if (!observadores.contains(observador)) {
            observadores.add(observador)
            // Solo se entrega el estado si ya llegó al menos una trama
            if (estadoActual.timestamp > 0L) observador(estadoActual)
        }
    }

    override fun quitarObservador(observador: ObservadorEstado) {
        observadores.remove(observador)
    }

    /** Envía el comando como texto plano terminado en '\n'. */
    override fun enviarComando(comando: String) {
        // Los umbrales no viajan en la trama: la app recuerda los que envió
        estadoActual = Protocolo.aplicarSet(comando, estadoActual)

        val linea = Protocolo.normalizar(comando) + "\n"
        escritor?.post {
            try {
                salida?.write(linea.toByteArray(Charsets.US_ASCII))
                salida?.flush()
            } catch (e: IOException) {
                // La conexión se cayó: el hilo de lectura lo detecta
                cerrarSocket()
            }
        }
    }

    override fun estaConectada(): Boolean = conectada

    override fun nombreFuente(): String = "Bluetooth · $nombreDispositivo"

    /** Abre el socket SPP y lee tramas hasta que la conexión se cierre. */
    @SuppressLint("MissingPermission") // el permiso se verifica en ControlActivity y se captura SecurityException
    private fun conectarYLeer() {
        val adaptador = contextoApp.getSystemService(BluetoothManager::class.java)?.adapter
        if (adaptador == null || !adaptador.isEnabled) {
            informar(EstadoEnlace.BLUETOOTH_APAGADO)
            return
        }
        try {
            informar(EstadoEnlace.CONECTANDO)
            val dispositivo = adaptador.getRemoteDevice(direccion)
            dispositivo.name?.let { nombre -> hiloPrincipal.post { nombreDispositivo = nombre } }
            val nuevo = dispositivo.createRfcommSocketToServiceRecord(Protocolo.UUID_SPP)
            socket = nuevo
            nuevo.connect()
            salida = nuevo.outputStream
            conectada = true
            informar(EstadoEnlace.CONECTADO)
            leerLineas(nuevo)
        } catch (e: SecurityException) {
            informar(EstadoEnlace.SIN_PERMISO)
            return
        } catch (e: IOException) {
            // Conexión rechazada o cortada
        } catch (e: IllegalArgumentException) {
            // Dirección MAC inválida
        }
        conectada = false
        cerrarSocket()
        if (ejecutando) informar(EstadoEnlace.DESCONECTADO)
    }

    /** Lee líneas del socket hasta que se cierre. */
    private fun leerLineas(s: BluetoothSocket) {
        val lector = s.inputStream.bufferedReader(Charsets.US_ASCII)
        while (ejecutando) {
            val linea = lector.readLine() ?: return   // null: el otro extremo cerró
            hiloPrincipal.post { procesarLinea(linea) }
        }
    }

    /** Se ejecuta en el hilo principal. Las líneas que no son tramas JSON se ignoran. */
    private fun procesarLinea(linea: String) {
        val nuevo = Protocolo.desdeJson(linea, estadoActual) ?: return
        estadoActual = nuevo
        observadores.toList().forEach { it(estadoActual) }
    }

    private fun informar(enlace: EstadoEnlace) {
        hiloPrincipal.post { alCambiarEnlace(enlace) }
    }

    private fun cerrarSocket() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // Ya estaba cerrado
        }
        socket = null
        salida = null
    }
}
