package com.example.camera_x.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val photoUris = mutableListOf<Uri>()
    val videoUris = mutableListOf<Uri>()
}
