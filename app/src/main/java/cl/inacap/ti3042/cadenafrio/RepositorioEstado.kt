package cl.inacap.ti3042.cadenafrio

import android.content.Context

/**
 * Punto único de acceso a los datos del contenedor.
 *
 * Mantiene el último estado recibido y la fuente activa: el simulador interno o
 * BluetoothDatos (HC-06 u otro teléfono en modo contenedor). Las Activities solo
 * hablan con este objeto; nunca crean ni manejan una FuenteDatos. Así, cambiar el
 * simulador por Bluetooth no toca ninguna pantalla.
 *
 * Todas las llamadas deben hacerse en el hilo principal: las fuentes notifican
 * en ese hilo (BluetoothDatos reenvía sus lecturas con un Handler).
 */
object RepositorioEstado {

    // Se conserva una sola instancia del simulador para no perder su estado al volver a él
    private val simulador = SimuladorDatos()
    private var fuenteActual: FuenteDatos = simulador
    private val observadoresGlobales = mutableListOf<ObservadorEstado>()
    private var ultimoEstado: EstadoSistema = EstadoSistema()

    // Conexión inalámbrica
    private var contextoApp: Context? = null
    private var servidor: ServidorContenedor? = null
    private var estadoEnlace = EstadoEnlace.SIMULADOR
    private val observadoresEnlace = mutableListOf<(EstadoEnlace) -> Unit>()

    // Historial en SQLite (se asigna en inicializar)
    private var baseDatos: BaseDatos? = null
    private var insercionesDesdePoda = 0

    // Recibe cada lectura de la fuente activa, la guarda y la reparte a las pantallas
    private val observadorInterno: ObservadorEstado = { nuevoEstado ->
        ultimoEstado = nuevoEstado
        guardarEnHistorial(nuevoEstado)
        notificarObservadoresGlobales(nuevoEstado)
    }

    init {
        fuenteActual.registrarObservador(observadorInterno)
    }

    /** Lo llama CadenaFrioApp al abrir la app, para disponer de la base de datos. */
    fun inicializar(contexto: Context) {
        contextoApp = contexto.applicationContext
        if (baseDatos == null) baseDatos = BaseDatos(contexto.applicationContext)
    }

    /** Base de datos local de la app (usuarios e historial). */
    fun baseDatos(): BaseDatos? = baseDatos

    private fun guardarEnHistorial(estado: EstadoSistema) {
        val bd = baseDatos ?: return
        bd.insertarLectura(estado)
        insercionesDesdePoda++
        if (insercionesDesdePoda >= 100) {
            bd.podarLecturas(MAX_LECTURAS)
            insercionesDesdePoda = 0
        }
    }

    /** Últimas lecturas guardadas en SQLite, de la más nueva a la más antigua. */
    fun historial(limite: Int): List<EstadoSistema> = baseDatos?.ultimasLecturas(limite).orEmpty()

    fun cantidadEnHistorial(): Long = baseDatos?.cantidadLecturas() ?: 0L

    // Máximo de lecturas que se conservan (a 1 lectura cada 2 s, unas 2,8 horas)
    private const val MAX_LECTURAS = 5000

    /** Arranca la fuente activa (no hace nada si ya está corriendo). */
    fun iniciar() {
        fuenteActual.iniciar()
    }

    /** Suscribe una pantalla a las lecturas; recibe de inmediato la última conocida. */
    fun registrarObservador(observador: ObservadorEstado) {
        if (!observadoresGlobales.contains(observador)) {
            observadoresGlobales.add(observador)
            observador(ultimoEstado)
        }
    }

    fun quitarObservador(observador: ObservadorEstado) {
        observadoresGlobales.remove(observador)
    }

    /** Envía un comando del protocolo a la fuente activa. */
    fun enviarComando(comando: String) {
        fuenteActual.enviarComando(comando)
    }

    /**
     * Reemplaza la fuente de datos (por ejemplo, SimuladorDatos → BluetoothDatos).
     * Las pantallas siguen suscritas sin enterarse del cambio.
     */
    fun cambiarFuenteDatos(nuevaFuente: FuenteDatos) {
        fuenteActual.quitarObservador(observadorInterno)
        fuenteActual.detener()

        // Hasta que llegue la primera trama de la nueva fuente no hay datos que mostrar
        ultimoEstado = EstadoSistema(timestamp = 0L)
        notificarObservadoresGlobales(ultimoEstado)

        fuenteActual = nuevaFuente
        fuenteActual.registrarObservador(observadorInterno)
        fuenteActual.iniciar()
    }

    fun nombreFuente(): String = fuenteActual.nombreFuente()

    // ---------------- Conexión inalámbrica ----------------

    /**
     * Modo monitor: recibe los datos de un dispositivo Bluetooth emparejado
     * (HC-06 del Arduino u otro teléfono en modo contenedor).
     */
    fun conectarBluetooth(direccion: String) {
        val contexto = contextoApp ?: return
        detenerModoContenedor()
        lateinit var fuente: BluetoothDatos
        // Se ignoran avisos tardíos de una conexión que ya fue reemplazada
        fuente = BluetoothDatos(contexto, direccion) { enlace -> if (fuenteActual === fuente) actualizarEnlace(enlace) }
        cambiarFuenteDatos(fuente)
    }

    /** Vuelve al simulador interno (y deja de emitir como contenedor). */
    fun usarSimulador() {
        detenerModoContenedor()
        if (fuenteActual !== simulador) cambiarFuenteDatos(simulador)
        actualizarEnlace(EstadoEnlace.SIMULADOR)
    }

    /**
     * Modo contenedor: este teléfono usa el simulador y lo emite por Bluetooth
     * con el protocolo del Arduino, para que otro teléfono lo monitoree y controle.
     */
    fun iniciarModoContenedor() {
        val contexto = contextoApp ?: return
        if (servidor != null) return
        if (fuenteActual !== simulador) cambiarFuenteDatos(simulador)
        simulador.iniciar()
        lateinit var nuevo: ServidorContenedor
        nuevo = ServidorContenedor(contexto) { enlace -> if (servidor === nuevo) actualizarEnlace(enlace) }
        servidor = nuevo
        nuevo.iniciar()
    }

    fun detenerModoContenedor() {
        servidor?.detener()
        servidor = null
        if (fuenteActual === simulador) actualizarEnlace(EstadoEnlace.SIMULADOR)
    }

    fun modoContenedorActivo(): Boolean = servidor != null

    /** Suscribe a los cambios del enlace; recibe de inmediato el estado actual. */
    fun registrarObservadorEnlace(observador: (EstadoEnlace) -> Unit) {
        if (!observadoresEnlace.contains(observador)) {
            observadoresEnlace.add(observador)
            observador(estadoEnlace)
        }
    }

    fun quitarObservadorEnlace(observador: (EstadoEnlace) -> Unit) {
        observadoresEnlace.remove(observador)
    }

    private fun actualizarEnlace(enlace: EstadoEnlace) {
        estadoEnlace = enlace
        observadoresEnlace.toList().forEach { it(enlace) }
    }

    private fun notificarObservadoresGlobales(estado: EstadoSistema) {
        // Copia de la lista: una pantalla puede desuscribirse durante la notificación
        observadoresGlobales.toList().forEach { it(estado) }
    }
}
