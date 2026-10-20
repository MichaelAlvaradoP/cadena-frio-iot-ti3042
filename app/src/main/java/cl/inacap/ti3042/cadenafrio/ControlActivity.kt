package cl.inacap.ti3042.cadenafrio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.ti3042.cadenafrio.databinding.ActivityControlBinding
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/**
 * Pantalla 5: sistema de control.
 * Activa y desactiva funciones del contenedor enviando los comandos del
 * protocolo del Arduino (LOCK, UNLOCK, DEMO ON/OFF, SIL, SET INF/SUP)
 * a través de RepositorioEstado. El calefactor y el ventilador los gobierna
 * el propio Arduino, por lo que aquí solo se muestra su estado.
 */
class ControlActivity : AppCompatActivity() {

    // Instancia de ViewBinding para acceder a las vistas del layout activity_control.xml
    private lateinit var binding: ActivityControlBinding

    // Evita que los cambios hechos por código disparen los listeners del usuario
    private var actualizandoUIPorCodigo = false

    // Mientras hay un diálogo abierto no se tocan los switches desde el observador
    private var dialogoAbierto = false

    // true cuando el usuario movió un slider y aún no aplica: el observador no lo pisa
    private var editandoUmbrales = false

    // Observador del estado (las fuentes notifican en el hilo principal)
    private val observadorEstado: ObservadorEstado = { estado -> actualizarInterfazControl(estado) }

    // Observador del enlace inalámbrico (simulador, Bluetooth o modo contenedor)
    private val observadorEnlace: (EstadoEnlace) -> Unit = { enlace -> mostrarEnlace(enlace) }

    // Acción que espera a que el usuario conceda el permiso de Bluetooth
    private var accionPendiente: (() -> Unit)? = null

    // Pide BLUETOOTH_CONNECT (Android 12+) en tiempo de ejecución
    private val lanzadorPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        val accion = accionPendiente
        accionPendiente = null
        if (concedido && accion != null) {
            accion()
        } else {
            Toast.makeText(this, R.string.msg_permiso_denegado, Toast.LENGTH_LONG).show()
            reflejarModoContenedor()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Control de acceso: sin sesión iniciada se vuelve al login
        if (!Sesion.exigir(this)) return

        // Inflar el diseño mediante ViewBinding
        binding = ActivityControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarConexion()
        configurarComandos()
        configurarUmbrales()

        // Botón Anterior: vuelve a SensoresActivity con un Intent explícito.
        // FLAG_ACTIVITY_CLEAR_TOP cierra las pantallas que estén sobre SensoresActivity
        // en la pila (esta misma) y SINGLE_TOP reutiliza esa instancia en vez de recrearla.
        binding.btnAnterior.setOnClickListener {
            val intent = Intent(this, SensoresActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        RepositorioEstado.iniciar()
        // Suscribirse a los cambios de estado y del enlace en el repositorio
        RepositorioEstado.registrarObservador(observadorEstado)
        RepositorioEstado.registrarObservadorEnlace(observadorEnlace)
    }

    override fun onPause() {
        super.onPause()
        // Cancelar suscripción al salir o pausar la actividad
        RepositorioEstado.quitarObservador(observadorEstado)
        RepositorioEstado.quitarObservadorEnlace(observadorEnlace)
    }

    /**
     * Conexión inalámbrica:
     * - Monitor: conectarse por Bluetooth a un HC-06 u otro teléfono en modo contenedor.
     * - Contenedor: este teléfono emite su simulador por Bluetooth (interconexión de 2 Android).
     */
    private fun configurarConexion() {
        binding.btnConectarBluetooth.setOnClickListener { conBluetooth { elegirDispositivo() } }

        binding.btnUsarSimulador.setOnClickListener { RepositorioEstado.usarSimulador() }

        binding.swModoContenedor.setOnCheckedChangeListener { _, activar ->
            if (actualizandoUIPorCodigo) return@setOnCheckedChangeListener
            if (activar) {
                conBluetooth { RepositorioEstado.iniciarModoContenedor() }
            } else {
                RepositorioEstado.detenerModoContenedor()
            }
        }
    }

    /**
     * Ejecuta [accion] solo si hay adaptador Bluetooth encendido y permiso.
     * En Android 12+ pide BLUETOOTH_CONNECT si aún no está concedido.
     */
    private fun conBluetooth(accion: () -> Unit) {
        val adaptador = getSystemService(BluetoothManager::class.java)?.adapter
        when {
            adaptador == null -> {
                Toast.makeText(this, R.string.msg_sin_bluetooth, Toast.LENGTH_LONG).show()
                reflejarModoContenedor()
            }
            !adaptador.isEnabled -> {
                Toast.makeText(this, R.string.msg_bluetooth_apagado, Toast.LENGTH_LONG).show()
                reflejarModoContenedor()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED -> {
                accionPendiente = accion
                lanzadorPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            else -> accion()
        }
    }

    /** Lista los dispositivos ya emparejados en Ajustes y conecta al elegido. */
    @SuppressLint("MissingPermission") // conBluetooth ya verificó el permiso
    private fun elegirDispositivo() {
        val adaptador = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val dispositivos = try {
            adaptador.bondedDevices.toList()
        } catch (e: SecurityException) {
            emptyList()
        }
        if (dispositivos.isEmpty()) {
            Toast.makeText(this, R.string.msg_sin_emparejados, Toast.LENGTH_LONG).show()
            return
        }
        val nombres = dispositivos.map { "${it.name ?: it.address}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.dialogo_dispositivos_titulo)
            .setItems(nombres) { _, posicion -> RepositorioEstado.conectarBluetooth(dispositivos[posicion].address) }
            .setNegativeButton(R.string.btn_cancelar, null)
            .show()
    }

    private fun mostrarEnlace(enlace: EstadoEnlace) {
        binding.tvEstadoEnlace.setText(
            when (enlace) {
                EstadoEnlace.SIMULADOR -> R.string.enlace_simulador
                EstadoEnlace.CONECTANDO -> R.string.enlace_conectando
                EstadoEnlace.CONECTADO -> R.string.enlace_conectado
                EstadoEnlace.DESCONECTADO -> R.string.enlace_desconectado
                EstadoEnlace.SIN_PERMISO -> R.string.enlace_sin_permiso
                EstadoEnlace.BLUETOOTH_APAGADO -> R.string.enlace_bluetooth_apagado
                EstadoEnlace.ESPERANDO_MONITOR -> R.string.enlace_esperando_monitor
                EstadoEnlace.MONITOR_CONECTADO -> R.string.enlace_monitor_conectado
            }
        )
        reflejarModoContenedor()
    }

    /** Ajusta el switch de modo contenedor a lo que realmente está activo. */
    private fun reflejarModoContenedor() {
        actualizandoUIPorCodigo = true
        binding.swModoContenedor.isChecked = RepositorioEstado.modoContenedorActivo()
        actualizandoUIPorCodigo = false
    }

    /** Switches y botones que envían comandos directos al contenedor. */
    private fun configurarComandos() {
        // Cerradura: bloquear es inmediato; liberar pide confirmación (acción crítica)
        binding.swCerradura.setOnCheckedChangeListener { _, bloquear ->
            if (actualizandoUIPorCodigo) return@setOnCheckedChangeListener
            if (bloquear) {
                enviar("LOCK")
            } else {
                confirmarDesbloqueo()
            }
        }

        // Modo demostración: umbrales estrechos para mostrar cambios de estado en clases
        binding.swModoDemo.setOnCheckedChangeListener { _, activar ->
            if (actualizandoUIPorCodigo) return@setOnCheckedChangeListener
            enviar(if (activar) "DEMO ON" else "DEMO OFF")
        }

        // Silencia el zumbador por 5 minutos
        binding.btnSilenciar.setOnClickListener { enviar("SIL") }
    }

    /** Diálogo de confirmación antes de liberar la cerradura. */
    private fun confirmarDesbloqueo() {
        dialogoAbierto = true
        AlertDialog.Builder(this)
            .setTitle(R.string.dialogo_desbloqueo_titulo)
            .setMessage(R.string.dialogo_desbloqueo_mensaje)
            .setPositiveButton(R.string.btn_confirmar) { _, _ -> enviar("UNLOCK") }
            .setNegativeButton(R.string.btn_cancelar) { _, _ ->
                // Se revierte el switch sin volver a disparar el listener
                actualizandoUIPorCodigo = true
                binding.swCerradura.isChecked = true
                actualizandoUIPorCodigo = false
            }
            .setOnDismissListener { dialogoAbierto = false }
            .setCancelable(false)
            .show()
    }

    /** Sliders de umbrales y botón Aplicar (SET INF / SET SUP). */
    private fun configurarUmbrales() {
        // Las etiquetas muestran el valor del slider; mover uno marca edición en curso
        val alCambiar = Slider.OnChangeListener { _, _, desdeUsuario ->
            actualizarEtiquetasUmbrales()
            if (desdeUsuario) editandoUmbrales = true
        }
        binding.sliderInferior.addOnChangeListener(alCambiar)
        binding.sliderSuperior.addOnChangeListener(alCambiar)
        actualizarEtiquetasUmbrales()

        binding.btnAplicarUmbrales.setOnClickListener {
            val inferior = binding.sliderInferior.value
            val superior = binding.sliderSuperior.value

            if (inferior >= superior) {
                Toast.makeText(this, R.string.error_umbrales, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Los umbrales viajan en centésimas de grado (200 = 2,00 °C)
            val comandoInf = "SET INF ${centesimas(inferior)}"
            val comandoSup = "SET SUP ${centesimas(superior)}"
            RepositorioEstado.enviarComando(comandoInf)
            RepositorioEstado.enviarComando(comandoSup)
            binding.tvUltimoComando.text = getString(R.string.formato_ultimo_comando, "$comandoInf · $comandoSup")
            editandoUmbrales = false
        }
    }

    /** Escribe en las etiquetas el valor actual de cada slider. */
    private fun actualizarEtiquetasUmbrales() {
        binding.tvLabelInferior.text = getString(R.string.formato_umbral_inferior, binding.sliderInferior.value)
        binding.tvLabelSuperior.text = getString(R.string.formato_umbral_superior, binding.sliderSuperior.value)
    }

    private fun centesimas(valor: Float): Int = (valor * 100f).roundToInt()

    /** Envía un comando y lo muestra como último comando. */
    private fun enviar(comando: String) {
        RepositorioEstado.enviarComando(comando)
        binding.tvUltimoComando.text = getString(R.string.formato_ultimo_comando, comando)
    }

    /**
     * Refleja el estado actual sin disparar los listeners del usuario.
     * No pisa los switches con un diálogo abierto ni los sliders en edición.
     */
    private fun actualizarInterfazControl(estado: EstadoSistema) {
        actualizandoUIPorCodigo = true

        if (!dialogoAbierto) {
            binding.swCerradura.isChecked = estado.bloqueado
            binding.swModoDemo.isChecked = estado.modoDemo
        }

        binding.tvCalefactorEstado.setText(if (estado.calefactorEncendido) R.string.estado_activo else R.string.estado_apagado)
        binding.tvVentiladorEstado.setText(if (estado.ventiladorEncendido) R.string.estado_activo else R.string.estado_apagado)

        if (!editandoUmbrales) {
            fijarSlider(binding.sliderInferior, estado.umbralInferior)
            fijarSlider(binding.sliderSuperior, estado.umbralSuperior)
            actualizarEtiquetasUmbrales()
        }

        actualizandoUIPorCodigo = false
    }

    /**
     * Asigna un valor al slider ajustándolo a su rango y a su paso: el Slider de
     * Material lanza una excepción si el valor no es múltiplo de stepSize.
     */
    private fun fijarSlider(slider: Slider, valor: Double) {
        val pasos = ((valor.toFloat() - slider.valueFrom) / slider.stepSize).roundToInt()
        val ajustado = (slider.valueFrom + pasos * slider.stepSize).coerceIn(slider.valueFrom, slider.valueTo)
        if (slider.value != ajustado) slider.value = ajustado
    }
}
