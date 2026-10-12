package com.example.hello_abd.services

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioStreamer(private val scope: CoroutineScope) {

    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
    }

    private val audioChannel = Channel<ShortArray>(Channel.BUFFERED)
    private var streamingJob: Job? = null
    var isStreaming = false
        private set

    fun startStreaming(fullUrl: String) {
        // CLEAN THE URL: Remove any quotes (") and extra spaces
        val cleanUrl = fullUrl.replace("\"", "").replace("'", "").trim()
        
        if (isStreaming) return
        isStreaming = true
        Log.d("AudioStreamer", "🚀 Attempting connection to: $cleanUrl")
        
        streamingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isStreaming) {
                try {
                    httpClient.webSocket(
                        urlString = cleanUrl,
                        request = {
                            // Bypasses ngrok's browser warning page
                            header("ngrok-skip-browser-warning", "true")
                        }
                    ) {
                        Log.d("AudioStreamer", "✅ SUCCESS: Audio streaming to laptop active!")
                        
                        while (isActive && isStreaming) {
                            try {
                                val frame = audioChannel.receive()
                                val byteBuffer = ByteBuffer.allocate(frame.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                                for (s in frame) byteBuffer.putShort(s)
                                send(Frame.Binary(true, byteBuffer.array()))
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.e("AudioStreamer", "Frame error: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isStreaming) {
                        Log.e("AudioStreamer", "❌ Connection failed: ${e.message}")
                        delay(5000) // retry every 5 seconds
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        isStreaming = false
        streamingJob?.cancel()
        streamingJob = null
        Log.d("AudioStreamer", "🛑 Streaming stopped")
    }

    fun sendAudioFrame(frame: ShortArray) {
        if (isStreaming) {
            audioChannel.trySend(frame.copyOf())
        }
    }
}
