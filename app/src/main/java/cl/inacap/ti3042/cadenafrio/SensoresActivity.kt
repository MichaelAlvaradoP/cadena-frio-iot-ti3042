package cl.inacap.ti3042.cadenafrio

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import cl.inacap.ti3042.cadenafrio.databinding.ActivitySensoresBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla 4: información de los sensores y del estado del contenedor.
 * Se suscribe a RepositorioEstado, que le entrega una lectura nueva cada 2 s;
 * no sabe si los datos vienen del simulador o del HC-06.
 */
class SensoresActivity : AppCompatActivity() {

    // Instancia de ViewBinding para acceder a las vistas del layout activity_sensores.xml
    private lateinit var binding: ActivitySensoresBinding

    // Formateador de hora en formato HH:mm:ss
    private val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Observador que recibe cada EstadoSistema (las fuentes notifican en el hilo principal)
    private val observadorEstado: ObservadorEstado = { estado -> actualizarInterfaz(estado) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Control de acceso: sin sesión iniciada se vuelve al login
        if (!Sesion.exigir(this)) return

        // Inflar la vista usando ViewBinding
        binding = ActivitySensoresBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navegación con Intent explícito hacia la pantalla 5 (ControlActivity)
        binding.btnControl.setOnClickListener {
            val intent = Intent(this, ControlActivity::class.java)
            startActivity(intent)
        }

        // Navegación con Intent explícito hacia el historial guardado en SQLite
        binding.btnHistorial.setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Asegurar que la fuente de datos esté entregando lecturas
        RepositorioEstado.iniciar()
        // Suscribirse para recibir las actualizaciones periódicas
        RepositorioEstado.registrarObservador(observadorEstado)
    }

    override fun onPause() {
        super.onPause()
        // Cancelar la suscripción al dejar la pantalla para no actualizar vistas ocultas
        RepositorioEstado.quitarObservador(observadorEstado)
    }

    /**
     * Refleja en pantalla el EstadoSistema recibido.
     */
    private fun actualizarInterfaz(estado: EstadoSistema) {
        // Fuente recién cambiada (por ejemplo, Bluetooth conectando): aún no hay lecturas
        if (estado.timestamp == 0L) {
            mostrarSinDatos()
            return
        }

        // 1. Estado general: semáforo, texto y fondo de la tarjeta
        val (colorRes, textoRes) = when (estado.estadoGeneral) {
            Estado.CONFORME -> R.color.estadoConforme to R.string.estado_conforme
            Estado.MARGINAL -> R.color.estadoMarginal to R.string.estado_marginal
            Estado.EXCURSION -> R.color.estadoExcursion to R.string.estado_excursion
        }
        val colorEstado = ContextCompat.getColor(this, colorRes)
        binding.vSemaforo.background?.mutate()?.setTint(colorEstado)
        binding.tvEstado.setText(textoRes)
        binding.tvEstado.setTextColor(colorEstado)

        // Fondo rojo tenue solo durante una excursión
        val fondoRes = if (estado.estadoGeneral == Estado.EXCURSION) R.color.fondoExcursion else R.color.white
        binding.cardEstado.setCardBackgroundColor(ContextCompat.getColor(this, fondoRes))

        // 2. Fuente de datos y hora de la lectura
        val formatoFuente = if (estado.modoDemo) R.string.formato_fuente_demo else R.string.formato_fuente
        binding.tvFuente.text = getString(formatoFuente, RepositorioEstado.nombreFuente())
        binding.tvMarcaTiempo.text = getString(R.string.formato_ultima_lectura, formatoHora.format(Date(estado.timestamp)))

        // 3. Lecturas de sensores (-99.9 = sonda desconectada)
        mostrarTemperatura(binding.tvValViales, estado.tempViales)
        mostrarTemperatura(binding.tvValRefrig, estado.tempRefrigerante)
        mostrarTemperatura(binding.tvValExterior, estado.tempExterior)
        binding.tvValHumedad.text = getString(R.string.formato_humedad, estado.humedad)

        // 4. Estado de actuadores
        binding.tvCalefactor.setText(if (estado.calefactorEncendido) R.string.estado_activo else R.string.estado_apagado)
        binding.tvVentilador.setText(if (estado.ventiladorEncendido) R.string.estado_activo else R.string.estado_apagado)
        binding.tvCerradura.setText(if (estado.bloqueado) R.string.estado_bloqueada else R.string.estado_liberada)
        binding.tvTapa.setText(if (estado.tapaAbierta) R.string.estado_abierta else R.string.estado_cerrada)

        // 5. Acumulados: los lleva el Arduino (mf, ap)
        binding.tvMinFuera.text = getString(R.string.formato_minutos, estado.minutosFuera)
        binding.tvAperturas.text = getString(R.string.formato_entero, estado.aperturas)
    }

    /** Deja la pantalla en "esperando datos" hasta que llegue la primera trama. */
    private fun mostrarSinDatos() {
        binding.vSemaforo.background?.mutate()?.setTint(ContextCompat.getColor(this, R.color.textoSecundario))
        binding.tvEstado.setText(R.string.estado_sin_datos)
        binding.tvEstado.setTextColor(ContextCompat.getColor(this, R.color.textoSecundario))
        binding.cardEstado.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
        binding.tvFuente.text = getString(R.string.formato_fuente, RepositorioEstado.nombreFuente())
        binding.tvMarcaTiempo.setText(R.string.valor_vacio)
        listOf(
            binding.tvValViales, binding.tvValRefrig, binding.tvValExterior, binding.tvValHumedad,
            binding.tvCalefactor, binding.tvVentilador, binding.tvCerradura, binding.tvTapa,
            binding.tvMinFuera, binding.tvAperturas
        ).forEach { it.setText(R.string.valor_vacio) }
    }

    /** Muestra una temperatura o el aviso de sonda desconectada. */
    private fun mostrarTemperatura(vista: TextView, temperatura: Double) {
        vista.text = if (EstadoSistema.sondaConectada(temperatura)) {
            getString(R.string.formato_temperatura, temperatura)
        } else {
            getString(R.string.sonda_desconectada)
        }
    }
}
