package com.example.hello_abd.utils

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.hello_abd.transparentActivity.CommandRelayActivity

object AppLauncher {

    fun launch(context: Context, intent: Intent?) {
        if (intent == null) return
        val relayIntent = Intent(context, CommandRelayActivity::class.java).apply {
            putExtra("EXTRA_LAUNCH_INTENT", intent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(relayIntent)
    }

    fun openYouTube(context: Context): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    fun openGoogle(context: Context): Intent {
        return context.packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
            ?: Intent(Intent.ACTION_WEB_SEARCH).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }

    fun searchYouTube(context: Context, query: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun searchGoogle(context: Context, query: String): Intent {
        return Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun setAlarm(context: Context): Intent? {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Toast.makeText(context, "No clock app found to set alarm", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun openPlayStore(context: Context): Intent? {
        val intent = context.packageManager.getLaunchIntentForPackage("com.android.vending")
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent
        } else {
            Toast.makeText(context, "Google Play Store is not installed", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun openCamera(context: Context): Intent? {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun toggleFlashlight(context: Context, enable: Boolean) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not access flashlight", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWifiSettings(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    fun volumeUp(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    fun volumeDown(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    fun openWhatsApp(context: Context): Intent? {
        val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent
        } else {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
            null
        }
    }

    @SuppressLint("Range")
    fun initiateCall(context: Context, contactName: String): Intent? {
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$contactName%")
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)

        var phoneNumber: String? = null
        if (cursor?.moveToFirst() == true) {
            val foundName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
            phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
            Log.d("AppLauncher", "Search for '$contactName' found first match: Name = $foundName, Number = $phoneNumber")
        }
        cursor?.close()

        return if (phoneNumber != null) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Toast.makeText(context, "Contact '$contactName' not found.", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
