package cl.inacap.ti3042.cadenafrio

import android.app.Activity
import android.content.Intent
import android.os.SystemClock

/**
 * Control de acceso local: sesión iniciada y bloqueo por intentos fallidos.
 *
 * - Las pantallas 3, 4 y 5 llaman a [exigir]; si no hay sesión vuelven al login,
 *   así no se puede llegar a ellas saltándose la autenticación (por ejemplo,
 *   cuando Android recrea la app después de cerrarla en segundo plano).
 * - Tras [MAX_INTENTOS] fallos seguidos, el login se bloquea [SEGUNDOS_BLOQUEO] s.
 *
 * Vive en memoria: al cerrar la app la sesión termina.
 */
object Sesion {

    const val MAX_INTENTOS = 5
    const val SEGUNDOS_BLOQUEO = 30L

    private var usuarioActivo: String? = null
    private var intentosFallidos = 0
    private var bloqueadoHastaMs = 0L

    fun iniciar(usuario: String) {
        usuarioActivo = usuario
        intentosFallidos = 0
    }

    fun cerrar() {
        usuarioActivo = null
    }

    fun activa(): Boolean = usuarioActivo != null

    fun usuario(): String = usuarioActivo.orEmpty()

    /** Registra un intento fallido; al llegar al máximo activa el bloqueo. */
    fun registrarFallo() {
        intentosFallidos++
        if (intentosFallidos >= MAX_INTENTOS) {
            bloqueadoHastaMs = SystemClock.elapsedRealtime() + SEGUNDOS_BLOQUEO * 1000
            intentosFallidos = 0
        }
    }

    /** Segundos de bloqueo que quedan (0 si se puede intentar). */
    fun segundosBloqueo(): Long {
        val restante = bloqueadoHastaMs - SystemClock.elapsedRealtime()
        return if (restante > 0) (restante + 999) / 1000 else 0
    }

    fun intentosRestantes(): Int = MAX_INTENTOS - intentosFallidos

    /**
     * Verifica que haya sesión. Si no la hay, abre el login, cierra la pantalla
     * actual y devuelve false para que la Activity no siga construyéndose.
     */
    fun exigir(activity: Activity): Boolean {
        if (activa()) return true
        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        activity.startActivity(intent)
        activity.finish()
        return false
    }
}
