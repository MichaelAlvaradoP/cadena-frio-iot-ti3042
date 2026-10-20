package cl.inacap.ti3042.cadenafrio

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.ti3042.cadenafrio.databinding.ActivityLoginBinding

/**
 * Pantalla 2: control de acceso local.
 *
 * Valida usuario y contraseña contra la tabla "usuarios" de SQLite, donde solo
 * existe el hash PBKDF2 con sal de cada contraseña. No hay credenciales escritas
 * en la app: si todavía no existe ningún usuario, se ofrece crearlo (registro en
 * primer uso). Tras varios intentos fallidos el acceso se bloquea unos segundos.
 */
class LoginActivity : AppCompatActivity() {

    // Instancia de ViewBinding para acceder a las vistas del layout activity_login.xml
    private lateinit var binding: ActivityLoginBinding

    // Abre el registro y, al volver, deja escrito el usuario recién creado
    private val lanzadorRegistro = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val creado = resultado.data?.getStringExtra(RegistroActivity.EXTRA_USUARIO)
        if (resultado.resultCode == RESULT_OK && creado != null) {
            binding.etUsuario.setText(creado)
            binding.etClave.requestFocus()
            mostrarMensaje(getString(R.string.msg_usuario_creado, creado), esError = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflar el diseño mediante ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnValidacion.setOnClickListener { validarYAcceder() }

        // Registro en primer uso: navegación con Intent explícito a RegistroActivity
        binding.btnCrearUsuario.setOnClickListener {
            lanzadorRegistro.launch(Intent(this, RegistroActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Volver al login (con Atrás o por falta de sesión) cierra la sesión anterior
        Sesion.cerrar()

        // El botón de registro solo aparece mientras no exista ningún usuario
        val hayUsuarios = (RepositorioEstado.baseDatos()?.cantidadUsuarios() ?: 0L) > 0L
        binding.btnCrearUsuario.visibility = if (hayUsuarios) View.GONE else View.VISIBLE
        if (!hayUsuarios) mostrarMensaje(getString(R.string.msg_sin_usuarios), esError = false)
    }

    /**
     * Valida que los campos no estén vacíos y luego verifica las credenciales.
     */
    private fun validarYAcceder() {
        val usuario = binding.etUsuario.text?.toString()?.trim().orEmpty()
        // La contraseña no se recorta: los espacios pueden ser parte de ella
        val clave = binding.etClave.text?.toString().orEmpty()

        var esValido = true

        // Validar que el campo usuario no esté vacío
        if (usuario.isEmpty()) {
            binding.tilUsuario.error = getString(R.string.error_usuario_vacio)
            esValido = false
        } else {
            binding.tilUsuario.error = null
        }

        // Validar que el campo clave no esté vacío
        if (clave.isEmpty()) {
            binding.tilClave.error = getString(R.string.error_clave_vacia)
            esValido = false
        } else {
            binding.tilClave.error = null
        }
        if (!esValido) return

        // Bloqueo por intentos fallidos
        val segundos = Sesion.segundosBloqueo()
        if (segundos > 0) {
            mostrarMensaje(getString(R.string.msg_bloqueado, segundos), esError = true)
            return
        }

        // PBKDF2 tarda una fracción de segundo: se calcula fuera del hilo principal
        binding.btnValidacion.isEnabled = false
        Thread {
            // Nombre del usuario autenticado, o null si las credenciales no corresponden
            val autenticado = RepositorioEstado.baseDatos()?.buscarUsuario(usuario)
                ?.takeIf { SeguridadClaves.verificar(clave, it.sal, it.iteraciones, it.hash) }
                ?.usuario
            runOnUiThread {
                binding.btnValidacion.isEnabled = true
                if (autenticado != null) accesoConcedido(autenticado) else accesoDenegado()
            }
        }.start()
    }

    private fun accesoConcedido(usuario: String) {
        Sesion.iniciar(usuario)
        binding.tvMensajeLogin.visibility = View.GONE

        // La contraseña no queda escrita en pantalla al volver con Atrás
        binding.etClave.text?.clear()

        // Se utiliza un Intent explícito indicando la actividad origen (this) y la actividad destino (DescripcionActivity::class.java)
        val intent = Intent(this, DescripcionActivity::class.java)
        startActivity(intent)
    }

    private fun accesoDenegado() {
        Sesion.registrarFallo()
        binding.etClave.text?.clear()
        // Mensaje genérico: no revela si falló el usuario o la contraseña
        val segundos = Sesion.segundosBloqueo()
        val mensaje = if (segundos > 0) {
            getString(R.string.msg_bloqueado, segundos)
        } else {
            getString(R.string.msg_error_login_intentos, Sesion.intentosRestantes())
        }
        mostrarMensaje(mensaje, esError = true)
    }

    private fun mostrarMensaje(texto: String, esError: Boolean) {
        val color = if (esError) R.color.estadoExcursion else R.color.textoSecundario
        binding.tvMensajeLogin.setTextColor(getColor(color))
        binding.tvMensajeLogin.text = texto
        binding.tvMensajeLogin.visibility = View.VISIBLE
    }
}
