package common

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class NetworkMonitor(
    connectivityManager: ConnectivityManager,
) : ConnectivityManager.NetworkCallback() {

    var online: Boolean = false

    init {
        connectivityManager.registerDefaultNetworkCallback(this)
    }

    override fun onCapabilitiesChanged(
        network: Network,
        capabilities: NetworkCapabilities
    ) {
        online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}