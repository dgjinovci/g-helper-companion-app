package com.dardang.ghelperclient.viewModels

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.util.Log
import androidx.annotation.IntRange
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.dardang.ghelperclient.service.BLEService
import com.dardang.ghelperclient.service.CPU
import com.dardang.ghelperclient.service.FAN_MID
import com.dardang.ghelperclient.service.GPU
import com.dardang.ghelperclient.service.GpuMode
import com.dardang.ghelperclient.service.IService
import com.dardang.ghelperclient.service.MODE_GPU_STANDARD
import com.dardang.ghelperclient.service.Mode
import com.dardang.ghelperclient.service.PERFORMANCE_MODE_BALANCED
import com.dardang.ghelperclient.service.STATE_CONNECTED
import com.dardang.ghelperclient.service.STATE_NONE
import com.dardang.ghelperclient.service.TYPE_CMD
import com.dardang.ghelperclient.service.TYPE_INFO
import com.dardang.ghelperclient.service.TYPE_MODES
import com.dardang.ghelperclient.service.TYPE_SENSOR
import com.dardang.ghelperclient.service.WiFiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


private val <T> StateFlow<T>.mutable: MutableStateFlow<T>
    get() = this as MutableStateFlow<T>

private val <T> Flow<T>.mutable: MutableSharedFlow<T>
    get() = this as MutableSharedFlow<T>


class AppViewModel(app: Application) : AndroidViewModel(app) {

    private lateinit var service: IService

    data class ServiceStats(
        val cpuFan: IService.FanValue = IService.FanValue(0, 0),
        //val cpuUsage: Int = 0,
        val cpuTemp: Int = 0,
        val gpuFan: IService.FanValue = IService.FanValue(0, 0),
        //val gpuUsage: Int = 0,
        val gpuTemp: Int = 0,
        val midFan: IService.FanValue = IService.FanValue(0, 0),
        val memory: IService.Memory = IService.Memory(0, 0, 0)
    )


//    data class ServiceModes(
//        @Mode
//        val performanceMode: Int = PERFORMANCE_MODE_BALANCED,
//        @GpuMode
//        val gpuMode: Int = MODE_GPU_STANDARD,
//        val screen : Screen,
//        val auraKeyboardModes : List<AuraMode>,
//        val auraKeyboardMode = AuraMode
//    )

    init {
        Log.d("DD", "Init")
    }

    private val prefs = app.getSharedPreferences(app.packageName, MODE_PRIVATE)


    val isTablet: Boolean
        get() {
            val layoutSize =
                application.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
            return layoutSize >= Configuration.SCREENLAYOUT_SIZE_LARGE
        }

    val isLandscape: Boolean
        get() {
            val orientation = application.resources.configuration.orientation
            return orientation == Configuration.ORIENTATION_LANDSCAPE
        }

    val isWiFiService: Boolean
        get() = service is WiFiService

    val isBLEService: Boolean
        get() = service is BLEService

    var serverIpAddress: String = ""
        get() = prefs.getString("server_ip_address","")!!
        set(value) {
            prefs.edit {
                putString("server_ip_address", value)
            }
            field = value
        }

    val theme: StateFlow<Int> = MutableStateFlow(prefs.getInt("theme", 0))

    fun setTheme(theme: Int) {
        this.theme.mutable.value = theme
        prefs.edit { putInt("theme", theme) }
    }


    var connectionEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    fun setConnectionEnabled(enabled: Boolean) {
        connectionEnabled.mutable.value = enabled
    }

    // 0 - ProgressBar
    // 1 - ProgressBar (from Graph-Smooth)
    // 2 - Graph
    // 3 - Graph-Smooth
    val sensorMode: StateFlow<Int> = MutableStateFlow(prefs.getInt("sensor_mode", 0))

    fun setSensorMode(value: Int) {
        sensorMode.mutable.value = value
        prefs.edit { putInt("sensor_mode", value) }
    }

    val isRPM: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean("is_rpm", false))

    fun setRPM(value: Boolean) {
        isRPM.mutable.value = value
        prefs.edit { putBoolean("is_rpm", value) }
    }

    val isTempCelsius: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean("is_celsius", true))

    fun setCelsius(value: Boolean) {
        isTempCelsius.mutable.value = value
        prefs.edit { putBoolean("is_celsius", value) }
    }

    val refreshTickState: StateFlow<Int> = MutableStateFlow(prefs.getInt("time_tick", 2000))

    fun setRefreshTickState(value: Int) {
        refreshTickState.mutable.value = value
        prefs.edit { putInt("time_tick", value) }
    }

    val connectionState: StateFlow<Int> = MutableStateFlow(STATE_NONE)

    val deviceModel: StateFlow<String> = MutableStateFlow("")

    val performanceMode: StateFlow<@Mode Int> = MutableStateFlow(PERFORMANCE_MODE_BALANCED)
    val gpuMode: StateFlow<@GpuMode Int> = MutableStateFlow(MODE_GPU_STANDARD)

    val cpuUsage: Flow<Int> = MutableSharedFlow(0)
    val gpuUsage: Flow<Int> = MutableSharedFlow(0)

    val serviceStats: StateFlow<ServiceStats> = MutableStateFlow(ServiceStats())
    // val serviceModes: StateFlow<ServiceModes> = MutableStateFlow(ServiceModes())

    private var networkMode: Int
        get() = prefs.getInt("connection_mode", 0)
        set(value) {
            prefs.edit { putInt("connection_mode", value) }
        }

    fun initConnectionMode(@IntRange(0, 1) mode: Int = networkMode) {

        // same mode, initialized
        if (this::service.isInitialized && mode == networkMode)
            return

        networkMode = mode

        if (this::service.isInitialized) {
            service.disconnect()
        }

        if (mode == 0) {
            connectionEnabled.mutable.value =
                (application.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled

            service = WiFiService(application)

        } else {
            connectionEnabled.mutable.value =
                (application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled

            service = BLEService(application)
        }

        service.sensorListener = object : IService.IReadListener {

            override fun onConnection(state: Int) {
                connectionState.mutable.value = state
                if (state == STATE_CONNECTED) {
                    service.read(TYPE_INFO)
                }
            }

            override fun onRead(type: Int) {

                when (type) {
                    TYPE_INFO -> {
                        deviceModel.mutable.value = service.deviceModel
                    }

                    TYPE_MODES -> {
                        performanceMode.mutable.value = service.performanceMode
                        gpuMode.mutable.value = service.gpuMode

                        // serviceModes.mutable.value = ServiceModes(
                        //     performanceMode = service.performanceMode,
                        //     gpuMode = service.gpuMode,
                        //     screen = service.screen,
                        //     auraKeyboardModes = service.auraKeyboardModes,
                        //     auraKeyboardMode = service.auraKeyboardMode
                        // )
                    }

                    TYPE_SENSOR -> {

                        viewModelScope.launch {
                            cpuUsage.mutable.emit(service.cpuUsage)
                            gpuUsage.mutable.emit(service.gpuUsage)
                        }
                        serviceStats.mutable.value =
                            ServiceStats(
                                cpuFan = service.fanValues[CPU]!!,
                                //cpuUsage = service.cpuUsage,
                                cpuTemp = service.temperatureValues[CPU]!!,
                                gpuFan = service.fanValues[GPU]!!,
                                //gpuUsage = service.gpuUsage,
                                gpuTemp = service.temperatureValues[GPU]!!,
                                midFan = service.fanValues[FAN_MID]!!,
                                memory = service.memory
                            )
                    }

                    TYPE_CMD -> {}
                }
            }
        }
    }


    fun disconnect() = service.disconnect()

    fun connect() {
        if (service is WiFiService) {
            (service as WiFiService).serverIp = serverIpAddress
        }
        service.connect()
    }
    fun setPerformanceMode(@Mode mode: Int) {
        service.setPerformanceMode(mode)
    }

    fun setGpuMode(@GpuMode mode: Int) {
        service.setGpuMode(mode)
    }

//    fun setScreenMode(@ScreenMode mode: Int) {
//        service?.setScreenMode(mode)
//        // request a type=modes read to make sure this mode is set on pc
//        viewModelScope.launch {
//            delay(500)
//            service?.read(TYPE_MODES)
//        }
//    }
//
//
//    fun setKeyboardMode(mode: AuraMode) {
//        service?.setKeyboardMode(mode)
//        // request a type=modes read to make sure this mode is set on pc
//        service?.read(TYPE_MODES)
//    }


    private var sensorsJob: Job? = null
    fun tick(interval: Long) {

        sensorsJob?.cancel()
        sensorsJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (service.connectionState == STATE_CONNECTED) {
                    service.read(TYPE_SENSOR)
                    service.read(TYPE_MODES) // read modes too
                }
                delay(interval)
            }
        }
    }

    fun stopTick() {
        sensorsJob?.cancel()
    }

}