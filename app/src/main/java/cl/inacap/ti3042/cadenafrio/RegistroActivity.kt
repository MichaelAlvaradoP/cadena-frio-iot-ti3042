package cl.inacap.ti3042.cadenafrio

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.ti3042.cadenafrio.databinding.ActivityRegistroBinding

/**
 * Registro en primer uso: crea el usuario local cuando la base de datos aún no
 * tiene ninguno. Así la app no trae credenciales escritas en su código.
 *
 * Aplica la política de credenciales de SeguridadClaves y guarda solo el hash
 * PBKDF2 con una sal aleatoria. Al terminar devuelve el usuario al login.
 */
class RegistroActivity : AppCompatActivity() {

    // Instancia de ViewBinding para acceder a las vistas del layout activity_registro.xml
    private lateinit var binding: ActivityRegistroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // El registro solo está disponible mientras no exista ningún usuario
        if ((RepositorioEstado.baseDatos()?.cantidadUsuarios() ?: 0L) > 0L) {
            finish()
            return
        }

        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegistrar.setOnClickListener { registrar() }
    }

    private fun registrar() {
        val usuario = binding.etUsuarioNuevo.text?.toString()?.trim().orEmpty()
        val clave = binding.etClaveNueva.text?.toString().orEmpty()
        val confirmacion = binding.etConfirmarClave.text?.toString().orEmpty()

        // Validación con la política de credenciales
        val errorUsuario = SeguridadClaves.validarUsuario(usuario)
        binding.tilUsuarioNuevo.error = errorUsuario?.let { textoError(it) }

        val errorClave = SeguridadClaves.validarClave(usuario, clave, confirmacion)
        if (errorClave == SeguridadClaves.ErrorCredencial.CLAVES_NO_COINCIDEN) {
            binding.tilClaveNueva.error = null
            binding.tilConfirmarClave.error = textoError(errorClave)
        } else {
            binding.tilClaveNueva.error = errorClave?.let { textoError(it) }
            binding.tilConfirmarClave.error = null
        }
        if (errorUsuario != null || errorClave != null) return

        // El hash se calcula fuera del hilo principal para no congelar la pantalla
        binding.btnRegistrar.isEnabled = false
        Thread {
            val sal = SeguridadClaves.generarSal()
            val hash = SeguridadClaves.calcularHash(clave, sal)
            val creado = RepositorioEstado.baseDatos()
                ?.insertarUsuario(usuario, hash, sal, SeguridadClaves.ITERACIONES) ?: false
            runOnUiThread {
                binding.btnRegistrar.isEnabled = true
                if (creado) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_USUARIO, usuario))
                    finish()
                } else {
                    Toast.makeText(this, R.string.error_usuario_existe, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun textoError(error: SeguridadClaves.ErrorCredencial): String = getString(
        when (error) {
            SeguridadClaves.ErrorCredencial.USUARIO_FORMATO -> R.string.error_usuario_formato
            SeguridadClaves.ErrorCredencial.CLAVE_CORTA -> R.string.error_clave_corta
            SeguridadClaves.ErrorCredencial.CLAVE_SIN_LETRA -> R.string.error_clave_sin_letra
            SeguridadClaves.ErrorCredencial.CLAVE_SIN_NUMERO -> R.string.error_clave_sin_numero
            SeguridadClaves.ErrorCredencial.CLAVE_CONTIENE_USUARIO -> R.string.error_clave_contiene_usuario
            SeguridadClaves.ErrorCredencial.CLAVES_NO_COINCIDEN -> R.string.error_claves_no_coinciden
        }
    )

    companion object {
        /** Extra con el nombre del usuario creado, que recibe LoginActivity. */
        const val EXTRA_USUARIO = "usuario_creado"
    }
}
