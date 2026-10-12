package com.example.hello_abd.services

import android.content.Context
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorder(private val context: Context, private val scope: CoroutineScope) {

    private var fileOutputStream: FileOutputStream? = null
    private var currentFile: File? = null
    private val storage = FirebaseStorage.getInstance()
    
    var isRecording = false
        private set

    fun startRecording(userId: String) {
        if (isRecording) return
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "rec_${userId}_$timestamp.pcm"
            currentFile = File(context.filesDir, fileName)
            fileOutputStream = FileOutputStream(currentFile)
            isRecording = true
            Log.d("AudioRecorder", "Local recording started: ${currentFile?.name}")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to init local file: ${e.message}")
        }
    }

    fun writeFrame(frame: ShortArray) {
        if (!isRecording) return
        try {
            val byteBuffer = ByteBuffer.allocate(frame.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in frame) byteBuffer.putShort(s)
            fileOutputStream?.write(byteBuffer.array())
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Write error: ${e.message}")
        }
    }

    fun stopAndUpload() {
        if (!isRecording) return
        isRecording = false
        
        try {
            fileOutputStream?.close()
            fileOutputStream = null
            
            val fileToUpload = currentFile
            if (fileToUpload != null && fileToUpload.exists()) {
                uploadFileToFirebase(fileToUpload)
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Stop/Upload error: ${e.message}")
        }
    }

    private fun uploadFileToFirebase(file: File) {
        scope.launch(Dispatchers.IO) {
            try {
                val storageRef = storage.reference.child("recordings/${file.name}")
                val fileUri = android.net.Uri.fromFile(file)
                
                storageRef.putFile(fileUri).await()
                Log.d("AudioRecorder", "✅ Cloud upload success: ${file.name}")
                file.delete() 
            } catch (e: Exception) {
                Log.e("AudioRecorder", "❌ Cloud upload failed: ${e.message}")
            }
        }
    }
}
