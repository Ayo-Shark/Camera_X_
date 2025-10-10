package com.example.camera_x

import android.net.Uri

class Media {
    sealed class MediaItem {
        data class Photo(val uri: Uri) : MediaItem()
        data class Video(val uri: Uri) : MediaItem()
    }
}