package com.dardang.ghelperclient.service

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

const val STATE_NONE = 0
const val STATE_SCANNING = 1
const val STATE_SCAN_COMPLETED = 2
const val STATE_CONNECTING = 3
const val STATE_CONNECTED = 4
const val STATE_DISCONNECTING = 5
const val STATE_DISCONNECTED = 6
const val STATE_ERROR = -1

const val TYPE_INFO = 0
const val TYPE_MODES = 1
const val TYPE_SENSOR = 2
const val TYPE_CMD = 3

const val CMD_SENSORS = 0
const val CMD_PERFORMANCE_MODE = 1
const val CMD_GPU_MODE = 2
const val CMD_AURA_KEYBOARD_MODE = 3
const val CMD_AURA_SCREEN_MODE = 4

// Matching with GHelper Desktop app
const val PERFORMANCE_MODE_SILENT = 2
const val PERFORMANCE_MODE_BALANCED = 0
const val PERFORMANCE_MODE_TURBO = 1

// Matching with GHelper Desktop app
const val MODE_GPU_ECO = 0
const val MODE_GPU_STANDARD = 1
const val MODE_GPU_ULTIMATE = 2
const val MODE_GPU_OPTIMIZED = 3

const val MODE_SCREEN_60HZ = 0
const val MODE_SCREEN_120HZ = 1
const val MODE_SCREEN_MINILED = 2
const val MODE_SCREEN_AUTO = 3


const val CPU = 0
const val GPU = 1
const val FAN_MID = 2


@Retention(AnnotationRetention.SOURCE)
@IntDef(TYPE_INFO, TYPE_MODES, TYPE_SENSOR, TYPE_CMD)
annotation class Type

@Retention(AnnotationRetention.SOURCE)
@IntDef(PERFORMANCE_MODE_SILENT, PERFORMANCE_MODE_BALANCED, PERFORMANCE_MODE_TURBO)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class Mode

@Retention(AnnotationRetention.SOURCE)
@IntDef(MODE_GPU_ECO, MODE_GPU_STANDARD, MODE_GPU_ULTIMATE, MODE_GPU_OPTIMIZED)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class GpuMode

//@Retention(AnnotationRetention.SOURCE)
//@IntDef(MODE_SCREEN_60HZ, MODE_SCREEN_120HZ, MODE_SCREEN_MINILED, MODE_SCREEN_AUTO)
//annotation class ScreenMode


@Retention(AnnotationRetention.SOURCE)
@IntDef(CPU, GPU)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class Sensor

@Retention(AnnotationRetention.SOURCE)
@IntDef(CPU, GPU, FAN_MID)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class FanSensor

@Retention(AnnotationRetention.SOURCE)
@IntDef(
    STATE_NONE,
    STATE_SCANNING,
    STATE_SCAN_COMPLETED,
    STATE_CONNECTING,
    STATE_CONNECTED,
    STATE_DISCONNECTING,
    STATE_DISCONNECTED,
    STATE_ERROR
)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class ConnectionState


//enum class AuraMode(val code: Int, val text: String) {
//    AuraStatic(0, "Static"),
//    AuraBreathe(1, "Breathe"),
//    AuraColorCycle(2, "Color Cycle"),
//    AuraRainbow(3, "Rainbow"),
//    Star(4, "Star"),
//    Rain(5, "Rain"),
//    Highlight(6, "Highlight"),
//    Laser(7, "Laser"),
//    Ripple(8, "Ripple"),
//    AuraStrobe(10, "Strobe"),
//    Comet(11, "Comet"),
//    Flash(12, "Flash"),
//    HEATMAP(20, "Heatmap"),
//    GPUMODE(21, "Gpu Mode"),
//    AMBIENT(22, "Ambient"),
//}

internal val <T> StateFlow<T>.mutable: MutableStateFlow<T>
    get() = this as MutableStateFlow<T>


private val <T> Flow<T>.mutable: MutableSharedFlow<T>
    get() = this as MutableSharedFlow<T>


abstract class IService(protected val context: Context) {


    interface IReadListener {
        fun onConnection(@ConnectionState state: Int) {}
        fun onRead(@Type type: Int)
    }


    //val connectionState: StateFlow<@ConnectionState Int> = MutableStateFlow(STATE_NONE)
    var connectionState = STATE_NONE
        protected set(value) {
            field = value
            sensorListener?.onConnection(value)
        }

    data class FanValue(val current: Int, val max: Int) {
        val rpm: Int
            get() = current * 100

        val percent: Float
            @FloatRange(0.0, 1.0)
            get() = current.toFloat() / max(max.toFloat(), 1f)
    }

    // memory in KB
    data class Memory(
        val total: Int,
        val free: Int,
        val used: Int
    ) {
        val percent: Float
            get() = if (total <= 0) 0f else used.toFloat() / total
    }

//    data class Screen(
//        val screenEnabled: Boolean = false,
//        val screenAuto: Boolean = false,
//        val overdriveSetting: Boolean = false,
//        val hdr: Boolean = false,
//        val hdrControl: Int = 0,
//        val fhd: Int = 0,
//        val miniLed1: Int = 0,
//        val miniLed2: Int = 0,
//        val minRate: Int = 0,
//        val maxRate: Int = 0,
//        val frequency: Int = 0,
//        val maxFrequency: Int = 0
//    )


    var deviceModel: String = ""
        private set

    var cpuUsage: Int = 0
        private set

    var gpuUsage: Int = 0
        private set

    var memory: Memory = Memory(0, 0, 0)
        private set

    val fanValues: Map<@FanSensor Int, FanValue> = mutableMapOf<@FanSensor Int, FanValue>(
        CPU to FanValue(0, 1),
        GPU to FanValue(0, 1),
        FAN_MID to FanValue(0, 1)
    )
    val temperatureValues: Map<@Sensor Int, Int> = mutableMapOf<@Sensor Int, Int>(
        CPU to 0,
        GPU to 0
    )

    //val performanceMode: StateFlow<@Mode Int> = MutableStateFlow(PERFORMANCE_MODE_BALANCED)
    //val gpuMode: StateFlow<@GpuMode Int> = MutableStateFlow(MODE_GPU_STANDARD)

    var performanceMode: Int = PERFORMANCE_MODE_BALANCED
        private set
    var gpuMode: Int = MODE_GPU_STANDARD
        private set

    // var auraKeyboardModes: List<AuraMode> = listOf()
    //     private set
//
    // var auraKeyboardMode: AuraMode = AuraMode.AuraStatic
    //     private set
//
    // var screen: Screen = Screen()
    //     private set

    //fun getFan(@Sensor type: Int): FanValue = currentFanValues[type]!!

    //fun getSensorTemp(@Sensor type: Int): Int = currentTempValues[type]!!

    var sensorListener: IReadListener? = null


    @SuppressLint("MissingPermission")
    fun setPerformanceMode(@Mode mode: Int) {
        val data = ByteBuffer
            .allocate(3)
            .put(TYPE_CMD.toByte())
            .put(CMD_PERFORMANCE_MODE.toByte())
            .put(mode.toByte())
            .array()

        sendCmd(data)
    }

    @SuppressLint("MissingPermission")
    fun setGpuMode(@GpuMode mode: Int) {
        val data = ByteBuffer
            .allocate(3)
            .put(TYPE_CMD.toByte())
            .put(CMD_GPU_MODE.toByte())
            .put(mode.toByte()).array()

        sendCmd(data)
    }

//    fun setScreenMode(@ScreenMode mode: Int) {
//        val data = ByteBuffer
//            .allocate(3 * 4)
//            .putInt(TYPE_CMD)
//            .putInt(CMD_AURA_SCREEN_MODE)
//            .putInt(mode).array()
//
//        sendCmd(data)
//    }
//
//
//    @SuppressLint("MissingPermission")
//    fun setKeyboardMode(mode: AuraMode) {
//        val data = ByteBuffer
//            .allocate(3 * 4)
//            .putInt(TYPE_CMD)
//            .putInt(CMD_AURA_KEYBOARD_MODE)
//            .putInt(mode.code).array()
//
//        sendCmd(data)
//    }

    abstract fun read(@Type type: Int)

    protected abstract fun sendCmd(data: ByteArray)

    abstract fun connect()

    abstract fun disconnect()


    protected fun onReadData(type: Int, data: ByteArray) {
        when (type) {
            TYPE_INFO -> {
                try {
                    val length = data[0].toInt() and 0xFF
                    deviceModel = String(data, 1, length, Charsets.US_ASCII)

                } finally {
                }

                sensorListener?.onRead(TYPE_INFO)
            }

            TYPE_MODES -> {

                var idx = 0
                performanceMode = data[idx++].toInt() and 0xFF
                gpuMode = data[idx++].toInt() and 0xFF

                /*screen = Screen(
                    screenEnabled = (data[idx++].toInt() and 0xFF) == 1,
                    screenAuto = (data[idx++].toInt() and 0xFF) == 1,
                    overdriveSetting = (data[idx++].toInt() and 0xFF) == 1,
                    hdr = (data[idx++].toInt() and 0xFF) == 1,
                    hdrControl = data[idx++].toInt() and 0xFF,
                    fhd = data[idx++].toInt() and 0xFF,
                    miniLed1 = data[idx++].toInt() and 0xFF,
                    miniLed2 = data[idx++].toInt() and 0xFF,
                    minRate = ByteBuffer.wrap(data, idx.let { idx += 4; it }, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).int,
                    maxRate = ByteBuffer.wrap(data, idx.let { idx += 4; it }, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).int,
                    frequency = ByteBuffer.wrap(data, idx.let { idx += 4; it }, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).int,
                    maxFrequency = ByteBuffer.wrap(data, idx.let { idx += 4; it }, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).int
                )


                try {
                    auraKeyboardMode = AuraMode.entries.toTypedArray()[data[idx++].toInt() and 0xFF]
                } finally {
                }


                val kbModesLen = data[idx].toInt() and 0xFF
                val auraModes = mutableListOf<AuraMode>()
                for (i in 1 until kbModesLen) {
                    val mode = data[idx + i].toInt() and 0xFF
                    when (mode) {
                        AuraMode.AuraStatic.code -> auraModes.add(AuraMode.AuraStatic)
                        AuraMode.AuraBreathe.code -> auraModes.add(AuraMode.AuraBreathe)
                        AuraMode.AuraColorCycle.code -> auraModes.add(AuraMode.AuraColorCycle)
                        AuraMode.AuraRainbow.code -> auraModes.add(AuraMode.AuraRainbow)
                        AuraMode.Star.code -> auraModes.add(AuraMode.Star)
                        AuraMode.Rain.code -> auraModes.add(AuraMode.Rain)
                        AuraMode.Highlight.code -> auraModes.add(AuraMode.Highlight)
                        AuraMode.Laser.code -> auraModes.add(AuraMode.Laser)
                        AuraMode.Ripple.code -> auraModes.add(AuraMode.Ripple)
                        AuraMode.AuraStrobe.code -> auraModes.add(AuraMode.AuraStrobe)
                        AuraMode.Comet.code -> auraModes.add(AuraMode.Comet)
                        AuraMode.Flash.code -> auraModes.add(AuraMode.Flash)
                        AuraMode.HEATMAP.code -> auraModes.add(AuraMode.HEATMAP)
                        AuraMode.GPUMODE.code -> auraModes.add(AuraMode.GPUMODE)
                        AuraMode.AMBIENT.code -> auraModes.add(AuraMode.AMBIENT)
                    }
                }
                auraKeyboardModes = auraModes
*/

                sensorListener?.onRead(TYPE_MODES)
                //onReadResult?.invoke(type, String(data))
                //onReadResult = null
            }

            TYPE_SENSOR -> {

                // Get data as bye, they wont reach value > 254
                val cpuFanRpm = data[0].toInt() and 0xFF
                val gpuFanRpm = data[1].toInt() and 0xFF
                val midFanRpm = data[2].toInt() and 0xFF

                val maxCpuFanRpm = data[3].toInt() and 0xFF
                val maxGpuFanRpm = data[4].toInt() and 0xFF
                val maxMidFanRpm = data[5].toInt() and 0xFF

                val cpuTemp = data[6].toInt() and 0xFF
                val gpuTemp = data[7].toInt() and 0xFF


                // convert 0.0 to 1.0
                cpuUsage = data[8].toInt() and 0xFF
                gpuUsage = data[9].toInt() and 0xFF


                val totalMem = ByteBuffer.wrap(data, 10, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val freeMem = ByteBuffer.wrap(data, 14, 4).order(ByteOrder.LITTLE_ENDIAN).int

                memory = Memory(totalMem, freeMem, totalMem - freeMem)

                fanValues as MutableMap<@FanSensor Int, FanValue>
                fanValues[CPU] = FanValue(cpuFanRpm, maxCpuFanRpm)
                fanValues[GPU] = FanValue(gpuFanRpm, maxGpuFanRpm)
                fanValues[FAN_MID] = FanValue(midFanRpm, maxMidFanRpm)

                temperatureValues as MutableMap<@Sensor Int, Int>
                temperatureValues[CPU] = cpuTemp
                temperatureValues[GPU] = gpuTemp

                //log("cpu:$cpuFan$ext gpu:$gpuFan$ext mid:$midFan$ext  ctemp:$cpuTemp  gtemp:$gpuTemp")
                sensorListener?.onRead(TYPE_SENSOR)
            }
        }
    }
}