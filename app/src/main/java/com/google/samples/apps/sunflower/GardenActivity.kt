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
import android.content.Context
import android.content.SharedPreferences
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
import androidx.appcompat.app.AppCompatDelegate
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
import com.google.samples.apps.sunflower.utils.WifiP2pUtils
import com.google.samples.apps.sunflower.viewmodels.FileReceiverViewModel
import com.google.samples.apps.sunflower.viewmodels.FileSenderViewModel
import com.google.samples.apps.sunflower.viewmodels.PairDeviceViewModel
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// TODO: update the sperclass to ComponentActivity https://github.com/android/sunflower/issues/829
@AndroidEntryPoint
class GardenActivity : AppCompatActivity() {
    private val viewModel: PlantListViewModel by viewModels()
    private val pairDeviceViewModel: PairDeviceViewModel by viewModels()
    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkMode: Boolean = false

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
            Toast.makeText(this, "Akses penuh telah diberikan", Toast.LENGTH_LONG)
        } else {
            onPermissionDenied()
        }
    }

    private fun onPermissionDenied() {
        Toast.makeText(
            this,
            "Izin tidak ada, harap berikan izin terlebih dahulu",
            Toast.LENGTH_LONG
        ).show()
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

                R.id.switch_darkmode -> {
                    if (isDarkMode) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        isDarkMode = !isDarkMode
                        sharedPreferences.edit().putBoolean("isDarkMode", isDarkMode).apply()
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        isDarkMode = !isDarkMode
                        sharedPreferences.edit().putBoolean("isDarkMode", isDarkMode).apply()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private val darkmodeProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menu.clear()
            menuInflater.inflate(R.menu.switch_darkmode, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.switch_darkmode -> {
                    if (!isDarkMode) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        isDarkMode = !isDarkMode
                        sharedPreferences.edit().putBoolean("isDarkMode", isDarkMode).apply()

                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        isDarkMode = !isDarkMode
                        sharedPreferences.edit().putBoolean("isDarkMode", isDarkMode).apply()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private var broadcastReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairDeviceViewModel.initDevice(this)
        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)

        // Displaying edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestPermissionLaunch.launch(requestedPermissions)
        setContentView(ComposeView(this).apply {
            consumeWindowInsets = false
            setContent {
                MdcTheme {
                    SunflowerApp(
                        onAttached = { toolbar ->
                            setSupportActionBar(toolbar)
                        },
                        onPageChange = { page ->
                            when (page) {
                                SunflowerPage.MY_GARDEN -> addMenuProvider(
                                    darkmodeProvider,
                                    this@GardenActivity
                                )

                                SunflowerPage.PLANT_LIST -> addMenuProvider(
                                    menuProvider,
                                    this@GardenActivity
                                )

                                SunflowerPage.PAIR_DEVICE -> addMenuProvider(
                                    darkmodeProvider,
                                    this@GardenActivity
                                )
                            }
                        },
                        plantListViewModel = viewModel,
                        pairDeviceViewModel = pairDeviceViewModel,

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
        pairDeviceViewModel.removeGroup(this)

    }

}