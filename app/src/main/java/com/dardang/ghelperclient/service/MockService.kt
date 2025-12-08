package com.dardang.ghelperclient.service

import android.content.Context
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextInt

class MockService(context: Context) : IService(context) {

    private val scope = MainScope()

    private var tick = 0
    private fun NDRandom(mean: Double, stdDev: Double): Double {
        // This is a placeholder. In a real scenario, you'd use a library function
        // val distribution = NormalDistribution(mean, stdDev)
        // return distribution.nextValue()

        // For demonstration, a simple approximation or a uniform value:
        return Random.nextDouble(mean - stdDev, mean + stdDev) // Not truly normal
    }

    override fun read(type: Int) {

        tick++

        val data = when (type) {
            TYPE_INFO -> {
                val name = "Mocked Model"
                ByteBuffer.allocate(name.length + 1)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .put(name.length.toByte())
                    .put(name.toByteArray())

            }

            TYPE_MODES -> {
                ByteBuffer.allocate(2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .put(performanceMode.toByte())
                    .put(gpuMode.toByte())
            }

            TYPE_SENSOR -> {
                val totalMemKb = 1024 * 1024 * 32
                val freeMem = totalMemKb * Random.nextFloat()


                val r = (sin(System.currentTimeMillis().toDouble()*10000) + 1).toInt() * Random.nextInt(10, 80)

                ByteBuffer.allocate(18)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .put(r.toByte())
                    .put(r.toByte())
                    .put(r.toByte())
                    .put(100.toByte())
                    .put(100.toByte())
                    .put(100.toByte())
                    .put(r.toByte())
                    .put(r.toByte())
                    .put(r.toByte())
                    .put(r.toByte())
                    .putInt(totalMemKb)
                    .putInt(freeMem.toInt())
            }

            else -> {
                return
            }
        }
        onReadData(type, data.array())

    }

    override fun sendCmd(data: ByteArray) {

        val cmd = data[0].toInt()

        if (cmd == TYPE_CMD) {

            var p = performanceMode
            var g = gpuMode
            if (data[1].toInt() == CMD_PERFORMANCE_MODE) {
                p = data[2].toInt()
            } else if (data[1].toInt() == CMD_GPU_MODE) {
                g = data[2].toInt()
            }

            onReadData(TYPE_MODES, byteArrayOf(p.toByte(), g.toByte()))
        }
    }

    override fun connect() {

        connectionState = STATE_CONNECTING

        scope.launch {
            delay(1000)
            connectionState = STATE_CONNECTED
        }
    }

    override fun disconnect() {
        connectionState = STATE_DISCONNECTED
    }
}