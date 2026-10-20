package cl.inacap.ti3042.cadenafrio

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.ti3042.cadenafrio.databinding.ActivityHistorialBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historial de lecturas: consulta las últimas tramas guardadas en SQLite.
 * Se abre desde SensoresActivity y se refresca con cada lectura nueva.
 */
class HistorialActivity : AppCompatActivity() {

    // Instancia de ViewBinding para acceder a las vistas del layout activity_historial.xml
    private lateinit var binding: ActivityHistorialBinding
    private lateinit var adaptador: ArrayAdapter<String>

    private val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Cada trama nueva ya quedó guardada por RepositorioEstado: se vuelve a consultar
    private val observadorEstado: ObservadorEstado = { cargarHistorial() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Control de acceso: sin sesión iniciada se vuelve al login
        if (!Sesion.exigir(this)) return

        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adaptador = ArrayAdapter(this, R.layout.item_lectura, mutableListOf())
        binding.lvLecturas.adapter = adaptador

        // Anterior: vuelve a SensoresActivity reutilizando la instancia de la pila
        binding.btnAnteriorHistorial.setOnClickListener {
            val intent = Intent(this, SensoresActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        RepositorioEstado.registrarObservador(observadorEstado)
    }

    override fun onPause() {
        super.onPause()
        RepositorioEstado.quitarObservador(observadorEstado)
    }

    /** Consulta SQLite y muestra las últimas lecturas, de la más nueva a la más antigua. */
    private fun cargarHistorial() {
        val lecturas = RepositorioEstado.historial(LECTURAS_VISIBLES)
        binding.tvResumenHistorial.text = getString(
            R.string.formato_resumen_historial,
            RepositorioEstado.cantidadEnHistorial(),
            lecturas.size
        )
        adaptador.clear()
        adaptador.addAll(lecturas.map { formatear(it) })
    }

    private fun formatear(e: EstadoSistema): String = getString(
        R.string.formato_fila_historial,
        formatoHora.format(Date(e.timestamp)),
        textoEstado(e.estadoGeneral),
        temperatura(e.tempViales),
        temperatura(e.tempRefrigerante),
        temperatura(e.tempExterior),
        e.humedad
    )

    private fun temperatura(valor: Double): String =
        if (EstadoSistema.sondaConectada(valor)) getString(R.string.formato_temperatura, valor)
        else getString(R.string.sonda_desconectada)

    private fun textoEstado(estado: Estado): String = getString(
        when (estado) {
            Estado.CONFORME -> R.string.estado_conforme
            Estado.MARGINAL -> R.string.estado_marginal
            Estado.EXCURSION -> R.string.estado_excursion
        }
    )

    companion object {
        private const val LECTURAS_VISIBLES = 50
    }
}
