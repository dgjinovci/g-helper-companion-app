package com.dardang.ghelperclient.service

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer


class WiFiService(context: Context) : IService(context) {

    private val scope = MainScope()
    private var socket = Socket()

    private var serverPort = 8080

    private var job: Job? = null

    var serverIp: String = ""

    override fun connect() {

        job = scope.launch(Dispatchers.IO) {

            val ip = serverIp
            val ok = ip.isNotEmpty()

            if (!ok) {
                connectionState = STATE_ERROR
                return@launch
            }

            Log.d("DD", "connecting to $ip")

            try {
                connectionState = STATE_CONNECTING

                // recreate, reuse is not available if closed
                try {
                    if (!socket.isClosed)
                        socket.close()
                } finally {
                }
                socket = Socket()
                socket.connect(InetSocketAddress(ip, serverPort), 10000)
                socket.soTimeout = 4000

                connectionState = STATE_CONNECTED

            } catch (e: Exception) {
                Log.e("DD", "connect error", e)
                connectionState = STATE_ERROR
            }
            //  }
        }
    }


    override fun disconnect() {

        scope.launch {

            connectionState = STATE_DISCONNECTING

            job?.cancelAndJoin()

            try {
                socket.close()
            } finally {
            }

            connectionState = STATE_DISCONNECTED
        }
    }

    private val mutex = Mutex()

    override fun read(type: Int) {

        if (!socket.isConnected) {
            if (connectionState == STATE_CONNECTED) {
                connectionState = STATE_DISCONNECTED
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val command = ByteBuffer
                    .allocate(1)
                    .put(type.toByte())
                    .array()


                try {
                    socket.outputStream?.write(command)

                    val data = ByteArray(1024)
                    val len = socket.inputStream?.read(data)

                    withContext(Dispatchers.Main) {
                        onReadData(type, data)
                    }
                } catch (se: SocketException) {
                    try {
                        socket.close()
                    } finally {
                    }
                    connectionState = STATE_ERROR
                    Log.e("DD", "SEx", se)
                } catch (e: Exception) {
                    Log.e("DD", "Ex", e)
                }
            }
        }
    }

    override fun sendCmd(data: ByteArray) {
        if (!socket.isConnected) {
            if (connectionState == STATE_CONNECTED) {
                connectionState = STATE_DISCONNECTED
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                socket.outputStream?.write(data)
            }
        }
    }
}