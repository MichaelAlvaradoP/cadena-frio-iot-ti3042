package cl.inacap.ti3042.cadenafrio

/**
 * Estado de la conexión inalámbrica, para mostrarlo en pantalla.
 * Lo informan BluetoothDatos (monitor) y ServidorContenedor (teléfono contenedor).
 */
enum class EstadoEnlace {
    SIMULADOR,          // datos del simulador interno, sin Bluetooth
    CONECTANDO,         // monitor intentando abrir el socket SPP
    CONECTADO,          // monitor recibiendo tramas
    DESCONECTADO,       // monitor sin conexión (se vuelve a conectar desde Control)
    SIN_PERMISO,        // falta el permiso BLUETOOTH_CONNECT
    BLUETOOTH_APAGADO,  // el adaptador está apagado o no existe
    ESPERANDO_MONITOR,  // contenedor esperando que un monitor se conecte
    MONITOR_CONECTADO   // contenedor enviando tramas a un monitor
}
