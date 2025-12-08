package com.dardang.ghelperclient

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dardang.ghelperclient.connected.GraphScreen
import com.dardang.ghelperclient.service.ConnectionState
import com.dardang.ghelperclient.service.STATE_CONNECTED
import com.dardang.ghelperclient.service.STATE_CONNECTING
import com.dardang.ghelperclient.service.STATE_DISCONNECTED
import com.dardang.ghelperclient.service.STATE_DISCONNECTING
import com.dardang.ghelperclient.service.STATE_ERROR
import com.dardang.ghelperclient.service.STATE_NONE
import com.dardang.ghelperclient.service.STATE_SCANNING
import com.dardang.ghelperclient.service.STATE_SCAN_COMPLETED
import com.dardang.ghelperclient.ui.theme.ContainerColor
import com.dardang.ghelperclient.ui.theme.GHelperClientTheme
import com.dardang.ghelperclient.ui.theme.SensorColor
import com.dardang.ghelperclient.viewModels.AppViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel


    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (!this@MainActivity::viewModel.isInitialized) return

            if (viewModel.isWiFiService && WifiManager.WIFI_STATE_CHANGED_ACTION == intent?.action) {
                val wifiState = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN
                )
                when (wifiState) {
                    WifiManager.WIFI_STATE_DISABLED -> viewModel.setConnectionEnabled(false)
                    WifiManager.WIFI_STATE_ENABLED -> viewModel.setConnectionEnabled(true)
                    // WifiManager.WIFI_STATE_DISABLING -> { }
                    // WifiManager.WIFI_STATE_ENABLING -> { }
                }
            } else if (viewModel.isBLEService && BluetoothAdapter.ACTION_STATE_CHANGED == intent?.action) {
                val btState = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )
                when (btState) {
                    BluetoothAdapter.STATE_OFF -> viewModel.setConnectionEnabled(false)
                    BluetoothAdapter.STATE_ON -> viewModel.setConnectionEnabled(true)
                    // BluetoothAdapter.STATE_TURNING_OFF -> {}
                    // BluetoothAdapter.STATE_TURNING_ON -> {}
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            networkReceiver, IntentFilter()
                .apply {
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                })
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(networkReceiver)
    }


    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {

            viewModel = viewModel<AppViewModel>()

            val context = LocalContext.current

            DisposableEffect(Unit) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                if (!isChangingConfigurations) {
                    viewModel.initConnectionMode()
                }

                onDispose {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (!isChangingConfigurations) {
                        viewModel.disconnect()
                    }
                }
            }

            // State Connected ect
            val connectionState by viewModel.connectionState.collectAsState(STATE_NONE)
            // fetch data at n secs
            val refreshTickState by viewModel.refreshTickState.collectAsState()



            LaunchedEffect(connectionState, refreshTickState) {
                if (connectionState == STATE_CONNECTED) {
                    viewModel.tick(refreshTickState.toLong())
                } else if (connectionState == STATE_DISCONNECTED) {
                    viewModel.stopTick()
                }
            }


            val themeState by viewModel.theme.collectAsState(0)

            val isDarkTheme = when (themeState) {
                0 -> isSystemInDarkTheme()
                1 -> false
                else -> true
            }


            val blePermissionsState =
                rememberMultiplePermissionsState(
                    permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                    else listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                ) { states ->
                    if (states.all { it.value }) {
                        viewModel.connect()
                    } else {
                        Toast.makeText(
                            context,
                            "Bluetooth permissions not granted",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }


            fun connectWithStateCheck() {
                if (!viewModel.connectionEnabled.value) {
                    val modeName =
                        if (viewModel.isWiFiService) "WiFi" else "Bluetooth"
                    Toast.makeText(
                        context,
                        "$modeName is turned off. Please enable it first",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                if (viewModel.isWiFiService) {
                    if (viewModel.serverIpAddress.isEmpty())
                        Toast.makeText(
                            context,
                            "You must set Ip Address\nOpen Settings (Gear Icon) and set WiFi Ip Address there",
                            Toast.LENGTH_LONG
                        ).show()
                    else
                        viewModel.connect()
                } else {
                    when {
                        blePermissionsState.allPermissionsGranted -> {
                            viewModel.connect()
                        }

                        blePermissionsState.shouldShowRationale -> {
                            Toast.makeText(
                                context,
                                "Bluetooth permissions are required for scanning nearby devices.",
                                Toast.LENGTH_LONG
                            ).show()
                            blePermissionsState.launchMultiplePermissionRequest()
                        }

                        !blePermissionsState.allPermissionsGranted && !blePermissionsState.shouldShowRationale -> {
                            blePermissionsState.launchMultiplePermissionRequest()
                        }
                    }
                }
            }

            GHelperClientTheme(darkTheme = isDarkTheme) {

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = ContainerColor
                            ),
                            title = { Title(viewModel) },
                            actions = {

                                ConnectionAction(
                                    connectionState,
                                    icon =
                                        if (viewModel.isWiFiService)
                                            Icons.Default.Wifi
                                        else
                                            Icons.Default.Bluetooth,
                                    onConnect = {
                                        connectWithStateCheck()
                                    },
                                    onDisconnect = {
                                        viewModel.disconnect()
                                    }
                                )

                                OptionsAction(viewModel)
                            }
                        )
                    },

                    ) { innerPadding ->

                    Box(
                        Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {

                        val connectionEnabled by viewModel.connectionEnabled.collectAsState()

                        if (connectionEnabled) {
                            if (connectionState == STATE_CONNECTED) {
                                GraphScreen(viewModel)

                            } else {
                                Text(
                                    "Disconnected",
                                    fontSize = 16.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        } else {


                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val modeName =
                                    if (viewModel.isWiFiService) "WiFi" else "Bluetooth"

                                Text(
                                    "$modeName is off",
                                    fontSize = 16.sp,
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    shape = RectangleShape,
                                    onClick = {
                                        if (viewModel.isWiFiService) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                                            } else {
                                                context.startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
                                            }
                                        } else {
                                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                        }

                                    }) {
                                    Text("Open $modeName")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(viewModel: AppViewModel) {

    val deviceModel by viewModel.deviceModel.collectAsState("")

    if (viewModel.isTablet) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("G-Helper")

            if (deviceModel.isNotEmpty()) {
                Text(deviceModel)
            }
        }
    } else {
        Column {
            Text(
                "G-Helper", fontSize =
                    if (deviceModel.isEmpty())
                        TextUnit.Unspecified
                    else
                        16.sp
            )
            if (deviceModel.isNotEmpty()) {
                Text(
                    maxLines = 1,
                    fontSize = 14.sp,
                    text = deviceModel
                )
            }
        }
    }
}

@Composable
private fun ConnectionAction(
    @ConnectionState connectionState: Int,
    icon: ImageVector,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {

    @Composable
    fun Action(title: String, onClick: () -> Unit) {
        OutlinedButton(
            shape = RectangleShape,
            modifier = Modifier.height(25.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            onClick = onClick
        ) {
            Text(title)
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = icon,
                contentDescription = "",
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }

    when (connectionState) {
        STATE_NONE -> Action("Connect", onConnect)
        STATE_SCANNING -> Action("Scanning...", onDisconnect)
        STATE_SCAN_COMPLETED -> Action("Connecting...", onDisconnect)
        STATE_CONNECTING -> Action("Connecting...", onDisconnect)
        STATE_CONNECTED -> Action("Disconnect", onDisconnect)
        STATE_DISCONNECTING -> Action("Disconnecting...", {})
        STATE_DISCONNECTED -> Action("Connect", onConnect)
        STATE_ERROR -> Action("Error. Retry", onConnect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsAction(viewModel: AppViewModel) {
    var showOptions by remember { mutableStateOf(false) }

    IconButton(onClick = {
        showOptions = true
    }) {
        Icon(Icons.Outlined.Settings, contentDescription = "")
    }

    if (!showOptions) return


    var showWiFiAddressDialog by remember { mutableStateOf(false) }


    if (showWiFiAddressDialog) {

        var ipAddress by remember { mutableStateOf(viewModel.serverIpAddress) }
        val focusRequester = remember { FocusRequester() }

        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showWiFiAddressDialog = false },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = SensorColor),
                    shape = RectangleShape,
                    onClick = {
                        viewModel.serverIpAddress = ipAddress
                        showWiFiAddressDialog = false
                    }) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(
                    border = BorderStroke(1.dp, SensorColor),
                    shape = RectangleShape,
                    onClick = { showWiFiAddressDialog = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TextField(
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester),
                    value = TextFieldValue(text = ipAddress, TextRange(ipAddress.length)),
                    label = { Text("Server IP Address") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.serverIpAddress = ipAddress
                        showWiFiAddressDialog = false
                    }),
                    onValueChange = { ipAddress = it.text.take(15) })

            })
        LaunchedEffect(Unit) {
            delay(500)
            focusRequester.requestFocus()
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        sheetState = sheetState,
        shape = RectangleShape,
        onDismissRequest = { showOptions = false }) {

        val padding = 20.dp

        @Composable
        fun DropDownItem(
            modifier: Modifier = Modifier,
            text: String,
            selected: Boolean,
            enabled: Boolean = true,
            horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
            onClick: () -> Unit,
            trailingContent: @Composable RowScope.() -> Unit
        ) {

            Row(
                modifier = modifier
                    .let {
                        if (enabled) it
                        else it.alpha(0.5f)
                    }
                    .clickable(enabled) { onClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = horizontalArrangement
            ) {
                Box(Modifier.size(12.dp).let {
                    if (selected) it.background(SensorColor)
                    else it.border(1.dp, SensorColor)
                })
                Spacer(Modifier.width(10.dp))
                Text(
                    text = text,
                    color = if (selected)
                        SensorColor else MaterialTheme.colorScheme.onSurface
                )
                trailingContent()
            }
            Spacer(Modifier.height(5.dp))
        }


        val connectionState by viewModel.connectionState.collectAsState()
        val refreshTickState by viewModel.refreshTickState.collectAsState()
        val themeState by viewModel.theme.collectAsState()

        val connected = connectionState == STATE_CONNECTED

        val selectedServiceIndex = if (viewModel.isWiFiService) 0 else 1

        Text(
            "Connection Mode",
            modifier = Modifier.padding(
                start = padding,
                end = padding,
                top = 5.dp
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = padding),
            horizontalArrangement = Arrangement.SpaceBetween) {
            DropDownItem(
                modifier = Modifier.height(40.dp),
                "Wi-Fi",
                selected = selectedServiceIndex == 0,
                onClick = {
                    viewModel.initConnectionMode(0)
                }
            ) {
                if (selectedServiceIndex == 0) {
                    Column(
                        Modifier
                            .width(140.dp)
                            .padding(start = 5.dp)
                            .clickable(true) {
                                showWiFiAddressDialog = true
                            }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = viewModel.serverIpAddress.ifEmpty { "Server Ip Address" },
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                modifier = Modifier.size(18.dp),
                                contentDescription = ""
                            )
                        }
                        HorizontalDivider(thickness = 1.dp)
                    }
                } else {
                    Spacer(Modifier.padding(start = 5.dp).width(140.dp))
                }
            }

            DropDownItem(
                modifier = Modifier.height(40.dp),
                "Bluetooth LE",
                selected = selectedServiceIndex == 1,
                onClick = { viewModel.initConnectionMode(1) }
            ) { }
        }


        HorizontalDivider(thickness = 1.dp, modifier = Modifier.padding(vertical = padding))

        Text(
            "Refresh",
            modifier = Modifier.padding(
                start = padding,
                end = padding,
                top = 5.dp
            ).let {
                if (!connected) it.alpha(0.5f)
                else it
            }
        )

        val tickValues = remember { arrayOf(500, 1000, 2000) }
        val tickTexts = remember { arrayOf("0.5 sec", "1 sec", "2 sec") }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = padding),
            horizontalArrangement = Arrangement.SpaceBetween) {
            tickTexts.forEachIndexed { i, it ->
                DropDownItem(
                    modifier = Modifier.height(40.dp),
                    text = it,
                    enabled = connected,
                    selected = refreshTickState == tickValues[i],
                    onClick = {
                        viewModel.setRefreshTickState(tickValues[i])
                    }
                ) {}
            }
        }

        HorizontalDivider(thickness = 1.dp, modifier = Modifier.padding(vertical = padding))

        Text(
            "Theme",
            modifier = Modifier.padding(
                start = padding,
                end = padding,
                top = 5.dp
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = padding),
            horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("System", "Light", "Dark").forEachIndexed { i, it ->
                DropDownItem(
                    modifier = Modifier.height(40.dp),
                    text = it,
                    selected = themeState == i,
                    onClick = {
                        viewModel.setTheme(i)
                    }
                ) {}
            }
        }

        Spacer(Modifier.height(padding))
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GHelperClientTheme {

    }
}