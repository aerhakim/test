package com.google.samples.apps.sunflower.utils

import android.net.wifi.p2p.WifiP2pDevice

/**
 * @Author: leavesCZY
 * @Desc:
 */
object WifiP2pUtils {

    fun getDeviceStatus(deviceStatus: Int): String {
        return when (deviceStatus) {
            WifiP2pDevice.AVAILABLE -> "dapat digunakan"
            WifiP2pDevice.INVITED -> "Mengundang"
            WifiP2pDevice.CONNECTED -> "terhubung"
            WifiP2pDevice.FAILED -> "gagal"
            WifiP2pDevice.UNAVAILABLE -> "tidak tersedia"
            else -> "kesalahan"
        }
    }

}