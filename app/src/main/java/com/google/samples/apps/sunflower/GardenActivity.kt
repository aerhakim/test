/*
 * Copyright 2018 Google LLC
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

package com.google.samples.apps.sunflower

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.themeadapter.material.MdcTheme
import com.google.samples.apps.sunflower.Logger.log
import com.google.samples.apps.sunflower.compose.SunflowerApp
import com.google.samples.apps.sunflower.compose.home.SunflowerPage
import com.google.samples.apps.sunflower.viewmodels.FileReceiverViewModel
import com.google.samples.apps.sunflower.viewmodels.FileSenderViewModel
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// TODO: update the sperclass to ComponentActivity https://github.com/android/sunflower/issues/829
@AndroidEntryPoint
class GardenActivity : AppCompatActivity() {
    val parentValue = "Hello from Activity!"
    private val viewModel: PlantListViewModel by viewModels()
    private val requestedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    private val requestPermissionLaunch = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { it ->
        if (it.all { it.value }) {
            Toast.makeText(this,"Akses penuh telah diberikan", Toast.LENGTH_LONG)
        } else {
            onPermissionDenied()
        }
    }
    private fun onPermissionDenied() {
        Toast.makeText(this,"Izin tidak ada, harap berikan izin terlebih dahulu", Toast.LENGTH_LONG)
    }
    private fun allPermissionGranted(): Boolean {
        requestedPermissions.forEach {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }
    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menu.clear()
            menuInflater.inflate(R.menu.menu_plant_list, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.filter_zone -> {
                    viewModel.updateData()
                    true
                }
                else -> false
            }
        }
    }

    //Sender
    private val fileSenderViewModel by viewModels<FileSenderViewModel>()

    private val getContentLaunch = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null) {
            val ipAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress
            log("getContentLaunch $imageUri $ipAddress")
            if (!ipAddress.isNullOrBlank()) {
                fileSenderViewModel.send(ipAddress = ipAddress, fileUri = imageUri)
            }
        }
    }

    private val wifiP2pDeviceList = mutableListOf<WifiP2pDevice>()


    private var broadcastReceiver: BroadcastReceiver? = null

    private lateinit var wifiP2pManager: WifiP2pManager

    private lateinit var wifiP2pChannel: WifiP2pManager.Channel

    private var wifiP2pInfo: WifiP2pInfo? = null

    private var wifiP2pEnabled = false
    private val fileReceiverViewModel by viewModels<FileReceiverViewModel>()


    private var connectionInfoAvailable = false

    private val directActionListener = object : DirectActionListener {

        override fun wifiP2pEnabled(enabled: Boolean) {
            wifiP2pEnabled = enabled
        }

        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            wifiP2pDeviceList.clear()
            log("onConnectionInfoAvailable")
            log("onConnectionInfoAvailable groupFormed: " + wifiP2pInfo.groupFormed)
            log("onConnectionInfoAvailable isGroupOwner: " + wifiP2pInfo.isGroupOwner)
            log("onConnectionInfoAvailable getHostAddress: " + wifiP2pInfo.groupOwnerAddress.hostAddress)
            if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                this@GardenActivity.wifiP2pInfo = wifiP2pInfo
            }
        }

        override fun onDisconnection() {
            log("onDisconnection")

            wifiP2pDeviceList.clear()
            wifiP2pInfo = null
//            showToast("处于非连接状态")
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            log("onSelfDeviceAvailable")
            log("DeviceName: " + wifiP2pDevice.deviceName)
            log("DeviceAddress: " + wifiP2pDevice.deviceAddress)
            log("Status: " + wifiP2pDevice.status)
//            val log = "deviceName：" + wifiP2pDevice.deviceName + "\n" +
//                    "deviceAddress：" + wifiP2pDevice.deviceAddress + "\n" +
//                    "deviceStatus：" + WifiP2pUtils.getDeviceStatus(wifiP2pDevice.status)
//            tvDeviceState.text = log
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            log("onPeersAvailable :" + wifiP2pDeviceList.size)
            this@GardenActivity.wifiP2pDeviceList.clear()
            this@GardenActivity.wifiP2pDeviceList.addAll(wifiP2pDeviceList)
        }

        override fun onChannelDisconnected() {
            log("onChannelDisconnected")
        }
    }
    private fun initDevice() {
        val mWifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mWifiP2pManager == null) {
            finish()
            return
        }
        wifiP2pManager = mWifiP2pManager
        wifiP2pChannel = mWifiP2pManager.initialize(this, mainLooper, directActionListener)
        broadcastReceiver =
            DirectBroadcastReceiver(mWifiP2pManager, wifiP2pChannel, directActionListener)
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.getIntentFilter())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Displaying edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestPermissionLaunch.launch(requestedPermissions)
        initDevice()
        setContentView(ComposeView(this).apply {
            // Provide a different ComposeView instead of using ComponentActivity's setContent
            // method so that the `consumeWindowInsets` property can be set to `false`. This is
            // needed so that insets can be properly applied to `HomeScreen` which uses View interop
            // This can likely be changed when `HomeScreen` is fully in Compose.
            consumeWindowInsets = false
            setContent {
                MdcTheme {
                    SunflowerApp(
                        onAttached = { toolbar ->
                            setSupportActionBar(toolbar)
                        },
                        onPageChange = { page ->
                            when (page) {
                                SunflowerPage.MY_GARDEN -> removeMenuProvider(menuProvider)
                                SunflowerPage.PLANT_LIST -> addMenuProvider(
                                    menuProvider,
                                    this@GardenActivity
                                )
                                SunflowerPage.PAIR_DEVICE -> removeMenuProvider(menuProvider)
                            }
                        },
                        plantListViewModel = viewModel,
                    )
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)
        }
        removeGroup()

    }
    @SuppressLint("MissingPermission")
    fun createGroup() {
        lifecycleScope.launch {
            removeGroupIfNeed()
            wifiP2pManager.createGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    val log = "createGroup onSuccess"
                    log(log = log)
//                    showToast(message = log)
                }

                override fun onFailure(reason: Int) {
                    val log = "createGroup onFailure: $reason"
                    log(log = log)
//                    showToast(message = log)
                }
            })
        }
    }
    @SuppressLint("MissingPermission")
    private suspend fun removeGroupIfNeed() {
        return suspendCancellableCoroutine { continuation ->
            wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
                if (group == null) {
                    continuation.resume(value = Unit)
                } else {
                    wifiP2pManager.removeGroup(wifiP2pChannel,
                        object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                val log = "removeGroup onSuccess"
                                log(log = log)
//                                showToast(message = log)
                                continuation.resume(value = Unit)
                            }

                            override fun onFailure(reason: Int) {
                                val log = "removeGroup onFailure: $reason"
                                log(log = log)
//                                showToast(message = log)
                                continuation.resume(value = Unit)
                            }
                        })
                }
            }
        }
    }

    fun removeGroup() {
        lifecycleScope.launch {
            removeGroupIfNeed()
        }
    }
    @SuppressLint("MissingPermission")
    private fun connect(wifiP2pDevice: WifiP2pDevice) {
        val wifiP2pConfig = WifiP2pConfig()
        wifiP2pConfig.deviceAddress = wifiP2pDevice.deviceAddress
        wifiP2pConfig.wps.setup = WpsInfo.PBC
//        showLoadingDialog(message = "正在连接，deviceName: " + wifiP2pDevice.deviceName)
//        showToast("正在连接，deviceName: " + wifiP2pDevice.deviceName)
        wifiP2pManager.connect(wifiP2pChannel, wifiP2pConfig,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    log("connect onSuccess")
                }

                override fun onFailure(reason: Int) {
//                    showToast("连接失败 $reason")
//                    dismissLoadingDialog()
                }
            })
    }

    private fun disconnect() {
        wifiP2pManager.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                log("cancelConnect onFailure:$reasonCode")
            }

            override fun onSuccess() {
                log("cancelConnect onSuccess")
//                tvConnectionStatus.text = null
//                btnDisconnect.isEnabled = false
//                btnChooseFile.isEnabled = false
            }
        })
        wifiP2pManager.removeGroup(wifiP2pChannel, null)
    }

    private fun log(log: String) {
//        tvLog.append(log)
//        tvLog.append("\n\n")
    }

    private fun clearLog() {
//        tvLog.text = ""
    }
}