package com.example.camera_x

import android.graphics.Rect
import com.google.mlkit.vision.face.Face

class FaceViewModel(face: Face) {
    val boundingRect: Rect = face.boundingBox
    val smilingProbability: Float? = face.smilingProbability
}