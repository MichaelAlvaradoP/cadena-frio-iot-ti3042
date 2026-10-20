package cl.inacap.ti3042.cadenafrio

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.ti3042.cadenafrio.databinding.ActivityPortadaBinding

/**
 * Actividad Portada (Pantalla principal / LAUNCHER).
 * Infla el ViewBinding ActivityPortadaBinding y configura la navegación hacia LoginActivity.
 */
class PortadaActivity : AppCompatActivity() {

    // Instancia de ViewBinding para acceder a las vistas del layout activity_portada.xml
    private lateinit var binding: ActivityPortadaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflar el diseño mediante ViewBinding
        binding = ActivityPortadaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar el evento OnClickListener en btnSiguiente para navegar a LoginActivity
        binding.btnSiguiente.setOnClickListener {
            // Navegación con Intent explícito: se especifica directamente la clase destino LoginActivity.
            // Los Intents explícitos garantizan que la intención sea atendida por una componente específica dentro de la app.
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}
