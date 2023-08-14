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

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.samples.apps.sunflower.GardenActivity
import com.google.samples.apps.sunflower.R
import com.google.samples.apps.sunflower.viewmodels.PairDeviceViewModel

@Composable
fun PairDeviceScreen(
    pairDeviceViewModel: PairDeviceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val selectedRole = pairDeviceViewModel.selectedRole.observeAsState(initial = "null").value
    if (selectedRole === "Receiver") {
        Receiver(pairDeviceViewModel, context)
    } else if (selectedRole === "Sender") {
        Sender(pairDeviceViewModel, context)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Text("Pilih Role Anda")
            Text("Pastikan untuk sudah ada device receiver terlebih dahulu sebelum membuat device sender")
            Button(
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.onPrimary),
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = dimensionResource(id = R.dimen.button_corner_radius),
                    bottomStart = dimensionResource(id = R.dimen.button_corner_radius),
                    bottomEnd = 0.dp,
                ),
                onClick = { pairDeviceViewModel.changeSelectedRole("Sender") },
            ) {
                Text(
                    color = MaterialTheme.colors.primary,
                    text = "Sender"
                )
            }
            Button(
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.onPrimary),
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = dimensionResource(id = R.dimen.button_corner_radius),
                    bottomStart = dimensionResource(id = R.dimen.button_corner_radius),
                    bottomEnd = 0.dp,
                ),
                onClick = { pairDeviceViewModel.changeSelectedRole("Receiver") },
            ) {
                Text(
                    color = MaterialTheme.colors.primary,
                    text = "Receiver"
                )
            }
        }
    }
}

@Composable
fun Sender(
    pairDeviceViewModel: PairDeviceViewModel = hiltViewModel(),
    context: Context
) {
    val deviceStatus = pairDeviceViewModel.deviceStatus.observeAsState(initial = "null").value
    val wifiP2pDeviceList = pairDeviceViewModel.wifiP2pDeviceList.observeAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Anda Berperan Sebagai Sender",
            color = Color.Black,
            modifier = Modifier.padding(5.dp)
        )
        Text(
            text = "Pastikan Sudah Ada Device Yang Menjadi Receiver Sebelum Melakukan Pencarian Perangkat",
            color = Color.Black,
            modifier = Modifier.padding(5.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { pairDeviceViewModel.discoverWifi(context) },
                modifier = Modifier
                    .weight(1f)
                    .padding(5.dp)
            ) {
                Text("Cari Perangkat")
            }

            Button(
                onClick = { pairDeviceViewModel.disconnect(context) },
                modifier = Modifier
                    .weight(1f)
                    .padding(5.dp)
            ) {
                Text("Disconnect")
            }
        }
        Text(
            text = "Status hubungan",
            color = Color.Black,
            modifier = Modifier.padding(10.dp)
        )

        Text(
            text = if (deviceStatus.equals("null") === false) deviceStatus else "" ,
            color = Color.Black,
            modifier = Modifier.padding(10.dp)
        )

        Text(
            text = "Daftar perangkat",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(10.dp)
        )
        LazyColumn {
            items(wifiP2pDeviceList.value) { group ->
                Button(
                    onClick = { pairDeviceViewModel.connect(group,context) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(5.dp)
                ) {
                    Text("${group.deviceName} \n${group.deviceAddress}")
                }
            }
        }

        // Add your log output TextView here
    }
}

@Composable
fun Receiver(
    pairDeviceViewModel: PairDeviceViewModel = hiltViewModel(),
    context: Context
) {
    val deviceStatus = pairDeviceViewModel.deviceStatus.observeAsState(initial = "null").value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Anda Berperan Sebagai Receiver",
            modifier = Modifier.padding(5.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { pairDeviceViewModel.createGroup(context) },
                modifier = Modifier
                    .weight(1f)
                    .padding(5.dp)
            ) {
                Text("Buat Connection Group")
            }

            Button(
                onClick = { pairDeviceViewModel.removeGroup(context) },
                modifier = Modifier
                    .weight(1f)
                    .padding(5.dp)
            ) {
                Text("Hapus Connection Group")
            }
        }
        Text(
            text = "Status hubungan",
            color = Color.Black,
            modifier = Modifier.padding(10.dp)
        )

        Text(
            text = if (deviceStatus.equals("null") === false) deviceStatus else "" ,
            color = Color.Black,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Preview
@Composable
fun PairDeviceScreenPreview() {
//    Receiver()
}
