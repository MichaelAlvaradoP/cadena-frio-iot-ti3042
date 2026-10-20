package cl.inacap.ti3042.cadenafrio

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local SQLite de la app (sin librerías externas).
 *
 * Tablas:
 * - usuarios: credenciales locales. Se guarda el hash PBKDF2 y la sal, nunca la contraseña.
 * - lecturas: historial de las tramas recibidas desde la fuente de datos.
 */
class BaseDatos(context: Context) : SQLiteOpenHelper(context, NOMBRE, null, VERSION) {

    /** Usuario tal como está guardado en la tabla. */
    data class UsuarioGuardado(val usuario: String, val hash: ByteArray, val sal: ByteArray, val iteraciones: Int)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE usuarios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                usuario TEXT NOT NULL UNIQUE COLLATE NOCASE,
                hash BLOB NOT NULL,
                sal BLOB NOT NULL,
                iteraciones INTEGER NOT NULL,
                creado INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE lecturas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                sq INTEGER NOT NULL,
                tv REAL NOT NULL, tr REAL NOT NULL, te REAL NOT NULL, h REAL NOT NULL,
                st INTEGER NOT NULL,
                cal INTEGER NOT NULL, ven INTEGER NOT NULL, cer INTEGER NOT NULL, tap INTEGER NOT NULL,
                mf INTEGER NOT NULL, ap INTEGER NOT NULL, dm INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, versionAnterior: Int, versionNueva: Int) {
        // Primera versión del esquema: no hay migraciones todavía
    }

    // ---------------- Usuarios ----------------

    fun cantidadUsuarios(): Long =
        readableDatabase.compileStatement("SELECT COUNT(*) FROM usuarios").use { it.simpleQueryForLong() }

    /** Inserta un usuario; devuelve false si el nombre ya existe. */
    fun insertarUsuario(usuario: String, hash: ByteArray, sal: ByteArray, iteraciones: Int): Boolean {
        val valores = ContentValues().apply {
            put("usuario", usuario)
            put("hash", hash)
            put("sal", sal)
            put("iteraciones", iteraciones)
            put("creado", System.currentTimeMillis())
        }
        return writableDatabase.insert("usuarios", null, valores) != -1L
    }

    fun buscarUsuario(usuario: String): UsuarioGuardado? =
        readableDatabase.query(
            "usuarios", arrayOf("usuario", "hash", "sal", "iteraciones"),
            "usuario = ?", arrayOf(usuario), null, null, null
        ).use { c ->
            if (c.moveToFirst()) UsuarioGuardado(c.getString(0), c.getBlob(1), c.getBlob(2), c.getInt(3)) else null
        }

    // ---------------- Lecturas ----------------

    fun insertarLectura(e: EstadoSistema) {
        val valores = ContentValues().apply {
            put("timestamp", e.timestamp)
            put("sq", e.secuencia)
            put("tv", e.tempViales)
            put("tr", e.tempRefrigerante)
            put("te", e.tempExterior)
            put("h", e.humedad)
            put("st", e.estadoGeneral.codigo)
            put("cal", if (e.calefactorEncendido) 1 else 0)
            put("ven", if (e.ventiladorEncendido) 1 else 0)
            put("cer", if (e.bloqueado) 1 else 0)
            put("tap", if (e.tapaAbierta) 1 else 0)
            put("mf", e.minutosFuera)
            put("ap", e.aperturas)
            put("dm", if (e.modoDemo) 1 else 0)
        }
        writableDatabase.insert("lecturas", null, valores)
    }

    /** Últimas lecturas guardadas, de la más nueva a la más antigua. */
    fun ultimasLecturas(limite: Int): List<EstadoSistema> =
        readableDatabase.query(
            "lecturas", null, null, null, null, null, "id DESC", limite.toString()
        ).use { c ->
            val lista = mutableListOf<EstadoSistema>()
            while (c.moveToNext()) {
                lista.add(
                    EstadoSistema(
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        secuencia = c.getLong(c.getColumnIndexOrThrow("sq")),
                        tempViales = c.getDouble(c.getColumnIndexOrThrow("tv")),
                        tempRefrigerante = c.getDouble(c.getColumnIndexOrThrow("tr")),
                        tempExterior = c.getDouble(c.getColumnIndexOrThrow("te")),
                        humedad = c.getDouble(c.getColumnIndexOrThrow("h")),
                        estadoGeneral = Estado.desdeCodigo(c.getInt(c.getColumnIndexOrThrow("st"))),
                        calefactorEncendido = c.getInt(c.getColumnIndexOrThrow("cal")) == 1,
                        ventiladorEncendido = c.getInt(c.getColumnIndexOrThrow("ven")) == 1,
                        bloqueado = c.getInt(c.getColumnIndexOrThrow("cer")) == 1,
                        tapaAbierta = c.getInt(c.getColumnIndexOrThrow("tap")) == 1,
                        minutosFuera = c.getInt(c.getColumnIndexOrThrow("mf")),
                        aperturas = c.getInt(c.getColumnIndexOrThrow("ap")),
                        modoDemo = c.getInt(c.getColumnIndexOrThrow("dm")) == 1
                    )
                )
            }
            lista
        }

    fun cantidadLecturas(): Long =
        readableDatabase.compileStatement("SELECT COUNT(*) FROM lecturas").use { it.simpleQueryForLong() }

    /** Conserva solo las [maximo] lecturas más recientes para no llenar el teléfono. */
    fun podarLecturas(maximo: Int) {
        writableDatabase.execSQL(
            "DELETE FROM lecturas WHERE id NOT IN (SELECT id FROM lecturas ORDER BY id DESC LIMIT ?)",
            arrayOf(maximo)
        )
    }

    companion object {
        private const val NOMBRE = "cadenafrio.db"
        private const val VERSION = 1
    }
}
