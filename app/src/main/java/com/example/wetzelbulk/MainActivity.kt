package com.example.wetzelbulk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// --- Brand Color Scheme ---
private val WetzelBrandColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF3730A3),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1A2E),
    outlineVariant = Color(0xFFEEF0F3),
    onPrimary = Color.White
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerState.restore(this)
        setContent {
            MaterialTheme(colorScheme = WetzelBrandColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val workoutViewModel: WorkoutViewModel = viewModel(
                        factory = WorkoutViewModelFactory(context.applicationContext)
                    )
                    WetzelBulkApp(viewModel = workoutViewModel)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PORTABLE BACKGROUND TIMER SERVICE ENGINE
// ══════════════════════════════════════════════════════════════════════════════

object TimerState {
    var timerSeconds by mutableStateOf(60)
    var activeRestTargetSeconds by mutableStateOf(60)
    var activeBlockTitle by mutableStateOf("")
    var isTimerActive by mutableStateOf(false)

    fun persist(context: Context) {
        val prefs = context.getSharedPreferences("WetzelTimerPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("seconds", timerSeconds)
            putInt("target_seconds", activeRestTargetSeconds)
            putString("block_title", activeBlockTitle)
            putBoolean("active", isTimerActive)
            apply()
        }
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences("WetzelTimerPrefs", Context.MODE_PRIVATE)
        timerSeconds = prefs.getInt("seconds", 60)
        activeRestTargetSeconds = prefs.getInt("target_seconds", 60)
        activeBlockTitle = prefs.getString("block_title", "") ?: ""
        isTimerActive = prefs.getBoolean("active", false)
    }
}

class RestTimerService : Service() {
    private var serviceJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerState.restore(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "timer_channel",
                "Rest Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val duration = intent.getIntExtra("DURATION_SECONDS", 60)
                val blockTitle = intent.getStringExtra("BLOCK_TITLE") ?: ""
                startForeground(1, createNotification())
                startCountdown(duration, blockTitle)
            }
            "STOP" -> stopCountdown()
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "timer_channel")
            .setContentTitle("Rest Timer Active")
            .setContentText("Keep lifting!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun startCountdown(duration: Int, blockTitle: String) {
        serviceJob?.cancel()
        TimerState.timerSeconds = duration
        TimerState.activeRestTargetSeconds = duration
        TimerState.activeBlockTitle = blockTitle
        TimerState.isTimerActive = true
        TimerState.persist(this)

        serviceJob = CoroutineScope(Dispatchers.Default).launch {
            while (TimerState.isTimerActive) {
                delay(1000)
                TimerState.timerSeconds--
                TimerState.persist(this@RestTimerService)

                if (TimerState.timerSeconds <= -120) {
                    stopCountdown()
                    break
                }

                if (TimerState.timerSeconds == 0) {
                    triggerPhoneVibration()
                } else if (TimerState.timerSeconds < 0 && TimerState.timerSeconds % 10 == 0) {
                    triggerPhoneVibration()
                }
            }
        }
    }

    private fun stopCountdown() {
        serviceJob?.cancel()
        TimerState.isTimerActive = false
        TimerState.activeBlockTitle = ""
        TimerState.persist(this)
        stopSelf()
    }

    private fun triggerPhoneVibration() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopCountdown()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopCountdown()
        super.onDestroy()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ROOM DATABASE SCHEMA LAYER
// ══════════════════════════════════════════════════════════════════════════════

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val dateString: String,
    val dayName: String,
    val startTime: Long,
    val endTime: Long,
    val bodyWeight: Double = 0.0
)

@Entity(tableName = "set_logs")
data class SetLog(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val sessionId: Long,
    val exerciseBlockTitle: String,
    val exerciseLabel: String,
    val setNum: Int,
    val weightAchieved: Double,
    val repsAchieved: Int
)

@Entity(tableName = "exercise_notes")
data class ExerciseNotes(
    @PrimaryKey val blockTitle: String,
    val notesText: String
)

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Insert
    suspend fun insertSetLogs(logs: List<SetLog>)

    @Query("SELECT * FROM workout_sessions ORDER BY endTime DESC LIMIT 1")
    suspend fun getLastSession(): WorkoutSession?

    @Query("SELECT bodyWeight FROM workout_sessions WHERE bodyWeight > 0.0 ORDER BY endTime DESC LIMIT 1")
    suspend fun getLastRecordedBodyWeight(): Double?

    @Query("SELECT * FROM exercise_notes WHERE blockTitle = :title")
    suspend fun getNotesForBlock(title: String): ExerciseNotes?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNotes(notes: ExerciseNotes)

    @Query("SELECT * FROM set_logs WHERE sessionId = (SELECT s.sessionId FROM workout_sessions s JOIN set_logs l ON s.sessionId = l.sessionId WHERE s.dayName = :dayName ORDER BY s.endTime DESC LIMIT 1)")
    suspend fun getLastSessionBaselines(dayName: String): List<SetLog>

    @Query("SELECT * FROM workout_sessions ORDER BY endTime DESC")
    suspend fun getAllSessionsDesc(): List<WorkoutSession>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY logId ASC")
    suspend fun getSetLogsForSession(sessionId: Long): List<SetLog>

    @Query("SELECT * FROM set_logs ORDER BY logId DESC")
    suspend fun getAllRecentSetLogs(): List<SetLog>

    @Query("SELECT * FROM exercise_notes")
    suspend fun getAllNotes(): List<ExerciseNotes>
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN bodyWeight REAL NOT NULL DEFAULT 0.0")
    }
}

@Database(entities = [WorkoutSession::class, SetLog::class, ExerciseNotes::class], version = 2, exportSchema = false)
abstract class WetzelBulkDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var INSTANCE: WetzelBulkDatabase? = null
        fun getDatabase(context: Context): WetzelBulkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WetzelBulkDatabase::class.java,
                    "wetzel_bulk_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// LIVE STATE / VIEWMODEL BINDINGS ENGINE
// ══════════════════════════════════════════════════════════════════════════════

class LoggableSetState(
    val label: String,
    val setNum: Int,
    initialWeight: Double,
    initialReps: Int,
    val increment: Double,
    var baselineWeight: Double = -1.0,
    var baselineReps: Int = -1,
    val minReps: Int = 8,
    val maxReps: Int = 12,
    val restSeconds: Int = 120
) {
    var weight by mutableStateOf(initialWeight)
    var reps by mutableStateOf(initialReps)
    var isCompleted by mutableStateOf(false)
}

class WorkoutBlockState(
    val title: String,
    val sets: List<LoggableSetState>,
    initialNotes: String = "",
    val defaultRestSeconds: Int = 120
) {
    var notes by mutableStateOf(initialNotes)
}

fun getCanonicalExercise(label: String): String {
    val l = label.lowercase().trim()
    return when {
        // Lat Pulldown family (Vertical Pull compound)
        // Must match lat pull / lat pulldown, but NEVER isolated pulldown, straight-arm, or lateral raise
        l in listOf("lat pull", "lat pulldown", "lat pull-down", "lat pull down", "lat pulldowns") ||
            (l.contains("lat") && (l.contains("pull") || l.contains("pulldown")) && !l.contains("raise")) -> "LAT_PULLDOWN"

        // Straight-Arm Pulldown family (Lat isolation)
        // Matches "pulldown", "straight-arm cable pulldown", "straight-arm pulldown", etc.
        // MUST NEVER match Lat Pulldown (excludes any label containing "lat"), and never tricep/rope
        l in listOf("pulldown", "straight-arm cable pulldown", "straight-arm pulldown", "straight arm pulldown", "straight arm cable pulldown") ||
            ((l.contains("straight-arm") || l.contains("straight arm") || l == "pulldown" || l.contains("cable pulldown")) && !l.contains("lat") && !l.contains("tricep") && !l.contains("rope")) -> "STRAIGHT_ARM_PULLDOWN"

        // Lateral Raise family
        l in listOf("lat. raise", "lateral raise", "db lateral raise", "lat raise", "dumbbell lateral raise") ||
            (l.contains("lateral raise") || l.contains("lat. raise") || (l.contains("lat") && l.contains("raise"))) -> "LATERAL_RAISE"

        // Incline Press family
        l in listOf("inc. press", "incline press", "inc. chest", "inc. db", "incline db", "incline db press", "incline press (4 sets)", "incline chest press", "inc press", "inc db") ||
            ((l.contains("inc") || l.contains("incline")) && (l.contains("press") || l.contains("chest") || l.contains("db")) && !l.contains("curl")) -> "INCLINE_PRESS"

        // Flat Press family
        l in listOf("flat press", "flat chest", "chest press", "flat chest press machine", "flat chest press", "flat press (3 sets)", "flat chest press (3 sets)") ||
            (l.contains("flat") && (l.contains("press") || l.contains("chest"))) ||
            (l.contains("chest press") && !l.contains("inc")) -> "FLAT_PRESS"

        // DB Fly family
        l in listOf("db fly", "fly", "cable fly", "chest fly", "dumbbell fly") ||
            (l.contains("fly") && !l.contains("shrug")) -> "DB_FLY"

        // Rope Pushdown family
        l in listOf("rope push", "rope pushdown", "tricep pushdown", "triceps pushdown", "cable rope pushdown", "rope tricep pushdown") ||
            (l.contains("rope") && (l.contains("push") || l.contains("pushdown"))) ||
            l.contains("tricep pushdown") || l.contains("triceps pushdown") -> "ROPE_PUSHDOWN"

        // Skull Crusher family
        l in listOf("skull crusher", "skull crush", "db skull crusher", "skullcrusher", "db skull crush") ||
            l.contains("skull") -> "SKULL_CRUSHER"

        // Calf Raise family (MUST BE BEFORE Leg Press so "leg press calf raise" maps to CALF_RAISE)
        l in listOf("calf raise", "leg press calf raise") ||
            l.contains("calf") -> "CALF_RAISE"

        // Leg Press family (Heavy compound, excludes calf raise)
        l in listOf("leg press") ||
            (l.contains("leg press") && !l.contains("calf")) -> "LEG_PRESS"

        // RDL family
        l in listOf("rdl", "db rdl", "romanian dead lift", "romainian dead lift", "romanian deadlift", "db romanian deadlift", "dumbbell rdl") ||
            l.contains("rdl") || l.contains("romanian") || l.contains("romainian") -> "RDL"

        // Back Extension family
        l in listOf("back ext.", "back extension") ||
            l.contains("back ext") || l.contains("back extension") -> "BACK_EXTENSION"

        // Crunch family
        l in listOf("crunch", "mac. crunch", "machine crunch") ||
            l.contains("crunch") -> "CRUNCH"

        // Cable Row family
        l in listOf("cable row", "seated cable row", "seated cable row, neutral v-grip", "seated cable row - v-grip") ||
            l.contains("cable row") || l.contains("seated row") || (l.contains("seated") && l.contains("row")) -> "CABLE_ROW"

        // Face Pull family
        l in listOf("face pull", "cable face pull", "rope face pull") ||
            l.contains("face pull") -> "FACE_PULL"

        // Incline DB Curl family
        l in listOf("inc. curl", "incline curl", "incline db curl") ||
            ((l.contains("inc") || l.contains("incline")) && l.contains("curl")) -> "INCLINE_CURL"

        // Hammer Curl family
        l in listOf("hammer curl", "hammer", "db hammer curl") ||
            l.contains("hammer") -> "HAMMER_CURL"

        // Treadmill
        l.contains("treadmill") -> "TREADMILL"

        else -> l
    }
}

fun isLabelMatch(currentLabel: String, historyLabel: String): Boolean {
    if (currentLabel.equals(historyLabel, ignoreCase = true)) return true
    val cur = currentLabel.lowercase().trim()
    val hist = historyLabel.lowercase().trim()
    if (cur == hist) return true

    val curCanon = getCanonicalExercise(cur)
    val histCanon = getCanonicalExercise(hist)
    return curCanon == histCanon
}

fun getNotesAliases(blockTitle: String): List<String> {
    return when {
        blockTitle.startsWith("Incline Press", ignoreCase = true) || blockTitle.startsWith("Incline DB", ignoreCase = true) ->
            listOf("Incline DB Press (4 Sets)", "Incline Press (4 Sets)", "Incline Chest / Lateral Raise Superset", "Incline Press", "Incline DB Press")
        blockTitle.startsWith("Flat Press", ignoreCase = true) || blockTitle.startsWith("Flat Chest", ignoreCase = true) ->
            listOf("Flat Press (3 Sets)", "Flat Chest Press Machine (3 Sets)", "Flat Chest / Overhead Press Superset", "Flat Press", "Flat Chest Press")
        blockTitle.contains("Romanian", ignoreCase = true) || blockTitle.contains("Romainian", ignoreCase = true) || blockTitle.startsWith("RDL", ignoreCase = true) ->
            listOf("Romanian Dead Lift (3 Sets)", "RDL (3 Sets)", "DB Romanian Deadlift / Calf Raise Superset", "Romainian Dead Lift (3 Sets)", "RDL")
        blockTitle.startsWith("Leg Press", ignoreCase = true) ->
            listOf("Leg Press (4 Sets)", "Leg Press (Heavy Straight Sets)", "Leg Press")
        blockTitle.contains("Calf Raise", ignoreCase = true) ->
            listOf("Leg Press Calf Raise (3 Sets)", "DB Romanian Deadlift / Calf Raise Superset", "Leg Press Calf Raise")
        blockTitle.contains("DB Fly", ignoreCase = true) ->
            listOf("Superset: DB Fly / DB Lateral Raise", "Incline Chest / Lateral Raise Superset", "Tricep Pushdown / Cable Fly Superset")
        blockTitle.contains("Rope Pushdown", ignoreCase = true) ->
            listOf("Superset: Rope Pushdown / Skull Crusher", "Tricep Pushdown / Cable Fly Superset")
        blockTitle.contains("Back Extension", ignoreCase = true) || blockTitle.contains("Crunch", ignoreCase = true) ->
            listOf("Superset: Crunch / Back Extension", "Machine Crunch / Back Extension Superset", "Superset: Back Extension / Crunch")
        blockTitle.startsWith("Lat Pulldown", ignoreCase = true) ->
            listOf("Lat Pulldown (4 Sets)", "Lat Pulldown / Face Pull Superset")
        blockTitle.contains("Cable Row", ignoreCase = true) ->
            listOf("Seated Cable Row - V-Grip (4 Sets)", "Cable Row / DB Shrug Superset")
        blockTitle.contains("Straight-Arm", ignoreCase = true) ->
            listOf("Superset: Straight-Arm Pulldown / Face Pull", "Lat Pulldown / Face Pull Superset")
        blockTitle.contains("Incline DB Curl", ignoreCase = true) ->
            listOf("Superset: Incline DB Curl / Hammer Curl", "Incline DB Curl / Hammer Curl Superset")
        else -> emptyList()
    }
}

fun getExerciseRepRange(label: String): Pair<Int, Int> {
    return when (getCanonicalExercise(label)) {
        "INCLINE_PRESS" -> Pair(8, 12)
        "FLAT_PRESS" -> Pair(8, 12)
        "DB_FLY" -> Pair(10, 15)
        "LATERAL_RAISE" -> Pair(12, 20)
        "ROPE_PUSHDOWN" -> Pair(8, 15)
        "SKULL_CRUSHER" -> Pair(10, 15)
        "LEG_PRESS" -> Pair(8, 12)
        "RDL" -> Pair(8, 12)
        "CALF_RAISE" -> Pair(12, 20)
        "CRUNCH" -> Pair(10, 15)
        "BACK_EXTENSION" -> Pair(10, 15)
        "LAT_PULLDOWN" -> Pair(8, 12)
        "CABLE_ROW" -> Pair(8, 12)
        "STRAIGHT_ARM_PULLDOWN" -> Pair(10, 15)
        "FACE_PULL" -> Pair(12, 20)
        "INCLINE_CURL" -> Pair(8, 12)
        "HAMMER_CURL" -> Pair(8, 12)
        else -> Pair(0, 0)
    }
}

class WorkoutViewModel(context: Context) : ViewModel() {
    private val db = WetzelBulkDatabase.getDatabase(context)
    private val dao = db.workoutDao()
    private val appContext = context.applicationContext

    val cycle = listOf("PUSH DAY", "LEGS DAY", "PULL DAY", "CARDIO DAY")
    var currentDayIndex by mutableStateOf(0)
    val currentDayName get() = cycle[currentDayIndex]

    var bodyWeight by mutableStateOf(185.0)

    var activeBlocks = mutableStateListOf<WorkoutBlockState>()
    var sessionStartTime: Long = 0
    var focusedSetInstance by mutableStateOf<LoggableSetState?>(null)

    // Live Workout In-Memory Preservation (Immunity against history navigation corruption)
    private var liveBlocksBackup: List<WorkoutBlockState>? = null
    private var liveDayIndexBackup: Int? = null
    private var liveBodyWeightBackup: Double? = null
    private var liveSessionStartTimeBackup: Long? = null

    // STAGE 3 STATE REGISTRY: Tape Deck Time Travel Parameters
    var isHistoryMode by mutableStateOf(false)
    var historicalSessions = mutableStateListOf<WorkoutSession>()
    var activeHistoryPointer by mutableStateOf(-1)
    val currentHistorySession get() = historicalSessions.getOrNull(activeHistoryPointer)

    // Git Cloud Sync State
    var gitSyncStatus by mutableStateOf(GitSyncManager.getConfig(context).lastSyncStatus)
    var isGitSyncing by mutableStateOf(false)
    var showGitSettingsDialog by mutableStateOf(false)

    fun triggerGitSync(customNote: String? = null) {
        if (!GitSyncManager.isConfigured(appContext)) return
        viewModelScopeLaunch {
            isGitSyncing = true
            gitSyncStatus = "Syncing to GitHub..."
            val result = withContext(Dispatchers.IO) {
                GitSyncManager.syncDatabaseToGit(appContext, db, customNote)
            }
            isGitSyncing = false
            result.onSuccess { msg ->
                gitSyncStatus = "✓ $msg"
            }.onFailure { err ->
                gitSyncStatus = "⚠ Sync failed: ${err.message}"
            }
        }
    }

    init {
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScopeLaunch {
            val prefs = appContext.getSharedPreferences("WetzelBulkPrefs", Context.MODE_PRIVATE)
            val isSessionActive = prefs.getBoolean("is_session_active", false)

            if (isSessionActive) {
                currentDayIndex = prefs.getInt("active_day_index", 0)
                sessionStartTime = prefs.getLong("active_session_start_time", 0L)
                bodyWeight = prefs.getFloat("active_body_weight", 185.0f).toDouble()
                loadActiveDayRoutine(restoreFromPrefs = true)
            } else {
                val lastSession = withContext(Dispatchers.IO) { dao.getLastSession() }
                if (lastSession != null) {
                    val lastIndex = cycle.indexOf(lastSession.dayName)
                    if (lastIndex != -1) {
                        currentDayIndex = (lastIndex + 1) % 4
                    }
                } else {
                    currentDayIndex = 0
                }
                loadActiveDayRoutine(restoreFromPrefs = false)
            }
        }
    }

    fun loadActiveDayRoutine(restoreFromPrefs: Boolean = false) {
        isHistoryMode = false
        activeHistoryPointer = -1

        viewModelScopeLaunch {
            val dayName = currentDayName
            val baseTemplate = createRoutineTemplate(dayName)
            val prefs = appContext.getSharedPreferences("WetzelBulkPrefs", Context.MODE_PRIVATE)

            withContext(Dispatchers.IO) {
                val lastWeight = dao.getLastRecordedBodyWeight() ?: 185.0
                withContext(Dispatchers.Main) {
                    if (restoreFromPrefs) {
                        bodyWeight = prefs.getFloat("active_body_weight", lastWeight.toFloat()).toDouble()
                    } else {
                        bodyWeight = lastWeight
                    }
                }

                baseTemplate.forEach { block ->
                    var savedNotes = dao.getNotesForBlock(block.title)
                    if (savedNotes == null || savedNotes.notesText.isBlank()) {
                        for (alias in getNotesAliases(block.title)) {
                            val aliasNotes = dao.getNotesForBlock(alias)
                            if (aliasNotes != null && aliasNotes.notesText.isNotBlank()) {
                                savedNotes = aliasNotes
                                dao.saveNotes(ExerciseNotes(block.title, aliasNotes.notesText))
                                break
                            }
                        }
                    }
                    if (savedNotes != null) {
                        block.notes = savedNotes.notesText
                    }
                }

                val allRecentLogs = dao.getAllRecentSetLogs()
                baseTemplate.forEachIndexed { bIdx, block ->
                    block.sets.forEachIndexed { sIdx, stateSet ->
                        val matchingLog = allRecentLogs.firstOrNull { log ->
                            log.setNum == stateSet.setNum && isLabelMatch(stateSet.label, log.exerciseLabel)
                        } ?: allRecentLogs.firstOrNull { log ->
                            isLabelMatch(stateSet.label, log.exerciseLabel)
                        }
                        if (matchingLog != null) {
                            stateSet.weight = matchingLog.weightAchieved
                            stateSet.reps = matchingLog.repsAchieved
                            stateSet.baselineWeight = matchingLog.weightAchieved
                            stateSet.baselineReps = matchingLog.repsAchieved
                        }

                        if (restoreFromPrefs) {
                            val isSessionActive = prefs.getBoolean("is_session_active", false)
                            val prefix = "set_${bIdx}_${sIdx}"
                            stateSet.weight = prefs.getFloat("${prefix}_weight", stateSet.weight.toFloat()).toDouble()
                            stateSet.reps = prefs.getInt("${prefix}_reps", stateSet.reps)
                            stateSet.isCompleted = if (isSessionActive) prefs.getBoolean("${prefix}_completed", false) else false
                        }
                    }
                }
            }

            activeBlocks.clear()
            activeBlocks.addAll(baseTemplate)
            if (!restoreFromPrefs) {
                sessionStartTime = 0
                stopRestTimer()
            }
            autoSelectFirstIncompleteSet()
            refreshHistoryListCache()
        }
    }

    // STAGE 3 ENGINE: Load Logged Historical Payload Decoupled from current templates
    fun loadHistoricalSessionReplay(index: Int) {
        val targetSession = historicalSessions.getOrNull(index) ?: return

        // If stepping into history from a live session, snapshot live state
        if (!isHistoryMode) {
            persistActiveSession()
            liveBlocksBackup = activeBlocks.map { block ->
                WorkoutBlockState(
                    title = block.title,
                    sets = block.sets.map { s ->
                        LoggableSetState(
                            label = s.label,
                            setNum = s.setNum,
                            initialWeight = s.weight,
                            initialReps = s.reps,
                            increment = s.increment,
                            baselineWeight = s.baselineWeight,
                            baselineReps = s.baselineReps,
                            minReps = s.minReps,
                            maxReps = s.maxReps,
                            restSeconds = s.restSeconds
                        ).apply {
                            isCompleted = s.isCompleted
                        }
                    },
                    initialNotes = block.notes,
                    defaultRestSeconds = block.defaultRestSeconds
                )
            }
            liveDayIndexBackup = currentDayIndex
            liveBodyWeightBackup = bodyWeight
            liveSessionStartTimeBackup = sessionStartTime
        }

        activeHistoryPointer = index
        isHistoryMode = true
        focusedSetInstance = null
        stopRestTimer()

        viewModelScopeLaunch {
            bodyWeight = targetSession.bodyWeight

            val targetLogs = withContext(Dispatchers.IO) {
                dao.getSetLogsForSession(targetSession.sessionId)
            }

            val historyBlocks = mutableListOf<WorkoutBlockState>()

            withContext(Dispatchers.IO) {
                val groupedLogs = targetLogs.groupBy { it.exerciseBlockTitle }
                groupedLogs.forEach { (blockTitle, logs) ->
                    val savedNotes = dao.getNotesForBlock(blockTitle)?.notesText ?: ""
                    val sets = logs.map { log ->
                        val (defaultMin, defaultMax) = getExerciseRepRange(log.exerciseLabel)
                        LoggableSetState(
                            label = log.exerciseLabel,
                            setNum = log.setNum,
                            initialWeight = log.weightAchieved,
                            initialReps = log.repsAchieved,
                            increment = 1.0,
                            baselineWeight = log.weightAchieved,
                            baselineReps = log.repsAchieved,
                            minReps = defaultMin,
                            maxReps = defaultMax
                        ).apply {
                            isCompleted = true
                        }
                    }
                    historyBlocks.add(WorkoutBlockState(blockTitle, sets, savedNotes))
                }
            }

            if (historyBlocks.isEmpty()) {
                val fallback = createRoutineTemplate(targetSession.dayName)
                historyBlocks.addAll(fallback)
            }

            activeBlocks.clear()
            activeBlocks.addAll(historyBlocks)
        }
    }

    private suspend fun refreshHistoryListCache() {
        withContext(Dispatchers.IO) {
            val runs = dao.getAllSessionsDesc()
            withContext(Dispatchers.Main) {
                historicalSessions.clear()
                historicalSessions.addAll(runs)
            }
        }
    }

    fun stepOlderInHistory() {
        val nextIndex = activeHistoryPointer + 1
        if (nextIndex < historicalSessions.size) {
            loadHistoricalSessionReplay(nextIndex)
        }
    }

    fun stepNewerInHistory() {
        val prevIndex = activeHistoryPointer - 1
        if (prevIndex >= 0) {
            loadHistoricalSessionReplay(prevIndex)
        } else if (prevIndex == -1) {
            returnToLiveSession()
        }
    }

    fun returnToLiveSession() {
        isHistoryMode = false
        activeHistoryPointer = -1
        stopRestTimer()

        val backup = liveBlocksBackup
        if (backup != null) {
            currentDayIndex = liveDayIndexBackup ?: currentDayIndex
            bodyWeight = liveBodyWeightBackup ?: bodyWeight
            sessionStartTime = liveSessionStartTimeBackup ?: sessionStartTime
            activeBlocks.clear()
            activeBlocks.addAll(backup)
            liveBlocksBackup = null
            autoSelectFirstIncompleteSet()
            viewModelScopeLaunch {
                refreshHistoryListCache()
            }
        } else {
            loadActiveDayRoutine(restoreFromPrefs = true)
        }
    }

    fun autoSelectFirstIncompleteSet() {
        if (isHistoryMode) return
        for (block in activeBlocks) {
            val nextSet = block.sets.find { !it.isCompleted }
            if (nextSet != null) {
                focusedSetInstance = nextSet
                return
            }
        }
        focusedSetInstance = activeBlocks.firstOrNull()?.sets?.firstOrNull()
    }

    fun persistActiveSession() {
        if (isHistoryMode) return
        val prefs = appContext.getSharedPreferences("WetzelBulkPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_session_active", sessionStartTime != 0L)
            putInt("active_day_index", currentDayIndex)
            putLong("active_session_start_time", sessionStartTime)
            putFloat("active_body_weight", bodyWeight.toFloat())

            activeBlocks.forEachIndexed { bIdx, block ->
                block.sets.forEachIndexed { sIdx, set ->
                    val prefix = "set_${bIdx}_${sIdx}"
                    putFloat("${prefix}_weight", set.weight.toFloat())
                    putInt("${prefix}_reps", set.reps)
                    putBoolean("${prefix}_completed", set.isCompleted)
                }
            }
            apply()
        }
    }

    fun handleSetChecked(block: WorkoutBlockState, set: LoggableSetState, isChecked: Boolean) {
        if (isHistoryMode) return
        focusedSetInstance = set
        set.isCompleted = isChecked

        if (isChecked) {
            if (sessionStartTime == 0L) {
                sessionStartTime = System.currentTimeMillis()
            }

            val restTime = set.restSeconds
            if (restTime > 0) {
                val intent = Intent(appContext, RestTimerService::class.java).apply {
                    action = "START"
                    putExtra("DURATION_SECONDS", restTime)
                    putExtra("BLOCK_TITLE", block.title)
                }
                appContext.startService(intent)
            } else {
                stopRestTimer()
            }

            autoSelectFirstIncompleteSet()
        }
        persistActiveSession()
    }

    fun stopRestTimer() {
        val intent = Intent(appContext, RestTimerService::class.java).apply { action = "STOP" }
        appContext.startService(intent)
    }

    fun saveNotesUpdate(blockTitle: String, updatedText: String) {
        viewModelScopeLaunch {
            withContext(Dispatchers.IO) {
                dao.saveNotes(ExerciseNotes(blockTitle, updatedText))
            }
            activeBlocks.find { it.title == blockTitle }?.notes = updatedText
        }
    }

    fun finishWorkoutSession() {
        if (isHistoryMode) return
        viewModelScopeLaunch {
            val endTimeEpoch = System.currentTimeMillis()
            val finalStart = if (sessionStartTime == 0L) endTimeEpoch else sessionStartTime

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = dateFormat.format(Date())

            val logsToSave = mutableListOf<SetLog>()
            activeBlocks.forEach { block ->
                block.sets.forEach { set ->
                    if (set.isCompleted) {
                        logsToSave.add(
                            SetLog(
                                sessionId = 0,
                                exerciseBlockTitle = block.title,
                                exerciseLabel = set.label,
                                setNum = set.setNum,
                                weightAchieved = set.weight,
                                repsAchieved = set.reps
                            )
                        )
                    }
                }
            }

            if (logsToSave.isEmpty()) {
                currentDayIndex = (currentDayIndex + 1) % 4
                val prefs = appContext.getSharedPreferences("WetzelBulkPrefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putBoolean("is_session_active", false)
                editor.remove("active_session_start_time")
                prefs.all.keys.filter { it.startsWith("set_") }.forEach { editor.remove(it) }
                editor.apply()
                liveBlocksBackup = null
                loadActiveDayRoutine()
                return@viewModelScopeLaunch
            }

            val finishedDay = currentDayName
            val finishedWeight = bodyWeight
            val finishedDate = dateStr
            val savedSetsCount = logsToSave.size

            withContext(Dispatchers.IO) {
                val newSessionId = dao.insertSession(
                    WorkoutSession(
                        dateString = dateStr,
                        dayName = currentDayName,
                        startTime = finalStart,
                        endTime = endTimeEpoch,
                        bodyWeight = bodyWeight
                    )
                )

                val finalizedLogs = logsToSave.map { it.copy(sessionId = newSessionId) }
                dao.insertSetLogs(finalizedLogs)
            }

            currentDayIndex = (currentDayIndex + 1) % 4

            val prefs = appContext.getSharedPreferences("WetzelBulkPrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putBoolean("is_session_active", false)
            editor.remove("active_session_start_time")
            prefs.all.keys.filter { it.startsWith("set_") }.forEach { editor.remove(it) }
            editor.apply()
            liveBlocksBackup = null

            loadActiveDayRoutine()

            // Trigger automatic Git upload in background if configured and enabled
            if (GitSyncManager.isAutoSyncEnabled(appContext)) {
                viewModelScopeLaunch {
                    isGitSyncing = true
                    gitSyncStatus = "Syncing to GitHub..."
                    val result = withContext(Dispatchers.IO) {
                        GitSyncManager.syncDatabaseToGit(
                            context = appContext,
                            database = db,
                            commitNote = "Workout Finish: $finishedDate - $finishedDay ($savedSetsCount sets, ${finishedWeight} lbs)"
                        )
                    }
                    isGitSyncing = false
                    result.onSuccess { msg ->
                        gitSyncStatus = "✓ $msg"
                    }.onFailure { err ->
                        gitSyncStatus = "⚠ Sync failed: ${err.message}"
                    }
                }
            }
        }
    }

    fun overrideDayTrack() {
        if (isHistoryMode) return
        currentDayIndex = (currentDayIndex + 1) % 4
        liveBlocksBackup = null
        loadActiveDayRoutine()
    }

    private fun viewModelScopeLaunch(block: suspend CoroutineScope.() -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            block()
        }
    }

    private fun createRoutineTemplate(day: String): List<WorkoutBlockState> {
        return when (day) {
            "PUSH DAY" -> listOf(
                WorkoutBlockState(
                    title = "Incline Press (4 Sets)",
                    sets = listOf(
                        LoggableSetState("Inc. Press", 1, 50.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Inc. Press", 2, 50.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Inc. Press", 3, 50.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Inc. Press", 4, 50.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120)
                    ),
                    defaultRestSeconds = 120
                ),
                WorkoutBlockState(
                    title = "Flat Press (3 Sets)",
                    sets = listOf(
                        LoggableSetState("Flat Press", 1, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Flat Press", 2, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Flat Press", 3, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120)
                    ),
                    defaultRestSeconds = 120
                ),
                WorkoutBlockState(
                    title = "Superset: DB Fly / DB Lateral Raise",
                    sets = listOf(
                        LoggableSetState("DB Fly", 1, 30.0, 12, 5.0, minReps = 10, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Lat. Raise", 1, 15.0, 15, 2.5, minReps = 12, maxReps = 20, restSeconds = 75),
                        LoggableSetState("DB Fly", 2, 30.0, 12, 5.0, minReps = 10, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Lat. Raise", 2, 15.0, 15, 2.5, minReps = 12, maxReps = 20, restSeconds = 75),
                        LoggableSetState("DB Fly", 3, 30.0, 12, 5.0, minReps = 10, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Lat. Raise", 3, 15.0, 15, 2.5, minReps = 12, maxReps = 20, restSeconds = 75)
                    ),
                    defaultRestSeconds = 75
                ),
                WorkoutBlockState(
                    title = "Superset: Rope Pushdown / Skull Crusher",
                    sets = listOf(
                        LoggableSetState("Rope Push", 1, 40.0, 12, 2.5, minReps = 8, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Skull Crush", 1, 25.0, 12, 5.0, minReps = 10, maxReps = 15, restSeconds = 75),
                        LoggableSetState("Rope Push", 2, 40.0, 12, 2.5, minReps = 8, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Skull Crush", 2, 25.0, 12, 5.0, minReps = 10, maxReps = 15, restSeconds = 75),
                        LoggableSetState("Rope Push", 3, 40.0, 12, 2.5, minReps = 8, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Skull Crush", 3, 25.0, 12, 5.0, minReps = 10, maxReps = 15, restSeconds = 75)
                    ),
                    defaultRestSeconds = 75
                )
            )
            "LEGS DAY" -> listOf(
                WorkoutBlockState(
                    title = "Leg Press (4 Sets)",
                    sets = listOf(
                        LoggableSetState("Leg Press", 1, 180.0, 10, 10.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Leg Press", 2, 180.0, 10, 10.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Leg Press", 3, 180.0, 10, 10.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Leg Press", 4, 180.0, 10, 10.0, minReps = 8, maxReps = 12, restSeconds = 120)
                    ),
                    defaultRestSeconds = 120
                ),
                WorkoutBlockState(
                    title = "Romanian Dead Lift (3 Sets)",
                    sets = listOf(
                        LoggableSetState("RDL", 1, 50.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("RDL", 2, 50.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("RDL", 3, 50.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120)
                    ),
                    defaultRestSeconds = 120
                ),
                WorkoutBlockState(
                    title = "Leg Press Calf Raise (3 Sets)",
                    sets = listOf(
                        LoggableSetState("Calf Raise", 1, 100.0, 15, 10.0, minReps = 12, maxReps = 20, restSeconds = 60),
                        LoggableSetState("Calf Raise", 2, 100.0, 15, 10.0, minReps = 12, maxReps = 20, restSeconds = 60),
                        LoggableSetState("Calf Raise", 3, 100.0, 15, 10.0, minReps = 12, maxReps = 20, restSeconds = 60)
                    ),
                    defaultRestSeconds = 60
                ),
                WorkoutBlockState(
                    title = "Superset: Crunch / Back Extension",
                    sets = listOf(
                        LoggableSetState("Crunch", 1, 0.0, 15, 1.0, minReps = 10, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Back Ext.", 1, 0.0, 12, 5.0, minReps = 10, maxReps = 15, restSeconds = 60),
                        LoggableSetState("Crunch", 2, 0.0, 15, 1.0, minReps = 10, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Back Ext.", 2, 0.0, 12, 5.0, minReps = 10, maxReps = 15, restSeconds = 60)
                    ),
                    defaultRestSeconds = 60
                )
            )
            "PULL DAY" -> listOf(
                WorkoutBlockState(
                    title = "Lat Pulldown (4 Sets)",
                    sets = listOf(
                        LoggableSetState("Lat Pull", 1, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Lat Pull", 2, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Lat Pull", 3, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Lat Pull", 4, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120)
                    ),
                    defaultRestSeconds = 120
                ),
                WorkoutBlockState(
                    title = "Seated Cable Row - V-Grip (4 Sets)",
                    sets = listOf(
                        LoggableSetState("Cable Row", 1, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Cable Row", 2, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Cable Row", 3, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120),
                        LoggableSetState("Cable Row", 4, 90.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 120)
                    ),
                    defaultRestSeconds = 120
                ),
                WorkoutBlockState(
                    title = "Superset: Straight-Arm Pulldown / Face Pull",
                    sets = listOf(
                        LoggableSetState("Pulldown", 1, 30.0, 12, 2.5, minReps = 10, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Face Pull", 1, 20.0, 15, 2.5, minReps = 12, maxReps = 20, restSeconds = 60),
                        LoggableSetState("Pulldown", 2, 30.0, 12, 2.5, minReps = 10, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Face Pull", 2, 20.0, 15, 2.5, minReps = 12, maxReps = 20, restSeconds = 60),
                        LoggableSetState("Pulldown", 3, 30.0, 12, 2.5, minReps = 10, maxReps = 15, restSeconds = 0),
                        LoggableSetState("Face Pull", 3, 20.0, 15, 2.5, minReps = 12, maxReps = 20, restSeconds = 60)
                    ),
                    defaultRestSeconds = 60
                ),
                WorkoutBlockState(
                    title = "Superset: Incline DB Curl / Hammer Curl",
                    sets = listOf(
                        LoggableSetState("Inc. Curl", 1, 25.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 0),
                        LoggableSetState("Hammer", 1, 25.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 75),
                        LoggableSetState("Inc. Curl", 2, 25.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 0),
                        LoggableSetState("Hammer", 2, 25.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 75),
                        LoggableSetState("Inc. Curl", 3, 25.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 0),
                        LoggableSetState("Hammer", 3, 25.0, 10, 5.0, minReps = 8, maxReps = 12, restSeconds = 75)
                    ),
                    defaultRestSeconds = 75
                )
            )
            else -> listOf(
                WorkoutBlockState(
                    title = "Treadmill Cardio",
                    sets = listOf(
                        LoggableSetState("Completed Treadmill Session", 1, 0.0, 0, 0.0, minReps = 0, maxReps = 0, restSeconds = 0)
                    ),
                    initialNotes = "Target Settings:\n- Speed: 3.0 mph\n- Incline: 10\n- Target: 350 Calories",
                    defaultRestSeconds = 0
                )
            )
        }
    }
}

class WorkoutViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkoutViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HIGH DATA-DENSITY NATIVE APP LAYOUT
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WetzelBulkApp(viewModel: WorkoutViewModel = viewModel()) {
    var showNotesDialog by remember { mutableStateOf(false) }
    var activeBlockForNotes by remember { mutableStateOf<WorkoutBlockState?>(null) }
    var interactiveNotesTextField by remember { mutableStateOf("") }

    var showFinishConfirmationDialog by remember { mutableStateOf(false) }
    var showWeightInputDialog by remember { mutableStateOf(false) }
    var weightInputString by remember { mutableStateOf("") }

    val uriHandler = LocalUriHandler.current

    var timeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            Column {
                // High-Density Header
                TopAppBar(
                    title = {
                        Box(modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(text = "Wetzel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                    Text(text = "Bulk", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                    Text(
                                        text = "v1.9",
                                        fontSize = 9.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                                    )
                                }

                                val context = LocalContext.current
                                val isGitReady = GitSyncManager.isConfigured(context)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (viewModel.isGitSyncing) Color(0xFFEFF6FF) else if (isGitReady) Color(0xFFF0FDF4) else Color(0xFFF3F4F6),
                                    modifier = Modifier
                                        .padding(start = 5.dp)
                                        .clickable { viewModel.showGitSettingsDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        if (viewModel.isGitSyncing) {
                                            CircularProgressIndicator(
                                                strokeWidth = 1.2.dp,
                                                modifier = Modifier.size(8.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = if (viewModel.isGitSyncing) "Syncing" else if (isGitReady) "☁ Git" else "☁ Setup",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.isGitSyncing) MaterialTheme.colorScheme.primary else if (isGitReady) Color(0xFF16A34A) else Color.Gray
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (viewModel.isHistoryMode) Color(0xFFFEF3C7) else Color(0xFFF0FDF4),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Text(
                                    text = if (viewModel.isHistoryMode) "REPLAY" else viewModel.currentDayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (viewModel.isHistoryMode) Color(0xFFD97706) else Color(0xFF16A34A),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!viewModel.isHistoryMode && TimerState.isTimerActive) {
                                    val isNegative = TimerState.timerSeconds < 0
                                    Text(
                                        text = if (isNegative) "LIFT! ${TimerState.timerSeconds}s" else "⏱ ${TimerState.timerSeconds}s",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isNegative) Color.Red else Color(0xFF16A34A),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clickable { viewModel.stopRestTimer() }
                                    )
                                } else if (!viewModel.isHistoryMode) {
                                    Text(text = timeString, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(end = 6.dp))
                                }

                                Button(
                                    onClick = { viewModel.overrideDayTrack() },
                                    enabled = !viewModel.isHistoryMode,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Override", fontSize = 11.sp)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )

                // Compact Body Weight Logging Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚖ Body Weight:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f lbs", viewModel.bodyWeight),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(enabled = !viewModel.isHistoryMode) {
                                    weightInputString = String.format(Locale.US, "%.1f", viewModel.bodyWeight)
                                    showWeightInputDialog = true
                                }
                            )
                            if (!viewModel.isHistoryMode) {
                                Text(
                                    text = " (tap to edit)",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }

                        if (!viewModel.isHistoryMode) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        viewModel.bodyWeight = maxOf(50.0, (viewModel.bodyWeight - 0.2))
                                        viewModel.persistActiveSession()
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("−", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(
                                    onClick = {
                                        viewModel.bodyWeight += 0.2
                                        viewModel.persistActiveSession()
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // Compact Time Travel Tape Deck Bar
                Surface(
                    color = if (viewModel.isHistoryMode) Color(0xFFFFFBEB) else Color(0xFFF3F4F6),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasOlder = viewModel.activeHistoryPointer + 1 < viewModel.historicalSessions.size
                        val hasNewer = viewModel.isHistoryMode

                        TextButton(
                            onClick = { viewModel.stepOlderInHistory() },
                            enabled = viewModel.historicalSessions.isNotEmpty() && hasOlder,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(text = "⏮ Older", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = if (viewModel.isHistoryMode) {
                                "${viewModel.currentHistorySession?.dateString} (${viewModel.currentHistorySession?.dayName})"
                            } else {
                                "🔴 LIVE TARGET"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (viewModel.isHistoryMode) Color(0xFFD97706) else Color(0xFF16A34A)
                        )

                        TextButton(
                            onClick = { viewModel.stepNewerInHistory() },
                            enabled = hasNewer,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = if (viewModel.activeHistoryPointer == 0) "🟢 Go Live" else "Newer ⏭",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.activeHistoryPointer == 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White
            ) {
                Button(
                    onClick = { showFinishConfirmationDialog = true },
                    enabled = !viewModel.isHistoryMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        disabledContainerColor = Color(0xFFE5E7EB)
                    ),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text(
                        text = if (viewModel.isHistoryMode) "History Review Mode Only" else "Finish Workout & Save Baseline",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewModel.isHistoryMode) Color.Gray else Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 3.dp, vertical = 1.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(viewModel.activeBlocks) { block ->
                ChronologicalSupersetCard(
                    block = block,
                    focusedSetInstance = viewModel.focusedSetInstance,
                    isHistoryMode = viewModel.isHistoryMode,
                    onRowTappedDirectly = { chosenSet -> viewModel.focusedSetInstance = chosenSet },
                    onNotesIconClicked = {
                        activeBlockForNotes = block
                        interactiveNotesTextField = block.notes
                        showNotesDialog = true
                    },
                    onStatusToggled = { set, isChecked -> viewModel.handleSetChecked(block, set, isChecked) }
                )
            }
        }
    }

    // Direct Weight Input Dialog
    if (showWeightInputDialog) {
        AlertDialog(
            onDismissRequest = { showWeightInputDialog = false },
            title = { Text("Log Body Weight", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter today's body weight (lbs):", fontSize = 12.sp)
                    OutlinedTextField(
                        value = weightInputString,
                        onValueChange = { weightInputString = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = weightInputString.toDoubleOrNull()
                        if (parsed != null && parsed > 30.0 && parsed < 600.0) {
                            viewModel.bodyWeight = parsed
                            viewModel.persistActiveSession()
                        }
                        showWeightInputDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Save", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWeightInputDialog = false },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // Finish Workout Safety Modal
    if (showFinishConfirmationDialog) {
        val completedSetsCount = viewModel.activeBlocks.flatMap { it.sets }.count { it.isCompleted }
        val totalExercisesCount = viewModel.activeBlocks.size
        val context = LocalContext.current
        val isGitAuto = GitSyncManager.isAutoSyncEnabled(context)
        AlertDialog(
            onDismissRequest = { showFinishConfirmationDialog = false },
            title = { Text("Record Workout?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "You logged $completedSetsCount sets across $totalExercisesCount exercises.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(Locale.US, "Recorded Body Weight: %.1f lbs", viewModel.bodyWeight),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isGitAuto) {
                        Text(
                            text = "☁ Cloud Sync: Enabled (will upload database to GitHub on save)",
                            fontSize = 11.sp,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Save this session to your permanent workout history?",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishConfirmationDialog = false
                        viewModel.finishWorkoutSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Yes, Record", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showFinishConfirmationDialog = false },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }
            }
        )
    }

    // Git Sync Settings Modal
    if (viewModel.showGitSettingsDialog) {
        GitSyncSettingsDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.showGitSettingsDialog = false }
        )
    }

    // Notes Dialog
    if (showNotesDialog && activeBlockForNotes != null) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text(text = activeBlockForNotes?.title ?: "Notes", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = interactiveNotesTextField,
                        onValueChange = { interactiveNotesTextField = it },
                        placeholder = { Text("Add targets, platform rules, or paste YouTube cues...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                        enabled = !viewModel.isHistoryMode
                    )

                    if (interactiveNotesTextField.isNotEmpty()) {
                        Text("Active Cues & Media Links:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        HyperlinkedText(
                            text = interactiveNotesTextField,
                            onUrlClick = { url -> runCatching { uriHandler.openUri(url) } }
                        )
                    }
                }
            },
            confirmButton = {
                if (!viewModel.isHistoryMode) {
                    Button(
                        onClick = {
                            viewModel.saveNotesUpdate(activeBlockForNotes!!.title, interactiveNotesTextField)
                            showNotesDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save", fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = { showNotesDialog = false }) { Text("Close", fontSize = 12.sp) }
                }
            },
            dismissButton = {
                if (!viewModel.isHistoryMode) {
                    TextButton(
                        onClick = {
                            viewModel.saveNotesUpdate(activeBlockForNotes!!.title, "")
                            showNotesDialog = false
                        }
                    ) {
                        Text("Clear All", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitSyncSettingsDialog(
    viewModel: WorkoutViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentConfig = remember { GitSyncManager.getConfig(context) }

    var repoText by remember { mutableStateOf(currentConfig.repo) }
    var tokenText by remember { mutableStateOf(currentConfig.token) }
    var branchText by remember { mutableStateOf(currentConfig.branch) }
    var autoSyncChecked by remember { mutableStateOf(currentConfig.autoSync) }
    var showToken by remember { mutableStateOf(false) }

    var testStatusText by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☁ GitHub Database Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Automatically commits your SQLite database & workouts.json to your GitHub repository whenever you finish a workout.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )

                OutlinedTextField(
                    value = repoText,
                    onValueChange = { repoText = it; testStatusText = null },
                    label = { Text("Repository (owner/repo)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. wetzel/WetzelBulk", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it; testStatusText = null },
                    label = { Text("Personal Access Token (PAT)", fontSize = 12.sp) },
                    placeholder = { Text("ghp_... or github_pat_...", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (showToken) "Hide" else "Show",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showToken = !showToken }
                                .padding(horizontal = 8.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                OutlinedTextField(
                    value = branchText,
                    onValueChange = { branchText = it },
                    label = { Text("Branch", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoSyncChecked = !autoSyncChecked }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = autoSyncChecked,
                        onCheckedChange = { autoSyncChecked = it },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-upload on workout finish", fontSize = 12.sp)
                }

                // Test Connection & Manual Sync Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isTesting = true
                            testStatusText = "Connecting to GitHub..."
                            scope.launch {
                                val res = GitSyncManager.testConnection(repoText, tokenText)
                                isTesting = false
                                res.onSuccess { msg ->
                                    testStatusText = "✓ $msg"
                                }.onFailure { err ->
                                    testStatusText = "✗ ${err.message}"
                                }
                            }
                        },
                        enabled = !isTesting && repoText.isNotBlank() && tokenText.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Test", fontSize = 11.sp)
                    }

                    if (GitSyncManager.isConfigured(context)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.triggerGitSync("Manual Sync from App")
                            },
                            enabled = !viewModel.isGitSyncing,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            if (viewModel.isGitSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("Sync Now", fontSize = 11.sp)
                        }
                    }
                }

                testStatusText?.let { status ->
                    val isSuccess = status.startsWith("✓")
                    Text(
                        text = status,
                        fontSize = 11.sp,
                        color = if (isSuccess) Color(0xFF16A34A) else Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (viewModel.gitSyncStatus.isNotBlank()) {
                    Text(
                        text = "Status: ${viewModel.gitSyncStatus}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    GitSyncManager.saveConfig(
                        context = context,
                        repo = repoText,
                        token = tokenText,
                        branch = branchText,
                        autoSync = autoSyncChecked
                    )
                    onDismiss()
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Save Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Close", fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun HyperlinkedText(text: String, onUrlClick: (String) -> Unit) {
    val urlPattern = "(https?://[^\\s]+)".toRegex()
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        urlPattern.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1

            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }

            val url = matchResult.value
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                style = SpanStyle(
                    color = Color(0xFF4F46E5),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(url)
            }
            lastIndex = end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onUrlClick(annotation.item)
                }
        }
    )
}

@Composable
fun ChronologicalSupersetCard(
    block: WorkoutBlockState,
    focusedSetInstance: LoggableSetState?,
    isHistoryMode: Boolean,
    onRowTappedDirectly: (LoggableSetState) -> Unit,
    onNotesIconClicked: () -> Unit,
    onStatusToggled: (LoggableSetState, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) {
            // High-Density Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (block.defaultRestSeconds > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF3F4F6),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "⏱ ${block.defaultRestSeconds}s",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNotesIconClicked,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Text("ⓘ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (block.sets.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (block.sets.first().increment > 0.0) {
                        Text("Exercise (Set) [Target]", modifier = Modifier.weight(3.4f), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        Text("Setting/Load", modifier = Modifier.weight(2.4f), fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                        Text("Reps", modifier = Modifier.weight(2.1f), fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                    } else {
                        Text("Activity Track", modifier = Modifier.weight(7.9f), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = "Done!",
                        modifier = Modifier.weight(1.0f),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Medium
                    )
                }

                block.sets.forEach { set ->
                    val isRowFocused = set == focusedSetInstance

                    ChronologicalSetRow(
                        set = set,
                        isRowFocused = isRowFocused,
                        isHistoryMode = isHistoryMode,
                        onRowInteracted = { onRowTappedDirectly(set) },
                        onStatusToggled = { isChecked -> onStatusToggled(set, isChecked) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChronologicalSetRow(
    set: LoggableSetState,
    isRowFocused: Boolean,
    isHistoryMode: Boolean,
    onRowInteracted: () -> Unit,
    onStatusToggled: (Boolean) -> Unit
) {
    val isLocked = set.isCompleted || isHistoryMode

    val backgroundColor = if (set.isCompleted) {
        Color(0xFFC8E6C9)
    } else if (set.setNum % 2 == 0) {
        Color(0xFFEEF0F3)
    } else {
        Color.Transparent
    }

    val effectiveMin = if (set.minReps > 0) set.minReps else getExerciseRepRange(set.label).first
    val effectiveMax = if (set.maxReps > 0) set.maxReps else getExerciseRepRange(set.label).second

    // Continuous Persistent Rep Color Logic
    val repTextColor = when {
        effectiveMin > 0 && set.reps < effectiveMin -> Color(0xFFDC2626) // RED: Under target
        effectiveMax > 0 && set.reps >= effectiveMax -> Color(0xFF16A34A) // GREEN: At or over max target
        else -> if (set.isCompleted) Color.DarkGray else MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(3.dp))
            .clickable(enabled = !isHistoryMode) { onRowInteracted() }
            .padding(
                horizontal = 3.dp,
                vertical = 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (set.increment > 0.0) {
            val currentStepDelta = if (set.weight < 20.0) 1.0 else 5.0

            val displayLabel = when {
                set.label.contains("Skull", ignoreCase = true) -> "Skull Crush"
                set.label.contains("Hammer", ignoreCase = true) -> "Hammer"
                else -> set.label
            }

            val labelText = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontSize = if (isRowFocused && !isHistoryMode) 12.5.sp else 11.sp,
                        fontWeight = if (isRowFocused && !isHistoryMode) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (set.isCompleted) Color.DarkGray else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    append("$displayLabel (${set.setNum})")
                }
                if (effectiveMin > 0 && effectiveMax > 0) {
                    withStyle(
                        SpanStyle(
                            fontSize = if (isRowFocused && !isHistoryMode) 10.5.sp else 9.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (set.isCompleted) Color.Gray else Color(0xFF2E7D32)
                        )
                    ) {
                        append(" [$effectiveMin–$effectiveMax]")
                    }
                }
            }

            Text(
                text = labelText,
                modifier = Modifier.weight(3.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Weight Control Column
            Row(
                modifier = Modifier.weight(2.4f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        onRowInteracted()
                        val dynamicStep = if (set.weight <= 20.0) 1.0 else 5.0
                        if (set.weight > 0) set.weight -= dynamicStep
                    },
                    enabled = !isLocked,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(if (isRowFocused && !isHistoryMode) 28.dp else 23.dp)
                ) {
                    Text(text = "−", fontSize = if (isRowFocused && !isHistoryMode) 18.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (isLocked) Color.Gray else MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = set.weight.toInt().toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isRowFocused && !isHistoryMode) 15.sp else 13.sp,
                    modifier = Modifier.padding(horizontal = 1.dp),
                    color = if (isLocked && !isHistoryMode) Color.Gray else MaterialTheme.colorScheme.onSurface
                )
                if (!isHistoryMode && set.baselineWeight > 0) {
                    val indicator = when {
                        set.weight > set.baselineWeight -> "⚡"
                        set.weight < set.baselineWeight -> "🐌"
                        else -> ""
                    }
                    if (indicator.isNotEmpty()) {
                        Text(indicator, fontSize = 9.sp)
                    }
                }
                TextButton(
                    onClick = {
                        onRowInteracted()
                        set.weight += currentStepDelta
                    },
                    enabled = !isLocked,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(if (isRowFocused && !isHistoryMode) 28.dp else 23.dp)
                ) {
                    Text(text = "+", fontSize = if (isRowFocused && !isHistoryMode) 18.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (isLocked) Color.Gray else MaterialTheme.colorScheme.primary)
                }
            }

            // Reps Control Column with Persistent Red/Green Target Logic
            Row(
                modifier = Modifier.weight(2.1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        onRowInteracted()
                        if (set.reps > 0) set.reps -= 1
                    },
                    enabled = !isLocked,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(if (isRowFocused && !isHistoryMode) 28.dp else 23.dp)
                ) {
                    Text(text = "−", fontSize = if (isRowFocused && !isHistoryMode) 18.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (isLocked) Color.Gray else MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = set.reps.toString(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isRowFocused && !isHistoryMode) 16.sp else 13.sp,
                    modifier = Modifier.padding(horizontal = 1.dp),
                    color = repTextColor
                )
                if (!isHistoryMode && set.baselineWeight > 0 && set.weight == set.baselineWeight) {
                    val indicator = when {
                        set.reps > set.baselineReps -> "⚡"
                        set.reps < set.baselineReps -> "🐌"
                        else -> ""
                    }
                    if (indicator.isNotEmpty()) {
                        Text(indicator, fontSize = 9.sp)
                    }
                }
                TextButton(
                    onClick = {
                        onRowInteracted()
                        set.reps += 1
                    },
                    enabled = !isLocked,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(if (isRowFocused && !isHistoryMode) 28.dp else 23.dp)
                ) {
                    Text(text = "+", fontSize = if (isRowFocused && !isHistoryMode) 18.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (isLocked) Color.Gray else MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Text(
                text = set.label,
                modifier = Modifier.weight(7.9f).padding(start = 2.dp),
                fontSize = if (isRowFocused && !isHistoryMode) 14.sp else 12.5.sp,
                fontWeight = if (isRowFocused && !isHistoryMode) FontWeight.Bold else FontWeight.Medium,
                color = if (set.isCompleted) Color.DarkGray else MaterialTheme.colorScheme.onSurface
            )
        }

        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Checkbox(
                checked = set.isCompleted,
                onCheckedChange = {
                    if (!isHistoryMode) {
                        set.isCompleted = it
                        onStatusToggled(it)
                    }
                },
                enabled = !isHistoryMode,
                modifier = Modifier.size(if (isRowFocused && !isHistoryMode) 24.dp else 21.dp)
            )
        }
    }
}