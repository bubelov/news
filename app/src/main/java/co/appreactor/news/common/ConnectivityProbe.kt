package co.appreactor.news.common

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

class ConnectivityProbe(
    private val connectivityManager: ConnectivityManager
) : ConnectivityManager.NetworkCallback() {

    var online: Boolean = false
    get() {
        if (Build.VERSION.SDK_INT < 24) {
            field = connectivityManager.activeNetworkInfo?.isConnected ?: false
        }

        return field
    }

    init {
        if (Build.VERSION.SDK_INT >= 24) {
            connectivityManager.registerDefaultNetworkCallback(this)
        }
    }

    override fun onCapabilitiesChanged(
        network: Network,
        capabilities: NetworkCapabilities
    ) {
        online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun throwIfOffline() {
        if (!online) {
            throw NetworkUnavailableException("Can not connect to Internet")
        }
    }
}