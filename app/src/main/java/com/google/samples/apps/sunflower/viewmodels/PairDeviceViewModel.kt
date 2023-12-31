/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.sunflower.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.ContextCompat.registerReceiver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.sunflower.Constants
import com.google.samples.apps.sunflower.DirectActionListener
import com.google.samples.apps.sunflower.DirectBroadcastReceiver
import com.google.samples.apps.sunflower.Logger
import com.google.samples.apps.sunflower.data.SharedRepository
import com.google.samples.apps.sunflower.utils.WifiP2pUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import kotlin.coroutines.resume

@HiltViewModel
class PairDeviceViewModel @Inject constructor(
    context: Application,
    private val sharedRepository: SharedRepository,
) : ViewModel() {

    private var job: Job? = null
    private var jobReceiver: Job? = null
    private var jobSender: Job? = null

    //WifiP2P Start
    private val _wifiP2pDeviceList = MutableLiveData<List<WifiP2pDevice>>()
    val wifiP2pDeviceList: LiveData<List<WifiP2pDevice>> = _wifiP2pDeviceList
    private var groupOwner: String = ""
    private var wifiP2pEnabled = false
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var wifiP2pInfo: WifiP2pInfo? = null

    // Initialize Wi-Fi P2P Manager and Channel in your ViewModel's constructor or init block
    init {
        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiP2pChannel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
    }
    //WifiP2P End

    private val _selectedRole = MutableSharedFlow<String>()
    val selectedRole = _selectedRole.asLiveData()
    private var _receiverData: String? = null

    private val _deviceStatus = MutableSharedFlow<String>()
    val deviceStatus = _deviceStatus.asLiveData()
    private var broadcastReceiver: BroadcastReceiver? = null

    private val directActionListener = object : DirectActionListener {

        override fun wifiP2pEnabled(enabled: Boolean) {
            wifiP2pEnabled = enabled
        }

        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            Log.e("wifiP2pInfo", wifiP2pInfo.groupOwnerAddress.hostAddress!!)
            groupOwner = wifiP2pInfo.groupOwnerAddress.hostAddress!!
        }

        override fun onDisconnection() {
            _wifiP2pDeviceList.value = emptyList()

            wifiP2pInfo = null
            Toast.makeText(context, "Berhasil Disconnect", Toast.LENGTH_SHORT).show()
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            val log = "deviceName：" + wifiP2pDevice.deviceName + "\n" +
                    "deviceStatus：" + WifiP2pUtils.getDeviceStatus(wifiP2pDevice.status)
            viewModelScope.launch {
                _deviceStatus.emit(log)
            }
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            Logger.log("onPeersAvailable :" + wifiP2pDeviceList.size)
            _wifiP2pDeviceList.value = emptyList()

            val currentList = _wifiP2pDeviceList.value.orEmpty()
            _wifiP2pDeviceList.value = currentList + wifiP2pDeviceList
        }

        override fun onChannelDisconnected() {
            Logger.log("onChannelDisconnected")
        }
    }

    fun send(plantId: String, context: Context) {
        if (jobSender != null) {
            return
        }
        jobSender = viewModelScope.launch {
            withContext(context = Dispatchers.IO) {
                var socket: Socket? = null
                var outputStream: OutputStream? = null
                var objectOutputStream: ObjectOutputStream? = null
                try {
                    socket = Socket()
                    socket.bind(null)
                    socket.connect(InetSocketAddress(groupOwner, Constants.PORT), 30000)
                    outputStream = socket.getOutputStream()
                    val objectOutputStream = BufferedWriter(OutputStreamWriter(outputStream))
                    objectOutputStream.write(plantId)
                    objectOutputStream.newLine()
                    objectOutputStream.flush()
                    Log.e(
                        "SendResultSuccess",
                        "$groupOwner $plantId"
                    )
                } catch (e: Throwable) {
                    e.printStackTrace()
                    Log.e("Send Error", e.toString())
                } finally {
                    outputStream?.close()
                    objectOutputStream?.close()
                    socket?.close()
                }
            }
        }
        jobSender?.invokeOnCompletion {
            jobSender = null
        }
    }

    fun startListener(context: Context) {
        if (jobReceiver != null) {
            return
        }
        jobReceiver = viewModelScope.launch(context = Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            var clientInputStream: InputStream? = null
            var reader: BufferedReader? = null
            try {
                serverSocket = ServerSocket()
                serverSocket.bind(InetSocketAddress(Constants.PORT))
                serverSocket.reuseAddress = true
                serverSocket.soTimeout = 30000
                val client = serverSocket.accept()
                clientInputStream = client.getInputStream()
                reader = BufferedReader(InputStreamReader(clientInputStream))
                _receiverData = reader.readLine()
//                Log.e("ReceiveResult ", reader.readLine())
            } catch (e: Throwable) {
                Log.e("startListener", e.toString())
            } finally {
                serverSocket?.close()
                clientInputStream?.close()
                reader?.close()
            }
        }
        jobReceiver?.invokeOnCompletion {
            jobReceiver = null
            sharedRepository.updateSharedData(_receiverData!!)
        }
    }

    fun initDevice(context: Context) {
        if (wifiP2pManager == null) {
            return
        }
        broadcastReceiver =
            wifiP2pChannel?.let {
                DirectBroadcastReceiver(
                    wifiP2pManager!!,
                    it, directActionListener
                )
            }
        registerReceiver(
            context,
            broadcastReceiver,
            DirectBroadcastReceiver.getIntentFilter(),
            RECEIVER_EXPORTED
        )
    }

    //Handle Select Role
    fun changeSelectedRole(selectedRoleData: String) {
        if (job != null) {
            return
        }
        job = viewModelScope.launch {
            _selectedRole.emit(value = selectedRoleData)
        }
        job?.invokeOnCompletion {
            job = null
        }
    }

    //Handle Receiver Create, Remove Group
    @SuppressLint("MissingPermission")
    fun createGroup(context: Context) {
        viewModelScope.launch {
            removeGroupIfNeed(context)
            wifiP2pManager!!.createGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    val log = "Sukses Buat Group"
                    Toast.makeText(context, log, Toast.LENGTH_SHORT).show()
                    startListener(context)
                }

                override fun onFailure(reason: Int) {
                    val log = "Gagal Buat Group: $reason"
                    Toast.makeText(context, log, Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun removeGroupIfNeed(context: Context) {
        return suspendCancellableCoroutine { continuation ->
            wifiP2pManager!!.requestGroupInfo(wifiP2pChannel) { group ->
                if (group == null) {
                    continuation.resume(value = Unit)
                } else {
                    wifiP2pManager!!.removeGroup(
                        wifiP2pChannel,
                        object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                val log = "Berhasil Hapus Group"
                                Toast.makeText(context, log, Toast.LENGTH_SHORT).show()
                                continuation.resume(value = Unit)
                            }

                            override fun onFailure(reason: Int) {
                                val log = "Gagal Hapus Group: $reason"
                                Toast.makeText(context, log, Toast.LENGTH_SHORT).show()
                                continuation.resume(value = Unit)
                            }
                        })
                }
            }
        }
    }

    fun removeGroup(context: Context) {
        viewModelScope.launch {
            removeGroupIfNeed(context)
        }
    }

    //Handle Sender Connect, Disconnect, DiscoverWifi
    @SuppressLint("MissingPermission")
    fun connect(wifiP2pDevice: WifiP2pDevice, context: Context) {
        val wifiP2pConfig = WifiP2pConfig()
        wifiP2pConfig.deviceAddress = wifiP2pDevice.deviceAddress
        wifiP2pConfig.wps.setup = WpsInfo.PBC
        Toast.makeText(
            context,
            "Menghubungkan Dengan Device " + wifiP2pDevice.deviceName,
            Toast.LENGTH_SHORT
        ).show()
        wifiP2pManager!!.connect(
            wifiP2pChannel,
            wifiP2pConfig,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(context, "Berhasil Terhubung", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(context, "Gagal Terkoneksi : $reason", Toast.LENGTH_SHORT).show()
                }
            })
    }

    fun disconnect(context: Context) {
        wifiP2pManager!!.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                Toast.makeText(
                    context,
                    "Gagal Melakukan Disconnect : $reasonCode",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onSuccess() {
                Toast.makeText(context, "Berhasil Melakukan Disconnect", Toast.LENGTH_SHORT).show()
            }
        })
        wifiP2pManager!!.removeGroup(wifiP2pChannel, null)
    }

    @SuppressLint("MissingPermission")
    fun discoverWifi(context: Context) {
        if (!wifiP2pEnabled) {
            Toast.makeText(context, "Hidupkan Wifi", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, "Mencari Receiver Terdekat", Toast.LENGTH_SHORT).show()
        _wifiP2pDeviceList.value = emptyList()

//        deviceAdapter.notifyDataSetChanged()
        wifiP2pManager!!.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(context, "Pencairan Receiver berhasil", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reasonCode: Int) {
                Toast.makeText(
                    context,
                    "Pencairan Receiver berhasil $reasonCode",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

}