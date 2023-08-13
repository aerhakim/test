import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.AsyncTask
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

enum class PairRole {
    Sender,
    Receiver
}
class PairDeviceViewModel(private val context: Context) : ViewModel() {
    private val _selectedRole = MutableStateFlow<PairRole?>(null)
    val selectedRole: StateFlow<PairRole?> = _selectedRole
    private val _groupCreationStatus =
        MutableStateFlow<GroupCreationStatus>(GroupCreationStatus.Idle)
    val groupCreationStatus: StateFlow<GroupCreationStatus> = _groupCreationStatus
    private val _receiverState = MutableStateFlow<ReceiverState>(ReceiverState.Idle)
    val receiverState: StateFlow<ReceiverState> = _receiverState
    private val _groupList = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val groupList: StateFlow<List<WifiP2pDevice>> = _groupList
    private val wifiP2pManager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val wifiP2pChannel: WifiP2pManager.Channel =
        wifiP2pManager.initialize(context, context.mainLooper, null)
    private var job: Job? = null

    fun createWifiP2pGroup() {
        _groupCreationStatus.value = GroupCreationStatus.InProgress
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            wifiP2pManager.createGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _groupCreationStatus.value = GroupCreationStatus.Success("groupName")

                }

                override fun onFailure(reason: Int) {
                    _groupCreationStatus.value = GroupCreationStatus.Error("groupName")
                }
            })
        }

    }

    fun setSelectedRole(role: PairRole) {
        _selectedRole.value = role
    }

    fun discoverAvailableGroups() {
        val wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val wifiP2pChannel: WifiP2pManager.Channel = wifiP2pManager.initialize(
            context,
            Looper.getMainLooper(),
            null
        )

        val actionListener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _receiverState.value = ReceiverState.Discovering
                startListener();
            }

            override fun onFailure(reason: Int) {
                _receiverState.value = ReceiverState.Error("Failed to discover groups")
            }
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            wifiP2pManager.discoverPeers(wifiP2pChannel, actionListener)
        }
        val peerListListener = WifiP2pManager.PeerListListener { peerList ->
            val discoveredGroups: List<WifiP2pDevice> = peerList.deviceList.toList()
            _groupList.value = discoveredGroups
        }
        wifiP2pManager.requestPeers(wifiP2pChannel, peerListListener)
    }

    fun connectToGroupOwner(groupInfo: WifiP2pDevice) {
        val wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val wifiP2pChannel: WifiP2pManager.Channel = wifiP2pManager.initialize(
            context,
            Looper.getMainLooper(),
            null
        )
        val config = WifiP2pConfig().apply {
            deviceAddress = groupInfo.deviceAddress
            // Set other configuration options if needed
        }

        val connectionListener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _receiverState.value = ReceiverState.Connecting(groupInfo.deviceName)
                Toast.makeText(
                    context,
                    "Berhasil Terkoneksi dengan " + groupInfo.deviceName,
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onFailure(reason: Int) {
                _receiverState.value = ReceiverState.Error("Failed to connect to group owner")
            }
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            wifiP2pManager.connect(wifiP2pChannel, config, connectionListener)
        }
    }
    fun send(data: String) {
        if (job != null) {
            return
        }
        job = viewModelScope.launch {
            withContext(context = Dispatchers.IO) {
//                val groupOwnerAddress: String = info.groupOwnerAddress.hostAddress

                var socket: Socket? = null
                var outputStream: OutputStream? = null
                var objectOutputStream: ObjectOutputStream? = null
                var fileInputStream: FileInputStream? = null
                try {
                    socket = Socket()
                    socket.bind(null)
//                    socket.connect(InetSocketAddress(ipAddress, 1995), 30000)
                    outputStream = socket.getOutputStream()
                    objectOutputStream = ObjectOutputStream(outputStream)
                    objectOutputStream.writeObject(data)
                    val buffer = ByteArray(1024 * 100)
                    var length: Int
                    while (true) {
                        length = fileInputStream!!.read(buffer)
                        if (length > 0) {
                            outputStream.write(buffer, 0, length)
                        } else {
                            break
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    fileInputStream?.close()
                    outputStream?.close()
                    objectOutputStream?.close()
                    socket?.close()
                }
            }
        }
        job?.invokeOnCompletion {
            job = null
        }
        }


    fun startListener() {
        Thread {
            try {
                val serverSocket = ServerSocket()
                serverSocket.bind(InetSocketAddress(1995))
                serverSocket.reuseAddress = true
                serverSocket.soTimeout = 30000

                val client = serverSocket.accept()
                val clientInputStream = client.getInputStream()
                val objectInputStream = ObjectInputStream(clientInputStream)
                val receivedData = objectInputStream.readObject() as String
                Log.e("ReceiverSuccess", receivedData)
                _receiverState.value = ReceiverState.DataReceived(receivedData)
                serverSocket.close()
                clientInputStream.close()
                objectInputStream.close()
            } catch (e: Exception) {
                Log.e("ReceiverError", e.toString())
            }
        }.start()
    }

}

    sealed class GroupCreationStatus {
        object Idle : GroupCreationStatus()
        object InProgress : GroupCreationStatus()
        data class Success(val groupName: String) : GroupCreationStatus()
        data class Error(val errorMessage: String) : GroupCreationStatus()
    }

    sealed class ReceiverState {
        object Idle : ReceiverState()
        object Discovering : ReceiverState()
        data class Connecting(val deviceName: String) : ReceiverState()
        data class DataReceived(val data: String) : ReceiverState()
        data class Error(val errorMessage: String) : ReceiverState()
    }

