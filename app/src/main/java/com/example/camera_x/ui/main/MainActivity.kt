package com.example.camera_x.ui.main

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent

import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.camera_x.ui.ml_face.FaceDrawable
import com.example.camera_x.ui.ml_face.FaceViewModel
import com.example.camera_x.R
import com.example.camera_x.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private var viewBinding: ActivityMainBinding? = null
    private var photoTake: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraExecutor: ExecutorService? = null
    private var switchCamera : CameraSelector? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private lateinit var faceDetector: FaceDetector
    private val viewModel: MainViewModel by viewModels()
    private lateinit var photosAdapter: PhotosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding?.root)
        cameraExecutor = Executors.newSingleThreadExecutor()
        photosAdapter = PhotosAdapter(viewModel.photoUris)
        viewBinding?.recyclerPhotos?.adapter = photosAdapter
        viewBinding?.recyclerPhotos?.layoutManager = GridLayoutManager(this, 2)
        val bottomSheetBehavior = BottomSheetBehavior.from(viewBinding?.bottomSheet!!)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 1
        photosAdapter.onItemClick = { uri ->
            showFullPhoto(uri)
        }

        viewBinding?.openGallery?.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        viewBinding?.main?.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
            loadExistingPhotos()
        } else {
            requestPermissions()
        }

        viewBinding?.takePhoto?.setOnClickListener { takePhoto() }
        viewBinding?.takeVideo?.setOnClickListener { captureVideo() }
        viewBinding?.switchCamera?.setOnClickListener { switchCamera() }


    }

    @SuppressLint("RestrictedApi")
    @OptIn(ExperimentalGetImage::class)

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding?.viewFinder?.surfaceProvider)
                }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()

            faceDetector = FaceDetection.getClient(options)
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) {imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            viewBinding?.viewFinder?.overlay?.clear()

                            if (faces.isNotEmpty()) {
                                for (face in faces) {
                                    val transformedRect = transformBoundingBox(face.boundingBox, imageProxy)
                                    val vm = FaceViewModel(face).apply {
                                        boundingRect.set(transformedRect)
                                    }
                                    val drawable = FaceDrawable(vm)
                                    viewBinding?.viewFinder?.overlay?.add(drawable)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Face detection failed", e)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            photoTake = ImageCapture.Builder().build()
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = currentCameraSelector

            try {
                cameraProvider.unbindAll()
                cameraProvider
                    .bindToLifecycle(this, cameraSelector, preview, videoCapture, photoTake, imageAnalysis)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }
    private fun takePhoto() {
        val imageCapture = photoTake ?: return
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Camera_X-Image")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    output.savedUri?.let { uri ->
                        viewModel.photoUris.add(0, uri)
                        photosAdapter.notifyItemInserted(0)
                    }
                }
            }
        )
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding?.takeVideo?.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }
        val name = android.icu.text.SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Camera_X-Video")
            }
        }
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        android.Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding?.takeVideo?.apply {
                            setImageResource(R.drawable.ic_switch_capture_video)
                            isEnabled = true
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                             recording?.close()
                            recording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding?.takeVideo?.apply {
                            setImageResource(R.drawable.ic_started_video)
                            isEnabled = true
                        }
                    }

                }

            }

    }

    private fun switchCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        viewBinding = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    companion object {
        private const val TAG = "Camera_X"
        private const val FILENAME_FORMAT = "yyyy-MM--dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {

                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()

            } else {
                startCamera()
            }

        }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadExistingPhotos() {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            viewModel.photoUris.clear()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                viewModel.photoUris.add(contentUri)
            }
            photosAdapter.notifyDataSetChanged()
        }
    }
    private fun loadExistingVideo() {
        val projectionVideo = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_TAKEN)
        val sortOrderVideo = "${MediaStore.Video.Media.DATE_TAKEN} DESC"
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projectionVideo,
            null,
            null,
            sortOrderVideo
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            viewModel.photoUris.clear()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                viewModel.videoUris.add(contentUri)
            }
            photosAdapter.notifyDataSetChanged()
        }


    }
    private fun transformBoundingBox(box: Rect, imageProxy: ImageProxy): Rect {
        val previewWidth = viewBinding?.viewFinder?.width?.toFloat() ?: 0f
        val previewHeight = viewBinding?.viewFinder?.height?.toFloat() ?: 0f
        val imageWidth = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) {
            imageProxy.width.toFloat()
        } else {
            imageProxy.height.toFloat()
        }

        val imageHeight = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) {
            imageProxy.height.toFloat()
        } else {
            imageProxy.width.toFloat()
        }
        val scaleX = previewWidth / imageWidth
        val scaleY = previewHeight / imageHeight
        val scale = min(scaleX, scaleY)
        val offsetX = (previewWidth - imageWidth * scale) / 2
        val offsetY = (previewHeight - imageHeight * scale) / 2
        val scaledLeft = box.left * scale + offsetX
        val scaledTop = box.top * scale + offsetY
        val scaledRight = box.right * scale + offsetX
        val scaledBottom = box.bottom * scale + offsetY

        return if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            Rect(
                (previewWidth - scaledRight).toInt(),
                scaledTop.toInt(),
                (previewWidth - scaledLeft).toInt(),
                scaledBottom.toInt()
            )
        } else {
            Rect(scaledLeft.toInt(), scaledTop.toInt(), scaledRight.toInt(), scaledBottom.toInt())
        }
    }
    private fun showFullPhoto(uri: Uri) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.photo, null)
        val imageView = view.findViewById<ImageView>(R.id.fullPhotoView)

        Glide.with(this)
            .load(uri)
            .into(imageView)

        dialog.setContentView(view)
        dialog.show()
    }
}