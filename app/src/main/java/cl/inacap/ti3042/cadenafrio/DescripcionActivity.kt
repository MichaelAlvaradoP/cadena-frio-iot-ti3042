package cl.inacap.ti3042.cadenafrio

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.ti3042.cadenafrio.databinding.ActivityDescripcionBinding

/**
 * Actividad que describe el propósito y arquitectura del sistema contenedor IoT.
 * Infla ActivityDescripcionBinding y permite navegar hacia SensoresActivity.
 */
class DescripcionActivity : AppCompatActivity() {

    // Instancia de ViewBinding para acceder a las vistas del layout activity_descripcion.xml
    private lateinit var binding: ActivityDescripcionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Control de acceso: sin sesión iniciada se vuelve al login
        if (!Sesion.exigir(this)) return

        // Inflar el diseño mediante ViewBinding
        binding = ActivityDescripcionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuración del botón Siguiente para navegar a la pantalla de información de sensores
        binding.btnSiguienteDesc.setOnClickListener {
            // Navegación mediante Intent explícito a SensoresActivity
            val intent = Intent(this, SensoresActivity::class.java)
            startActivity(intent)
        }
    }
}
