package cl.inacap.ti3042.cadenafrio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.io.IOException
import java.io.OutputStream

/**
 * "Modo contenedor": este teléfono se comporta como el Arduino del contenedor.
 *
 * Abre un servidor Bluetooth SPP y, cuando otro teléfono con la app se conecta
 * (BluetoothDatos), le envía las tramas JSON del simulador local cada 2 s y
 * ejecuta en el simulador los comandos que recibe (LOCK, UNLOCK, DEMO, SET...).
 *
 * Así se demuestra la interconexión de dos dispositivos Android; cuando esté el
 * HC-06, el monitor se conecta a él sin cambiar nada más.
 */
class ServidorContenedor(
    contexto: Context,
    private val alCambiarEnlace: (EstadoEnlace) -> Unit
) {

    private val contextoApp = contexto.applicationContext
    private val hiloPrincipal = Handler(Looper.getMainLooper())

    @Volatile private var activo = false
    @Volatile private var servidor: BluetoothServerSocket? = null
    @Volatile private var cliente: BluetoothSocket? = null
    @Volatile private var salida: OutputStream? = null

    private var hiloServidor: Thread? = null
    private var hiloEscritura: HandlerThread? = null
    private var escritor: Handler? = null

    // Cada estado local (del simulador) se reenvía al monitor conectado
    private val observadorLocal: ObservadorEstado = { estado -> enviarLinea(Protocolo.aJson(estado)) }

    fun estaActivo(): Boolean = activo

    fun iniciar() {
        if (activo) return
        activo = true
        hiloEscritura = HandlerThread("bt-servidor-escritura").also {
            it.start()
            escritor = Handler(it.looper)
        }
        hiloServidor = Thread({ bucleServidor() }, "bt-servidor").also { it.start() }
        RepositorioEstado.registrarObservador(observadorLocal)
    }

    fun detener() {
        activo = false
        RepositorioEstado.quitarObservador(observadorLocal)
        cerrar()
        hiloServidor?.interrupt()
        hiloServidor = null
        hiloEscritura?.quitSafely()
        hiloEscritura = null
        escritor = null
    }

    /** Acepta un monitor a la vez; cuando se desconecta, vuelve a esperar. */
    @SuppressLint("MissingPermission") // el permiso se verifica en ControlActivity y se captura SecurityException
    private fun bucleServidor() {
        val adaptador = contextoApp.getSystemService(BluetoothManager::class.java)?.adapter
        if (adaptador == null || !adaptador.isEnabled) {
            informar(EstadoEnlace.BLUETOOTH_APAGADO)
            activo = false
            return
        }
        try {
            servidor = adaptador.listenUsingRfcommWithServiceRecord(NOMBRE_SERVICIO, Protocolo.UUID_SPP)
            while (activo) {
                informar(EstadoEnlace.ESPERANDO_MONITOR)
                val socket = servidor?.accept() ?: break
                cliente = socket
                salida = socket.outputStream
                informar(EstadoEnlace.MONITOR_CONECTADO)
                atenderMonitor(socket)
                cerrarCliente()
            }
        } catch (e: SecurityException) {
            informar(EstadoEnlace.SIN_PERMISO)
            activo = false
        } catch (e: IOException) {
            // Servidor cerrado (detener) o Bluetooth apagado
        }
        cerrar()
    }

    /** Lee comandos del monitor hasta que se desconecte. */
    private fun atenderMonitor(socket: BluetoothSocket) {
        try {
            val lector = socket.inputStream.bufferedReader(Charsets.US_ASCII)
            while (activo) {
                val linea = lector.readLine() ?: return
                if (linea.isBlank()) continue
                // El comando se aplica al simulador en el hilo principal
                hiloPrincipal.post { RepositorioEstado.enviarComando(linea) }
            }
        } catch (e: IOException) {
            // El monitor se desconectó
        }
    }

    private fun enviarLinea(linea: String) {
        val bytes = (linea + "\n").toByteArray(Charsets.US_ASCII)
        escritor?.post {
            try {
                salida?.write(bytes)
                salida?.flush()
            } catch (e: IOException) {
                cerrarCliente()
            }
        }
    }

    private fun informar(enlace: EstadoEnlace) {
        hiloPrincipal.post { alCambiarEnlace(enlace) }
    }

    private fun cerrarCliente() {
        try {
            cliente?.close()
        } catch (e: IOException) {
            // Ya estaba cerrado
        }
        cliente = null
        salida = null
    }

    private fun cerrar() {
        cerrarCliente()
        try {
            servidor?.close()
        } catch (e: IOException) {
            // Ya estaba cerrado
        }
        servidor = null
    }

    companion object {
        private const val NOMBRE_SERVICIO = "ContenedorCadenaFrio"
    }
}
