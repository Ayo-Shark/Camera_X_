package com.example.camera_x

import android.media.browse.MediaBrowser
import android.net.Uri
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    val mediaItems = mutableListOf<MediaBrowser.MediaItem>()
    val photoUris = mutableListOf<Uri>()
    val videoUris = mutableListOf<Uri>()
}
