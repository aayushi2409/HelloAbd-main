package com.example.hello_abd.app

import android.app.Application
import com.example.hello_abd.viewmodel.JarvisViewModel

class MainApplication : Application() {

    // Create a single, shared instance of the ViewModel
    val jarvisViewModel by lazy { JarvisViewModel() }

}
