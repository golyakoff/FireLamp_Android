package net.agolyakov.firelamp.data.model.ble

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    /** The system pairing dialog is (or is about to be) shown. */
    object Bonding : ConnectionState()
    /** Connected and bonded, the first values are being read. */
    object Loading : ConnectionState()
    object Ready : ConnectionState()
    object Disconnecting : ConnectionState()

    /**
     * The lamp did not accept this phone: it paired outside the pairing window (the lamp drops
     * such keys and disconnects), or the lamp forgot the bond (factory reset, a 5th phone).
     * The user has to click the lamp button 5 times and connect again.
     * [staleBondRemoved]: the app already removed the phone's side of the bond.
     */
    data class PairingRejected(val staleBondRemoved: Boolean) : ConnectionState()

    /** The lamp speaks a protocol version this app does not know: update the app. */
    data class UnsupportedProtocol(val version: Int) : ConnectionState()

    /** The Bluetooth permissions were not granted or were revoked in the system settings. */
    object NoPermission : ConnectionState()

    data class Error(val message: String) : ConnectionState()

    /** Somewhere between "connect" and "ready". */
    val isInProgress: Boolean get() = this is Connecting || this is Bonding || this is Loading
}
