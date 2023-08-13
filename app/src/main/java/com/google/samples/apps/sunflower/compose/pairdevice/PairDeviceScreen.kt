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

package com.google.samples.apps.sunflower.compose.plantlist

import PairDeviceViewModel
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.samples.apps.sunflower.GardenActivity
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel
import com.google.samples.apps.sunflower.R
import com.google.samples.apps.sunflower.data.Plant
import java.time.format.TextStyle

@Composable
fun PairDeviceScreen(){
    val parentValue = (LocalContext.current as GardenActivity).parentValue
//    Text(text = "mm")

    FileReceiver()
//    FileReceiverScreen()
}

@Composable
fun FileReceiverScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "1. Baik ujung pengiriman file maupun ujung penerima harus mengaktifkan Wifi\\n2. Ujung penerima file terlebih dahulu membuat grup, dan ujung pengirim file mencari perangkat dan mengeklik untuk menghubungkan\\n3. Setelah koneksi berhasil, ujung penerima file mulai memantau terlebih dahulu, lalu mengirim file Kemudian pilih file yang akan dikirim\n",
            color = Color.Black,
            fontSize = 18.sp,
            modifier = Modifier.padding(5.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { /* Handle Direct Discover button click */ },
                modifier = Modifier.weight(1f).padding(5.dp)
            ) {
                Text("perangkat pencarian")
            }

            Button(
                onClick = { /* Handle Disconnect button click */ },
                modifier = Modifier.weight(1f).padding(5.dp)
            ) {
                Text("Memutuskan")
            }
        }

        Button(
            onClick = { /* Handle Choose File button click */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            enabled = false
        ) {
            Text("Pilih File")
        }

        Text(
            text = "Antarmuka informasi perangkat ini",
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(10.dp)
        )

        Text(
            text = "Mi 5X",
            color = Color.Black,
            fontSize = 18.sp,
            modifier = Modifier.padding(10.dp)
        )

        Text(
            text = "Status hubungan",
            color = Color.Black,
            fontSize = 18.sp,
            modifier = Modifier.padding(10.dp)
        )

        Text(
            text = "Daftar perangkat",
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(10.dp)
        )

        // RecyclerView for device list
        AndroidView(
            factory = { context ->
                RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    // Set up your adapter and data here
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 0.dp, max = 600.dp)
        )

        // Add your log output TextView here
    }
}

@Composable
fun FileReceiver() {
    val parentValue = GardenActivity().parentValue
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = parentValue,
            fontSize = 18.sp,
            modifier = Modifier.padding(5.dp)
        )
        Text(
            text = "Tips",
            fontSize = 18.sp,
            modifier = Modifier.padding(5.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { GardenActivity().createGroup() },
                modifier = Modifier.weight(1f).padding(5.dp)
            ) {
                Text("membuat grup")
            }

            Button(
                onClick = { GardenActivity().removeGroup() },
                modifier = Modifier.weight(1f).padding(5.dp)
            ) {
                Text("hapus grup")
            }
        }

        Button(
            onClick = { /* Handle Start Receive button click */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp)
        ) {
            Text("mulai pemantauan")
        }

//        Image(
//            painter = painterResource(id = R.drawable.your_image_resource), // Replace with your image resource
//            contentDescription = null,
//            modifier = Modifier
//                .size(200.dp)
//                .scale(1.0f, ContentScale.Crop)
//                .padding(16.dp)
//                .align(Alignment.CenterHorizontally)
//        )

        Text(
            text = "Log output",
            fontSize = 18.sp,
            modifier = Modifier.padding(10.dp)
        )

        // Add your log output TextView here
    }
}

@Preview
@Composable
fun PairDeviceScreenPreview() {
    FileReceiver()
}
