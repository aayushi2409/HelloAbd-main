package com.example.hello_abd.transparentActivity

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class CommandRelayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val externalIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_LAUNCH_INTENT", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_LAUNCH_INTENT")
        }

        if (externalIntent != null) {
            try {
                // The service context already provides the NEW_TASK flag.
                startActivity(externalIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not launch the requested app.", Toast.LENGTH_SHORT).show()
            }
        }

        finish() // Close this invisible activity immediately
    }
}