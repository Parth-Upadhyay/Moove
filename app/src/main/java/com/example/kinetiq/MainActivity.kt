package com.example.kinetiq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.kinetiq.exercises.Severity
import com.example.kinetiq.models.*
import com.example.kinetiq.ui.components.ExerciseDemoPlayer
import com.example.kinetiq.ui.theme.MooveTheme
import com.example.kinetiq.utils.showLogoSnackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

class MainActivity : AppCompatActivity(), PhysioSessionManager.SessionUpdateListener, TextToSpeech.OnInitListener {

    private lateinit var sessionManager: PhysioSessionManager
    private lateinit var viewFinder: PreviewView
    private lateinit var skeletonOverlay: SkeletonOverlayView
    private lateinit var exerciseNameText: TextView
    private lateinit var repCounterText: TextView
    private lateinit var setCounterText: TextView
    private lateinit var timerDisplayText: TextView
    private lateinit var feedbackText: TextView
    private lateinit var romDisplay: TextView
    private lateinit var btnEndSession: Button
    private lateinit var btnBack: ImageButton
    
    // Pre-Session Views
    private lateinit var instructionLayout: ConstraintLayout
    private lateinit var videoInstruction: VideoView
    private lateinit var composeDemoView: ComposeView
    private lateinit var prePainSeekBar: SeekBar
    private lateinit var prePainValue: TextView
    private lateinit var btnProceed: Button
    
    // Post-Session Report Views
    private lateinit var reportLayout: View 
    private lateinit var reportReps: TextView
    private lateinit var reportTime: TextView
    private lateinit var reportPeakMotion: TextView
    private lateinit var postPainSeekBar: SeekBar
    private lateinit var postPainValue: TextView
    private lateinit var reportNoteInput: TextInputEditText
    private lateinit var btnSaveReport: Button
    
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: PoseDetector
    private var tts: TextToSpeech? = null

    private var selectedExercise: String = "pendulum"
    private var selectedSide: String = "right"
    private var targetSets: Int = 3
    private var targetReps: Int = 10
    
    private var currentIncorrectJoints: List<String> = emptyList()
    private var isSessionStarted = false
    
    // Voice settings loaded from main settings
    private var voiceFeedbackEnabled: Boolean = false
    private var voiceCountEnabled: Boolean = true
    
    private var sessionStartTime: Long = 0
    private var peakMotionAcrossSession: Double = 0.0
    private var totalRepsAcrossSession: Int = 0
    private var initialPainScore: Int = 0
    private var currentSetsCompleted: Int = 0

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
        targetSets = intent.getIntExtra("TARGET_SETS", 3).coerceAtLeast(1)
        targetReps = intent.getIntExtra("TARGET_REPS", 10).coerceAtLeast(1)
        
        // Load voice settings passed from HomeActivity
        voiceFeedbackEnabled = intent.getBooleanExtra("VOICE_FEEDBACK_ENABLED", false)
        voiceCountEnabled = intent.getBooleanExtra("VOICE_COUNT_ENABLED", true)

        bindViews()
        setupListeners()

        exerciseNameText.text = "${selectedExercise.replace("_", " ").uppercase()} (${selectedSide.uppercase()})"

        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            v.setPadding(
                systemBars.left, 
                systemBars.top, 
                systemBars.right, 
                if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            )
            insets
        }

        sessionManager = PhysioSessionManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this, this)

        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)

        setupExerciseInstruction()

        if (!allPermissionsGranted()) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun bindViews() {
        viewFinder = findViewById(R.id.viewFinder)
        skeletonOverlay = findViewById(R.id.skeletonOverlay)
        exerciseNameText = findViewById(R.id.exerciseName)
        setCounterText = findViewById(R.id.setCounter)
        repCounterText = findViewById(R.id.repCounter)
        timerDisplayText = findViewById(R.id.timerDisplay)
        feedbackText = findViewById(R.id.feedbackText)
        romDisplay = findViewById(R.id.romDisplay)
        btnEndSession = findViewById(R.id.btnEndSession)
        btnBack = findViewById(R.id.btnBack)
        
        instructionLayout = findViewById(R.id.instructionLayout)
        videoInstruction = findViewById(R.id.videoInstruction)
        composeDemoView = findViewById(R.id.composeDemoView)
        prePainSeekBar = findViewById(R.id.prePainSeekBar)
        prePainValue = findViewById(R.id.prePainValue)
        btnProceed = findViewById(R.id.btnProceed)
        
        reportLayout = findViewById(R.id.reportLayout)
        reportReps = findViewById(R.id.reportReps)
        reportTime = findViewById(R.id.reportTime)
        reportPeakMotion = findViewById(R.id.reportPeakRom) 
        postPainSeekBar = findViewById(R.id.postPainSeekBar)
        postPainValue = findViewById(R.id.postPainValue)
        reportNoteInput = findViewById(R.id.reportNoteInput)
        btnSaveReport = findViewById(R.id.btnSaveReport)

        romDisplay.visibility = View.VISIBLE
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

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
        val exerciseId = selectedExercise.lowercase()
        
        if (exerciseId == "forward_arm_raise" || exerciseId == "lateral_arm_raise" ||
            exerciseId == "external_rotation" || exerciseId == "crossover" || exerciseId == "pendulum") {
            videoInstruction.visibility = View.GONE
            composeDemoView.visibility = View.VISIBLE
            
            val modelName = if (exerciseId == "pendulum") "pendulum_model" else exerciseId
            val modelPath = "models/$modelName.glb"
            composeDemoView.setContent {
                MooveTheme {
                    ExerciseDemoPlayer(modelPath = modelPath)
                }
            }
        } else {
            composeDemoView.visibility = View.GONE
            videoInstruction.visibility = View.VISIBLE
            
            val videoName = when (exerciseId) {
                "pendulum" -> "pendulum"
                "external_rotation", "external" -> "external"
                "crossover" -> "crossover"
                "wall_climb", "wallclimb" -> "wallclimb"
                "hitchhiker" -> "hitchhiker"
                else -> "pendulum"
            }

            try {
                val videoUri = Uri.parse("android.resource://$packageName/raw/$videoName")
                videoInstruction.setVideoURI(videoUri)
                videoInstruction.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    videoInstruction.start()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error playing video instruction", e)
            }
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
        totalRepsAcrossSession = 0
        currentSetsCompleted = 0
        
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
            findViewById<View>(android.R.id.content).showLogoSnackbar("Not logged in!")
            finish()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val now = Date()
        val timestampStr = sdf.format(now)

        val postPainEntry = PainEntry(
            rep = totalRepsAcrossSession,
            set = currentSetsCompleted,
            level = postPainSeekBar.progress.toString(),
            timestamp_ms = System.currentTimeMillis()
        )

        val sessionResult = SessionResult(
            session_id = UUID.randomUUID().toString(),
            patient_id = uid,
            doctor_id = "", 
            timestamp_start = sdf.format(Date(sessionStartTime)),
            timestamp_end = timestampStr,
            exercise = selectedExercise,
            side = selectedSide,
            protocol_stage = 1,
            prescription = Prescription(selectedExercise, targetSets, targetReps, 0, selectedSide, null, null, null, 20, false, ""),
            pre_session = PreSession(initialPainScore, 0, 8, "normal", 0, false, false, ""),
            context = SessionContext(0.8f, true, 72, 50, emptyList()),
            results = PerformanceResults(
                sets_completed = currentSetsCompleted,
                reps_per_set = listOf(totalRepsAcrossSession),
                valid_reps = totalRepsAcrossSession,
                compensated_reps = 0,
                invalid_reps = 0,
                invalid_rep_reasons = emptyList(),
                peak_rom_degrees = peakMotionAcrossSession, 
                mean_rep_duration_ms = 2000,
                session_duration_ms = System.currentTimeMillis() - sessionStartTime,
                calories_burned = 10
            ),
            rom_trend = RomTrend(peakMotionAcrossSession, peakMotionAcrossSession, 0.0, 0, 0),
            pain_log = listOf(postPainEntry),
            form_flags = emptyList(),
            doctor_alerts = emptyList(),
            wearable_data = WearableSessionData(0, 0, 0),
            journal_entry = JournalEntry(reportNoteInput.text.toString(), emptyList(), false),
            adherence = AdherenceSummary(0, 1, 1, 1.0f),
            benchmarking = null
        )

        val batch = db.batch()
        val sessionRef = db.collection("sessions").document()
        batch.set(sessionRef, sessionResult)
        
        val userRef = db.collection("users").document(uid)
        batch.update(userRef, "totalSessions", FieldValue.increment(1))
        batch.update(userRef, "streak", FieldValue.increment(1))
        batch.update(userRef, "lastSessionDate", now)

        batch.commit()
            .addOnSuccessListener {
                findViewById<View>(android.R.id.content).showLogoSnackbar("Session Logged Successfully!")
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error saving session", e)
                findViewById<View>(android.R.id.content).showLogoSnackbar("Failed to log session: ${e.message}")
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
            patient_context = PatientContext("recovery", "", 1, side, 42, emptyList(), "en", false, 74, 48, 4000),
            prescription = Prescription(exercise, targetSets, targetReps, 0, side, null, null, null, 20, false, ""),
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
        tts?.stop()
        tts?.shutdown()
    }

    // --- SessionUpdateListener Implementation ---

    override fun onVoiceFeedback(message: String, severity: Severity) {
        runOnUiThread {
            feedbackText.text = message
            
            val isCount = message.all { it.isDigit() } || message.contains("Set") || message.contains("ready")
            
            if (isCount && voiceCountEnabled) {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "count")
            } else if (!isCount && voiceFeedbackEnabled) {
                // For instructions/feedback: read fully and add 2s pause
                tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "feedback")
                tts?.playSilentUtterance(2000, TextToSpeech.QUEUE_ADD, "pause")
            }
        }
    }

    override fun onRepCountUpdated(count: Int, target: Int) {
        runOnUiThread {
            repCounterText.text = "$count / $target"
        }
    }

    override fun onRepCompleted(delta: Int) {
        runOnUiThread {
            totalRepsAcrossSession += delta
        }
    }
    
    override fun onSetCountUpdated(currentSet: Int, totalSets: Int) {
        runOnUiThread {
            setCounterText.text = "$currentSet / $totalSets"
            currentSetsCompleted = currentSet
        }
    }
    
    override fun onTimerUpdated(secondsRemaining: Int?) {
        runOnUiThread {
            if (secondsRemaining != null) {
                timerDisplayText.visibility = View.VISIBLE
                timerDisplayText.text = "REST: ${secondsRemaining}s"
            } else {
                timerDisplayText.visibility = View.GONE
            }
        }
    }

    override fun onPeakMotionUpdated(peakMotion: Double) {
        runOnUiThread {
            if (peakMotion > peakMotionAcrossSession) {
                peakMotionAcrossSession = peakMotion
            }
            romDisplay.text = "${peakMotion.toInt()}°"
        }
    }

    override fun onSessionEnded(reason: String, priority: String) {
        runOnUiThread {
            feedbackText.text = reason
            if (voiceFeedbackEnabled) {
                tts?.speak(reason, TextToSpeech.QUEUE_FLUSH, null, "end")
            }
            endExerciseSession()
        }
    }

    private fun showReport() {
        val durationSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
        
        reportReps.text = totalRepsAcrossSession.toString()
        reportTime.text = "${durationSeconds}s"
        reportPeakMotion.text = "${peakMotionAcrossSession.toInt()}°"
        
        reportLayout.visibility = View.VISIBLE
    }

    override fun onSecurityAlert(message: String) {
        runOnUiThread { 
            feedbackText.text = "ALERT: $message"
            if (voiceFeedbackEnabled) {
                tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "alert")
                tts?.playSilentUtterance(2000, TextToSpeech.QUEUE_ADD, "pause")
            }
        }
    }

    override fun onPositioningTip(tip: String) {
        runOnUiThread { 
            feedbackText.text = tip
            if (voiceFeedbackEnabled) {
                tts?.speak(tip, TextToSpeech.QUEUE_ADD, null, "tip")
                tts?.playSilentUtterance(2000, TextToSpeech.QUEUE_ADD, "pause")
            }
        }
    }

    override fun onPrescriptionAdjusted(newPrescription: Prescription) {
    }

    override fun onHoldCountdown(seconds: Int?) {
        runOnUiThread {
            if (seconds != null) {
                feedbackText.text = "Hold: $seconds"
                if (voiceCountEnabled) {
                    tts?.speak(seconds.toString(), TextToSpeech.QUEUE_FLUSH, null, "hold")
                }
            }
        }
    }

    override fun onIncorrectJointsUpdated(joints: List<String>) {
        runOnUiThread {
            currentIncorrectJoints = joints
        }
    }
}
