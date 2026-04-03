package com.example.kinetiq

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.kinetiq.exercises.Severity
import com.example.kinetiq.models.*
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), PhysioSessionManager.SessionUpdateListener {

    private lateinit var sessionManager: PhysioSessionManager
    private lateinit var viewFinder: PreviewView
    private lateinit var skeletonOverlay: SkeletonOverlayView
    private lateinit var exerciseNameText: TextView
    private lateinit var repCounterText: TextView
    private lateinit var feedbackText: TextView
    private lateinit var romDisplay: TextView
    private lateinit var btnEndSession: Button
    
    // Pre-Session Views
    private lateinit var instructionLayout: ConstraintLayout
    private lateinit var videoInstruction: VideoView
    private lateinit var prePainSeekBar: SeekBar
    private lateinit var prePainValue: TextView
    private lateinit var btnProceed: Button
    
    // Post-Session Report Views
    private lateinit var reportLayout: ConstraintLayout
    private lateinit var reportReps: TextView
    private lateinit var reportTime: TextView
    private lateinit var reportPeakRom: TextView
    private lateinit var postPainSeekBar: SeekBar
    private lateinit var postPainValue: TextView
    private lateinit var reportNoteInput: TextInputEditText
    private lateinit var btnSaveReport: Button
    
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: PoseDetector

    private var selectedExercise: String = "pendulum"
    private var selectedSide: String = "right"
    private var currentIncorrectJoints: List<String> = emptyList()
    private var isSessionStarted = false
    
    private var sessionStartTime: Long = 0
    private var peakRomAcrossSession: Double = 0.0
    private var totalRepsAcrossSession: Int = 0
    private var initialPainScore: Int = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            feedbackText.text = "Camera permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        selectedExercise = intent.getStringExtra("EXERCISE_TYPE") ?: "pendulum"
        selectedSide = intent.getStringExtra("SELECTED_SIDE") ?: "right"

        bindViews()
        setupListeners()

        exerciseNameText.text = "${selectedExercise.replace("_", " ").uppercase()} (${selectedSide.uppercase()})"

        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = PhysioSessionManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)

        setupExerciseInstruction()

        if (!allPermissionsGranted()) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun bindViews() {
        viewFinder = findViewById(R.id.viewFinder)
        skeletonOverlay = findViewById(R.id.skeletonOverlay)
        exerciseNameText = findViewById(R.id.exerciseName)
        repCounterText = findViewById(R.id.repCounter)
        feedbackText = findViewById(R.id.feedbackText)
        romDisplay = findViewById(R.id.romDisplay)
        btnEndSession = findViewById(R.id.btnEndSession)
        
        instructionLayout = findViewById(R.id.instructionLayout)
        videoInstruction = findViewById(R.id.videoInstruction)
        prePainSeekBar = findViewById(R.id.prePainSeekBar)
        prePainValue = findViewById(R.id.prePainValue)
        btnProceed = findViewById(R.id.btnProceed)
        
        reportLayout = findViewById(R.id.reportLayout)
        reportReps = findViewById(R.id.reportReps)
        reportTime = findViewById(R.id.reportTime)
        reportPeakRom = findViewById(R.id.reportPeakRom)
        postPainSeekBar = findViewById(R.id.postPainSeekBar)
        postPainValue = findViewById(R.id.postPainValue)
        reportNoteInput = findViewById(R.id.reportNoteInput)
        btnSaveReport = findViewById(R.id.btnSaveReport)
    }

    private fun setupListeners() {
        prePainSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prePainValue.text = progress.toString()
                initialPainScore = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        postPainSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                postPainValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnProceed.setOnClickListener {
            startExerciseSession()
        }

        btnEndSession.setOnClickListener {
            endExerciseSession()
        }

        btnSaveReport.setOnClickListener {
            saveSessionReport()
        }
    }

    private fun setupExerciseInstruction() {
        val videoName = when (selectedExercise.lowercase()) {
            "pendulum" -> "pendulum"
            "external_rotation", "external" -> "external"
            "crossover" -> "crossover"
            "wall_climb", "wallclimb" -> "wallclimb"
            else -> "pendulum"
        }

        try {
            val videoUri = Uri.parse("android.resource://$packageName/raw/$videoName")
            videoInstruction.setVideoURI(videoUri)
            videoInstruction.setOnPreparedListener { mp ->
                mp.isLooping = true
                videoInstruction.start()
            }
            videoInstruction.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing video instruction", e)
        }
    }

    private fun startExerciseSession() {
        if (videoInstruction.isPlaying) {
            videoInstruction.stopPlayback()
        }
        instructionLayout.visibility = View.GONE
        btnEndSession.visibility = View.VISIBLE
        isSessionStarted = true
        sessionStartTime = System.currentTimeMillis()
        
        val initialInput = createDummyInput(selectedExercise, selectedSide)
        sessionManager.startSession(initialInput)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun endExerciseSession() {
        isSessionStarted = false
        btnEndSession.visibility = View.GONE
        showReport()
    }

    private fun saveSessionReport() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not logged in!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val now = Date()
        val timestampStr = sdf.format(now)

        val sessionResult = SessionResult(
            session_id = UUID.randomUUID().toString(),
            patient_id = uid,
            doctor_id = "", 
            timestamp_start = sdf.format(Date(sessionStartTime)),
            timestamp_end = timestampStr,
            exercise = selectedExercise,
            side = selectedSide,
            protocol_stage = 1,
            prescription = Prescription(selectedExercise, 3, 12, 10, selectedSide, null, null, null, 60, false, ""),
            pre_session = PreSession(initialPainScore, 0, 8, "normal", 0, false, false, ""),
            context = SessionContext(0.8f, true, 72, 50, emptyList()),
            results = PerformanceResults(
                sets_completed = 1,
                reps_per_set = listOf(totalRepsAcrossSession),
                valid_reps = totalRepsAcrossSession,
                compensated_reps = 0,
                invalid_reps = 0,
                invalid_rep_reasons = emptyList(),
                peak_rom_degrees = peakRomAcrossSession,
                mean_rep_duration_ms = 2000,
                session_duration_ms = System.currentTimeMillis() - sessionStartTime,
                calories_burned = 10
            ),
            rom_trend = RomTrend(peakRomAcrossSession, peakRomAcrossSession, 0.0, 0, 0),
            pain_log = emptyList(),
            form_flags = emptyList(),
            doctor_alerts = emptyList(),
            wearable_data = WearableSessionData(0, 0, 0),
            journal_entry = JournalEntry(reportNoteInput.text.toString(), emptyList(), false),
            adherence = AdherenceSummary(0, 1, 1, 1.0f),
            benchmarking = null
        )

        db.collection("sessions").add(sessionResult)
            .addOnSuccessListener {
                Toast.makeText(this, "Session Logged Successfully!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error saving session", e)
                Toast.makeText(this, "Failed to log session: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (!isSessionStarted) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                            
                            poseDetector.process(image)
                                .addOnSuccessListener { pose ->
                                    val keypoints = mutableMapOf<String, Keypoint>()
                                    val isRotated = rotationDegrees == 90 || rotationDegrees == 270
                                    val analysisWidth = if (isRotated) imageProxy.height else imageProxy.width
                                    val analysisHeight = if (isRotated) imageProxy.width else imageProxy.height

                                    pose.allPoseLandmarks.forEach { landmark ->
                                        val name = getLandmarkName(landmark.landmarkType)
                                        if (name != null) {
                                            var x = landmark.position.x / analysisWidth
                                            val y = landmark.position.y / analysisHeight
                                            x = 1.0f - x 

                                            keypoints[name] = Keypoint(
                                                x = x,
                                                y = y,
                                                z = landmark.position3D.z / analysisWidth,
                                                conf = landmark.inFrameLikelihood
                                            )
                                        }
                                    }
                                    
                                    if (keypoints.isNotEmpty()) {
                                        val sessionInput = createDummyInput(selectedExercise, selectedSide, keypoints)
                                        sessionManager.processFrame(sessionInput)
                                        
                                        runOnUiThread {
                                            skeletonOverlay.updateKeypoints(keypoints, currentIncorrectJoints)
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch(exc: Exception) {
                Log.e("MainActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun createDummyInput(exercise: String, side: String, keypoints: Map<String, Keypoint> = emptyMap()): SessionInput {
        return SessionInput(
            frame_id = 0,
            timestamp_ms = System.currentTimeMillis(),
            keypoints = keypoints,
            pre_session = PreSession(initialPainScore, 0, 8, "normal", 0, false, false, ""),
            patient_context = PatientContext("recovery", "", 4, side, 42, emptyList(), "en", false, 74, 48, 4000),
            prescription = Prescription(exercise, 3, 12, 10, side, null, null, null, 60, false, ""),
            session_history_summary = SessionHistorySummary(3, 5, 80f, 0, 0, 0.8f)
        )
    }

    private fun getLandmarkName(type: Int): String? {
        return when (type) {
            PoseLandmark.NOSE -> "nose"
            PoseLandmark.LEFT_SHOULDER -> "left_shoulder"
            PoseLandmark.RIGHT_SHOULDER -> "right_shoulder"
            PoseLandmark.LEFT_ELBOW -> "left_elbow"
            PoseLandmark.RIGHT_ELBOW -> "right_elbow"
            PoseLandmark.LEFT_WRIST -> "left_wrist"
            PoseLandmark.RIGHT_WRIST -> "right_wrist"
            PoseLandmark.LEFT_HIP -> "left_hip"
            PoseLandmark.RIGHT_HIP -> "right_hip"
            PoseLandmark.LEFT_KNEE -> "left_knee"
            PoseLandmark.RIGHT_KNEE -> "right_knee"
            PoseLandmark.LEFT_ANKLE -> "left_ankle"
            PoseLandmark.RIGHT_ANKLE -> "right_ankle"
            else -> null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector.close()
    }

    // --- SessionUpdateListener Implementation ---

    override fun onVoiceFeedback(message: String, severity: Severity) {
        runOnUiThread {
            feedbackText.text = message
        }
    }

    override fun onRepCountUpdated(count: Int) {
        runOnUiThread {
            repCounterText.text = "Reps: $count"
            totalRepsAcrossSession = count
        }
    }

    override fun onRomUpdated(rom: Double) {
        runOnUiThread {
            if (selectedExercise.equals("crossover", ignoreCase = true)) {
                romDisplay.text = "Optimality: ${rom.toInt()}%"
            } else {
                romDisplay.text = "ROM: ${rom.toInt()}°"
            }
            if (rom > peakRomAcrossSession) {
                peakRomAcrossSession = rom
            }
        }
    }

    override fun onSessionEnded(reason: String, priority: String) {
        runOnUiThread {
            endExerciseSession()
        }
    }

    private fun showReport() {
        val durationSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
        
        reportReps.text = "Reps Completed: $totalRepsAcrossSession"
        reportTime.text = "Time Taken: ${durationSeconds}s"
        reportPeakRom.text = if (selectedExercise.equals("crossover", ignoreCase = true)) "Peak Optimality: ${peakRomAcrossSession.toInt()}%" else "Peak ROM: ${peakRomAcrossSession.toInt()}°"
        
        reportLayout.visibility = View.VISIBLE
    }

    override fun onSecurityAlert(message: String) {
        runOnUiThread { feedbackText.text = "ALERT: $message" }
    }

    override fun onPositioningTip(tip: String) {
        runOnUiThread { feedbackText.text = tip }
    }

    override fun onPrescriptionAdjusted(newPrescription: Prescription) {
    }

    override fun onHoldCountdown(seconds: Int?) {
        runOnUiThread {
            if (seconds != null) {
                feedbackText.text = "Hold: $seconds"
            }
        }
    }

    override fun onIncorrectJointsUpdated(joints: List<String>) {
        runOnUiThread {
            currentIncorrectJoints = joints
        }
    }
}
