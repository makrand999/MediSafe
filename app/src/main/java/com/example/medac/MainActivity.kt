package com.example.medac

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.medac.ui.DueMedicineAlarmFullScreen
import com.example.medac.ui.InAppAlarmPlayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medac.ui.theme.CardSurface
import com.example.medac.ui.theme.MedacTheme
import com.example.medac.ui.WelcomePinScreen
import com.example.medac.ui.MedRemindDashboardScreen
import com.example.medac.ui.MedRemindAddMedicationScreen
import com.example.medac.ui.MedRemindCalendarScreen
import com.example.medac.ui.MedRemindHistoryLogScreen
import com.example.medac.ui.MedRemindRefillTrackerScreen
import com.example.medac.ui.MedRemindNotificationsScreen
import com.example.medac.ui.MedRemindMedicineDetailScreen
import com.example.medac.ui.MedRemindMedicinesScreen
import com.example.medac.ui.MedRemindSuccessDialog
import com.example.medac.ui.UpdateMedicinePhotoDialog
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private var mainIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainIntent = intent
        setContent {
            MedacTheme {
                MedacRoot(initialIntent = mainIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainIntent = intent
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_POWER) {
            InAppAlarmPlayer.stop()
            AlarmService.stop(this)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        InAppAlarmPlayer.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedacRoot(initialIntent: Intent? = null) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()
    val viewModel: MedacViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val authState by authViewModel.authState.collectAsState()
    val authBusy by authViewModel.isBusy.collectAsState()
    val authError by authViewModel.error.collectAsState()
    val forgotSent by authViewModel.forgotSent.collectAsState()
    var authMode by rememberSaveable { mutableStateOf("login") } // login | register | forgot

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            android.util.Log.d("Medac", "POST_NOTIFICATIONS permission not granted by user")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) { authViewModel.checkSession(context) }

    // Automatic update check on startup
    var updateAvailable by remember { mutableStateOf<AppUpdateManager.UpdateCheckResult?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val check = AppUpdateManager.checkForUpdate(context)
        if (check.updateAvailable) {
            updateAvailable = check
        }
    }

    updateAvailable?.let { updateInfo ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!isDownloadingUpdate) updateAvailable = null
            },
            title = { Text("Update Available") },
            text = {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Text("A new version of Medac is available from the server.")
                    Text(
                        "Updating will download the latest APK and reset local app data to ensure a clean launch without migration conflicts.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (isDownloadingUpdate) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Text(
                            "Downloading: $downloadProgress%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    updateError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isDownloadingUpdate) {
                            isDownloadingUpdate = true
                            updateError = null
                            scope.launch {
                                val apkFile = AppUpdateManager.downloadApk(context) { progress ->
                                    downloadProgress = progress
                                }
                                if (apkFile != null) {
                                    AppUpdateManager.clearAppData(context, updateInfo.remoteLastModified)
                                    val activity = context as? android.app.Activity
                                    if (activity != null) {
                                        AppUpdateManager.installApk(activity, apkFile)
                                    }
                                } else {
                                    isDownloadingUpdate = false
                                    updateError = "Download failed. Please check network connection."
                                }
                            }
                        }
                    },
                    enabled = !isDownloadingUpdate
                ) {
                    Text(if (isDownloadingUpdate) "Downloading..." else "Update Now")
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    TextButton(onClick = { updateAvailable = null }) {
                        Text("Later")
                    }
                }
            }
        )
    }

    when (val s = authState) {
        is AuthUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        is AuthUiState.LoggedOut -> {
            BackHandler(authMode != "login") { authViewModel.clearError(); authMode = "login" }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (authMode) {
                    "login" -> LoginScreen(
                        isBusy = authBusy,
                        error = authError,
                        onLogin = { e, p ->
                            authViewModel.login(context, e, p) {
                                scope.launch {
                                    try { viewModel.refreshServer(context) } catch (_: Exception) {}
                                }
                            }
                        },
                        onNavigateToRegister = { authViewModel.clearError(); authMode = "register" },
                        onNavigateToForgot = { authViewModel.clearError(); authViewModel.resetForgot(); authMode = "forgot" },
                        onClearError = { authViewModel.clearError() }
                    )
                    "register" -> RegisterScreen(
                        isBusy = authBusy,
                        error = authError,
                        onRegister = { e, p -> authViewModel.register(context, e, p) { authMode = "login" } },
                        onNavigateToLogin = { authViewModel.clearError(); authMode = "login" },
                        onClearError = { authViewModel.clearError() }
                    )
                    "forgot" -> ForgotPasswordScreen(
                        isBusy = authBusy,
                        error = authError,
                        sent = forgotSent,
                        onSend = { e -> authViewModel.forgotPassword(context, e) },
                        onBack = { authViewModel.clearError(); authViewModel.resetForgot(); authMode = "login" }
                    )
                    else -> LoginScreen(
                        isBusy = authBusy,
                        error = authError,
                        onLogin = { e, p ->
                            authViewModel.login(context, e, p) {
                                scope.launch {
                                    try { viewModel.refreshServer(context) } catch (_: Exception) {}
                                }
                            }
                        },
                        onNavigateToRegister = { authViewModel.clearError(); authMode = "register" },
                        onNavigateToForgot = { authViewModel.clearError(); authViewModel.resetForgot(); authMode = "forgot" },
                        onClearError = { authViewModel.clearError() }
                    )
                }
            }
            return
        }
        is AuthUiState.MfaRequired -> {
            BackHandler { authViewModel.goToLogin(); authMode = "login" }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MfaScreen(
                    isBusy = authBusy,
                    error = authError,
                    onVerify = { code, rec -> authViewModel.verifyMfa(context, s.token.removePrefix("mfa_"), code, rec) },
                    onBack = { authViewModel.goToLogin(); authMode = "login" }
                )
            }
            return
        }
        is AuthUiState.LoggedIn -> {}
    }

    val medicines by viewModel.medicines.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val isAnalyzingPhoto by viewModel.isAnalyzingPhoto.collectAsState()
    val aiIdentifying by viewModel.aiIdentifying.collectAsState()
    val aiError by viewModel.aiError.collectAsState()
    val doseLogs by viewModel.doseLogs.collectAsState()
    val activePatientId by viewModel.activePatientId.collectAsState()
    val patientsState by viewModel.patients.collectAsState()
    val alertsState by viewModel.alerts.collectAsState()
    val isOfflineState by viewModel.isOffline.collectAsState()
    val isSyncingState by viewModel.isSyncing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var dueAlarm by remember { mutableStateOf<ActiveAlarm?>(null) }

    fun checkActiveAlarm(intentToCheck: Intent?) {
        val showFromIntent = intentToCheck?.getBooleanExtra(ActiveAlarmStore.EXTRA_SHOW_DUE_ALARM, false) == true
        if (showFromIntent) {
            val name = intentToCheck.getStringExtra(ActiveAlarmStore.EXTRA_DUE_MEDICINE_NAME) ?: ""
            val time = intentToCheck.getStringExtra(ActiveAlarmStore.EXTRA_DUE_TIME) ?: ""
            if (name.isNotBlank()) {
                dueAlarm = ActiveAlarm(name, time, System.currentTimeMillis())
                return
            }
        }
        val stored = ActiveAlarmStore.getActiveAlarm(context)
        if (stored != null) {
            dueAlarm = stored
        }
    }

    LaunchedEffect(initialIntent) {
        checkActiveAlarm(initialIntent)
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intnt: Intent?) {
                val name = intnt?.getStringExtra(ActiveAlarmStore.EXTRA_DUE_MEDICINE_NAME) ?: "Medicine"
                val time = intnt?.getStringExtra(ActiveAlarmStore.EXTRA_DUE_TIME) ?: ""
                dueAlarm = ActiveAlarm(name, time, System.currentTimeMillis())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ActiveAlarmStore.ACTION_MEDICINE_DUE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(dueAlarm) {
        if (dueAlarm != null) {
            InAppAlarmPlayer.start(context)
        } else {
            InAppAlarmPlayer.stop()
        }
    }

    var isPinUnlocked by rememberSaveable { mutableStateOf(true) }
    var activeMedRemindScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var showMedRemindSuccessDialog by remember { mutableStateOf(false) }

    var currentTab by rememberSaveable { mutableStateOf(MedacTab.TODAY) }
    var showAddMedicine by rememberSaveable { mutableStateOf(false) }
    var addStep by rememberSaveable { mutableStateOf(AddMedicineStep.Capture) }
    var expandedDoseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var detailMedicine by remember { mutableStateOf<ManagedMedicine?>(null) }
    var pendingDeleteMedicine by remember { mutableStateOf<ManagedMedicine?>(null) }
    var pendingDiscontinueMedicine by remember { mutableStateOf<ManagedMedicine?>(null) }
    var noteItem by remember { mutableStateOf<DoseScheduleItem?>(null) }
    var noteText by remember { mutableStateOf("") }
    var reminderEditRequest by remember { mutableStateOf<ReminderEntry?>(null) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showCancelDraftConfirm by remember { mutableStateOf(false) }
    var removeTimeIndex by remember { mutableIntStateOf(-1) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var profileSub by rememberSaveable { mutableStateOf<String?>(null) }
    var assistantInput by remember { mutableStateOf("") }
    // Intelligence state backed by ViewModel (real server, not mock)
    val intelligenceMessages by viewModel.intelligenceMessages.collectAsState()
    val intelligenceProposals by viewModel.intelligenceProposals.collectAsState()
    val isIntelligenceLoading by viewModel.isIntelligenceLoading.collectAsState()
    val intelligenceError by viewModel.intelligenceError.collectAsState()
    val scheduleDraft by viewModel.scheduleDraft.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val instructionParse by viewModel.instructionParse.collectAsState()
    val proposalMsg by viewModel.proposalActionMsg.collectAsState()
    var draftConstraints by remember { mutableStateOf("") }
    var draftWake by rememberSaveable { mutableStateOf("07:00") }
    var draftSleep by rememberSaveable { mutableStateOf("22:00") }
    var draftMedicationId by remember { mutableStateOf("") }
    var summaryType by rememberSaveable { mutableStateOf("adherence") }
    var summaryFrom by rememberSaveable { mutableStateOf("") }
    var summaryTo by rememberSaveable { mutableStateOf("") }
    var instructionInput by remember { mutableStateOf("") }
    var scheduleType by rememberSaveable { mutableStateOf("fixed_times") }
    var timingMode by rememberSaveable { mutableStateOf("local_clock") }
    var discreetMode by rememberSaveable { mutableStateOf(false) }
    val quietHours by viewModel.quietHours.collectAsState()
    val inventories by viewModel.inventories.collectAsState()
    val inventoryTxs by viewModel.inventoryTransactions.collectAsState()
    val expirationsMap by viewModel.expirations.collectAsState()
    val symptomLogsState by viewModel.symptomLogs.collectAsState()
    val injectionLogsMap by viewModel.injectionLogs.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCaptureUri?.let { uri -> viewModel.identifyMedicinePhoto(uri, context) }
        }
        pendingCaptureUri = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createPhotoUri(context)
            if (uri != null) {
                pendingCaptureUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    var photoUpdateMedicine by remember { mutableStateOf<ManagedMedicine?>(null) }
    var pendingPhotoUpdateUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoOptionsDialogFor by remember { mutableStateOf<ManagedMedicine?>(null) }

    val photoUpdateCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val med = photoUpdateMedicine
            val uri = pendingPhotoUpdateUri
            if (med != null && uri != null) {
                val updated = viewModel.updateMedicinePhoto(med.id, uri, context)
                if (detailMedicine?.id == med.id && updated != null) {
                    detailMedicine = updated
                }
            }
        }
        pendingPhotoUpdateUri = null
        photoUpdateMedicine = null
    }

    val photoUpdateCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createPhotoUri(context)
            if (uri != null) {
                pendingPhotoUpdateUri = uri
                photoUpdateCameraLauncher.launch(uri)
            }
        }
    }

    val photoUpdateGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val med = photoUpdateMedicine
        if (med != null && uri != null) {
            val updated = viewModel.updateMedicinePhoto(med.id, uri, context)
            if (detailMedicine?.id == med.id && updated != null) {
                detailMedicine = updated
            }
        }
        photoUpdateMedicine = null
    }

    LaunchedEffect(Unit) { viewModel.load(context) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLocalDoseLogs()
                if (dueAlarm == null) {
                    val active = ActiveAlarmStore.getActiveAlarm(context)
                    if (active != null) {
                        dueAlarm = active
                    }
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                InAppAlarmPlayer.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val sendAssistant: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            val t = text.trim()
            assistantInput = ""
            viewModel.sendIntelligenceMessage(t)
        }
    }

    if (showSaveConfirm) {
        ConfirmActionDialog(
            title = "Save medicine?",
            message = "This will save the medicine and schedule any active reminder times.",
            confirmLabel = "Save",
            onConfirm = {
                viewModel.saveDraftMedicine(scheduleType, timingMode)
                showSaveConfirm = false
                showAddMedicine = false
                addStep = AddMedicineStep.Capture
                currentTab = MedacTab.MEDICINES
            },
            onDismiss = { showSaveConfirm = false }
        )
    }
    if (showCancelDraftConfirm) {
        ConfirmActionDialog(
            title = "Discard this medicine?",
            message = "Any captured label details that are not saved will be lost.",
            confirmLabel = "Discard",
            onConfirm = {
                viewModel.clearDraft()
                showCancelDraftConfirm = false
                showAddMedicine = false
                addStep = AddMedicineStep.Capture
            },
            onDismiss = { showCancelDraftConfirm = false }
        )
    }
    pendingDeleteMedicine?.let { medicine ->
        ConfirmActionDialog(
            title = "Archive medicine?",
            message = "This will archive ${medicine.name} and cancel its reminders. You can undo.",
            confirmLabel = "Archive",
            onConfirm = {
                viewModel.removeMedicine(medicine)
                pendingDeleteMedicine = null
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "${medicine.name} archived",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreMedicine(medicine)
                }
            },
            onDismiss = { pendingDeleteMedicine = null }
        )
    }
    pendingDiscontinueMedicine?.let { medicine ->
        ConfirmActionDialog(
            title = "Discontinue ${medicine.name}?",
            message = "It moves to \"Ended\" and its reminders stop. Pausing keeps it in your plan instead.",
            confirmLabel = "Discontinue",
            onConfirm = {
                viewModel.setMedicineStatus(medicine, "discontinued")
                pendingDiscontinueMedicine = null
                detailMedicine = null
                scope.launch { snackbarHostState.showSnackbar("${medicine.name} discontinued") }
            },
            onDismiss = { pendingDiscontinueMedicine = null }
        )
    }
    noteItem?.let { item ->
        val existingNote = viewModel.getDoseLog(item.medicineId, item.time)?.note.orEmpty()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteItem = null },
            title = { Text("Note — ${item.medicineName} ${item.time}") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = noteText.ifBlank { existingNote },
                    onValueChange = { noteText = it },
                    label = { Text("How are you feeling after this dose?") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addDoseNote(item.medicineId, item.medicineName, item.time, noteText.ifBlank { existingNote })
                    noteItem = null; noteText = ""
                    scope.launch { snackbarHostState.showSnackbar("Note saved") }
                }) { Text("Save note") }
            },
            dismissButton = {
                TextButton(onClick = { noteItem = null; noteText = "" }) { Text("Cancel") }
            }
        )
    }
    reminderEditRequest?.let { entry ->
        LaunchedEffect(entry) {
            reminderEditRequest = null
            showTimePicker(context, entry.time) { selected -> viewModel.updateReminderTime(entry, selected) }
        }
    }
    if (removeTimeIndex >= 0) {
        ConfirmActionDialog(
            title = "Remove reminder time?",
            message = "This reminder time will be removed before the medicine is saved.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeReminderTime(removeTimeIndex); removeTimeIndex = -1 },
            onDismiss = { removeTimeIndex = -1 }
        )
    }

    // Due Medicine Full-Screen Simple UI & In-App Alarm
    val currentDue = dueAlarm
    if (currentDue != null) {
        val med = medicines.firstOrNull { it.name.equals(currentDue.medicineName, ignoreCase = true) }
        val medInstruction = med?.instruction?.ifBlank { med.genericNameAndDose } ?: ""

        BackHandler {
            // Same as "Go to Home (Skip)": silence and dismiss without logging.
            InAppAlarmPlayer.stop()
            AlarmService.stop(context)
            ActiveAlarmStore.removeActiveAlarm(context, currentDue.medicineName, currentDue.time)
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifManager.cancel(alarmNotificationId(currentDue.medicineName, currentDue.time))
            notifManager.cancel(preNotificationId(currentDue.medicineName, currentDue.time))
            notifManager.cancel(AlarmService.NOTIFICATION_ID)
            dueAlarm = ActiveAlarmStore.getActiveAlarm(context)
            isPinUnlocked = true
            currentTab = MedacTab.TODAY
        }

        DueMedicineAlarmFullScreen(
            medicineName = currentDue.medicineName,
            time = currentDue.time,
            instructions = medInstruction,
            onMarkTaken = {
                InAppAlarmPlayer.stop()
                AlarmService.stop(context)
                val medId = med?.id ?: currentDue.medicineName.hashCode().toLong()
                viewModel.markDoseTaken(medId, currentDue.medicineName, currentDue.time)
                logDoseDirectly(context, currentDue.medicineName, currentDue.time, "TAKEN")
                ActiveAlarmStore.removeActiveAlarm(context, currentDue.medicineName, currentDue.time)
                val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notifManager.cancel(alarmNotificationId(currentDue.medicineName, currentDue.time))
                notifManager.cancel(preNotificationId(currentDue.medicineName, currentDue.time))
                notifManager.cancel(AlarmService.NOTIFICATION_ID)
                dueAlarm = ActiveAlarmStore.getActiveAlarm(context)
                isPinUnlocked = true
                currentTab = MedacTab.TODAY
                android.widget.Toast.makeText(context, "${currentDue.medicineName} marked as taken", android.widget.Toast.LENGTH_SHORT).show()
            },
            onSnooze = {
                InAppAlarmPlayer.stop()
                AlarmService.stop(context)
                ActiveAlarmStore.removeActiveAlarm(context, currentDue.medicineName, currentDue.time)
                scheduleSnoozeReminder(context, currentDue.medicineName, currentDue.time, DEFAULT_SNOOZE_DELAY_MINUTES)
                val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notifManager.cancel(alarmNotificationId(currentDue.medicineName, currentDue.time))
                notifManager.cancel(preNotificationId(currentDue.medicineName, currentDue.time))
                notifManager.cancel(AlarmService.NOTIFICATION_ID)
                dueAlarm = ActiveAlarmStore.getActiveAlarm(context)
                isPinUnlocked = true
                currentTab = MedacTab.TODAY
                android.widget.Toast.makeText(context, "Snoozed for $DEFAULT_SNOOZE_DELAY_MINUTES minutes", android.widget.Toast.LENGTH_SHORT).show()
            },
            onGoToHome = {
                InAppAlarmPlayer.stop()
                AlarmService.stop(context)
                ActiveAlarmStore.removeActiveAlarm(context, currentDue.medicineName, currentDue.time)
                val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notifManager.cancel(alarmNotificationId(currentDue.medicineName, currentDue.time))
                notifManager.cancel(preNotificationId(currentDue.medicineName, currentDue.time))
                notifManager.cancel(AlarmService.NOTIFICATION_ID)
                dueAlarm = ActiveAlarmStore.getActiveAlarm(context)
                isPinUnlocked = true
                currentTab = MedacTab.TODAY
            }
        )
        return
    }

    // Welcome / PIN Screen bypassed per user request

    // MedRemind Sub-Screens Overlay
    activeMedRemindScreen?.let { screen ->
        BackHandler { activeMedRemindScreen = null }
        when (screen) {
            "add_medication" -> {
                MedRemindAddMedicationScreen(
                    draft = draft,
                    onDraftChange = { updated ->
                        viewModel.updateDraft(
                            name = updated.name,
                            genericNameAndDose = updated.genericNameAndDose,
                            purpose = updated.purpose,
                            form = updated.form,
                            foodTiming = updated.foodTiming,
                            instruction = updated.instruction,
                            frequency = updated.frequency,
                            duration = updated.duration,
                            startDate = updated.startDate,
                            remindersEnabled = updated.remindersEnabled,
                            refillTrackingEnabled = updated.refillTrackingEnabled,
                            currentSupply = updated.currentSupply,
                            refillThresholdPercent = updated.refillThresholdPercent,
                            notes = updated.notes
                        )
                        viewModel.updateDraftTimes(updated.times)
                    },
                    onSave = {
                        viewModel.saveDraftMedicine()
                        activeMedRemindScreen = null
                        showMedRemindSuccessDialog = true
                    },
                    onCancel = {
                        activeMedRemindScreen = null
                        viewModel.clearDraft()
                    },
                    onScanPhoto = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    isAnalyzing = isAnalyzingPhoto
                )
            }
            "calendar" -> {
                MedRemindCalendarScreen(
                    scheduleForDate = { date -> viewModel.scheduleForDate(date) },
                    doseLogs = doseLogs,
                    onTakeDose = { item, date ->
                        viewModel.markDoseTaken(item.medicineId, item.medicineName, item.time, date = date)
                    },
                    onBack = { activeMedRemindScreen = null }
                )
            }
            "history" -> {
                MedRemindHistoryLogScreen(
                    doseLogs = doseLogs,
                    onClearAll = { viewModel.clearAllHistory() },
                    onBack = { activeMedRemindScreen = null }
                )
            }
            "refill" -> {
                MedRemindRefillTrackerScreen(
                    medicines = medicines,
                    onRecordRefill = { id, units -> viewModel.recordRefill(id, units) },
                    onBack = { activeMedRemindScreen = null }
                )
            }
            "notifications" -> {
                MedRemindNotificationsScreen(
                    schedule = viewModel.todaySchedule(),
                    doseLogs = doseLogs,
                    onBack = { activeMedRemindScreen = null },
                    onTakeClick = { item ->
                        viewModel.markDoseTaken(item.medicineId, item.medicineName, item.time)
                    }
                )
            }
        }
        return
    }

    // Profile sub-screen overlay — single TopAppBar back, no duplicate inner button
    profileSub?.let { sub ->
        BackHandler { profileSub = null }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(sub.replaceFirstChar { it.uppercase() }) },
                    navigationIcon = { IconButton(onClick = { profileSub = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                )
            }
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
                when (sub) {
                    "account" -> AccountScreen(username = (authState as? AuthUiState.LoggedIn)?.username, onBack = { profileSub = null }, onLogoutAll = { authViewModel.logoutAll(context) { profileSub = null } }, viewModel = viewModel, authViewModel = authViewModel)
                    "security" -> SecurityScreen(onBack = { profileSub = null }, authViewModel = authViewModel)
                    "sharing" -> PeopleSharingScreen(onBack = { profileSub = null }, viewModel = viewModel)
                    "alertPrefs" -> AlertPreferencesScreen(onBack = { profileSub = null }, viewModel = viewModel)
                    "scanSettings" -> ScanSettingsScreen(onBack = { profileSub = null }, viewModel = viewModel)
                    "report" -> DoctorReportScreen(onBack = { profileSub = null }, medicines = medicines, logs = doseLogs, viewModel = viewModel)
                    "exports" -> ExportsScreen(viewModel = viewModel)
                    "assistant" -> AssistantScreen(
                        messages = intelligenceMessages,
                        proposals = intelligenceProposals,
                        isLoading = isIntelligenceLoading,
                        error = intelligenceError,
                        onClearError = { viewModel.clearIntelligenceError() },
                        input = assistantInput,
                        onInputChange = { assistantInput = it },
                        onSend = sendAssistant,
                        onConfirmProposal = { id -> viewModel.confirmProposal(id) },
                        onRejectProposal = { id -> viewModel.rejectProposal(id) },
                        draft = scheduleDraft,
                        constraintsInput = draftConstraints,
                        onConstraintsChange = { draftConstraints = it },
                        wakeTime = draftWake,
                        sleepTime = draftSleep,
                        onWakeTimeChange = { draftWake = it },
                        onSleepTimeChange = { draftSleep = it },
                        medicationId = draftMedicationId,
                        onMedicationIdChange = { draftMedicationId = it },
                        onCreateDraft = { viewModel.createScheduleDraft(constraints = draftConstraints.ifBlank{null}, medicationId = draftMedicationId.ifBlank{null}, wakeTime = draftWake, sleepTime = draftSleep) },
                        summary = summary,
                        summaryType = summaryType,
                        onSummaryTypeChange = { summaryType = it },
                        summaryFrom = summaryFrom,
                        summaryTo = summaryTo,
                        onSummaryFromChange = { summaryFrom = it },
                        onSummaryToChange = { summaryTo = it },
                        onFetchSummary = {
                            val fromIso = if (summaryFrom.isBlank()) null else try { java.time.LocalDate.parse(summaryFrom).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString() } catch (_:Exception){ null }
                            val toIso = if (summaryTo.isBlank()) null else try { java.time.LocalDate.parse(summaryTo).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString() } catch (_:Exception){ null }
                            viewModel.fetchSummary(summaryType, fromIso, toIso)
                        },
                        instructionText = instructionInput,
                        onInstructionTextChange = { instructionInput = it },
                        instructionParse = instructionParse,
                        onParseInstruction = { viewModel.parseInstruction(instructionInput) },
                        onDeleteConversation = { viewModel.deleteCurrentConversation() },
                        proposalMsg = proposalMsg,
                        onClearProposalMsg = { viewModel.clearProposalMsg() }
                    )
                    "about" -> AboutScreen(onBack = { profileSub = null })
                    "health" -> HealthDiagnosticsScreen(viewModel = viewModel)
                    "symptoms" -> {
                        LaunchedEffect(Unit) { viewModel.loadSymptomLogs() }
                        val symptomLogs by viewModel.symptomLogs.collectAsState()
                        SymptomTimelineScreen(logs = symptomLogs, onCreateSymptom = { at, note, linked -> viewModel.createSymptomLog(at, note, linked) }, onCorrectSymptom = { id, note, reason -> viewModel.correctSymptomLog(id, note, reason) }, onLoadMore = { viewModel.loadSymptomLogs() })
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Coming soon") }
                }
            }
        }
        return
    }

    detailMedicine?.let { med ->
        BackHandler { detailMedicine = null }
        MedRemindMedicineDetailScreen(
            medicine = med,
            logs = viewModel.logsForMedicine(med.id),
            onBack = { detailMedicine = null },
            onEditSave = { updated ->
                viewModel.updateMedicine(updated)
                detailMedicine = updated
            },
            onTakeNow = {
                val timeNow = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                viewModel.markDoseTaken(med.id, med.name, if (med.times.isNotEmpty()) med.times.first() else timeNow)
            },
            onPause = {
                viewModel.setMedicineStatus(med, "paused")
                val updated = med.copy(status = "paused")
                detailMedicine = updated
            },
            onResume = {
                viewModel.setMedicineStatus(med, "active")
                val updated = med.copy(status = "active")
                detailMedicine = updated
            },
            onArchive = {
                detailMedicine = null
                pendingDeleteMedicine = med
            },
            onRecordRefill = { units ->
                viewModel.recordRefill(med.id, units)
                val updated = med.copy(currentSupply = med.currentSupply + units)
                detailMedicine = updated
            },
            onUploadMedicinePhoto = { showPhotoOptionsDialogFor = it }
        )
        showPhotoOptionsDialogFor?.let { targetMed ->
            UpdateMedicinePhotoDialog(
                medicine = targetMed,
                onDismiss = { showPhotoOptionsDialogFor = null },
                onTakePhoto = {
                    photoUpdateMedicine = targetMed
                    showPhotoOptionsDialogFor = null
                    photoUpdateCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onChooseGallery = {
                    photoUpdateMedicine = targetMed
                    showPhotoOptionsDialogFor = null
                    photoUpdateGalleryLauncher.launch("image/*")
                },
                onRemovePhoto = if (!targetMed.cardImageUri.isNullOrBlank() || targetMed.photoUris.isNotEmpty()) {
                    {
                        val updated = viewModel.removeMedicinePhoto(targetMed.id)
                        if (detailMedicine?.id == targetMed.id && updated != null) {
                            detailMedicine = updated
                        }
                        showPhotoOptionsDialogFor = null
                    }
                } else null
            )
        }
        return
    }

    fun addFlowBack() {
        if (addStep != AddMedicineStep.Capture) addStep = AddMedicineStep.entries[addStep.ordinal - 1]
        else if (viewModel.hasUnsavedDraft()) showCancelDraftConfirm = true else showAddMedicine = false
    }
    BackHandler(enabled = showAddMedicine) { addFlowBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showAddMedicine) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        TextButton(onClick = { addFlowBack() }, modifier = Modifier.heightIn(min = 56.dp)) { Text(if (addStep == AddMedicineStep.Capture) "Cancel" else "Back") }
                    }
                )
            }
        },
        bottomBar = {
            if (!showAddMedicine) {
                NavigationBar(containerColor = CardSurface) {
                    MedacTab.entries.forEach { tab ->
                        NavigationBarItem(selected = currentTab == tab, onClick = { currentTab = tab; showAddMedicine = false; profileSub = null }, icon = tab.icon, label = { Text(tab.label) })
                    }
                }
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            if (showAddMedicine) {
                AiAddMedicineScreen(
                    draft = draft,
                    isIdentifying = aiIdentifying,
                    aiError = aiError,
                    onCapturePhoto = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onDosageChange = { viewModel.updateDraft(genericNameAndDose = it) },
                    onSave = { showSaveConfirm = true },
                    onRetry = { draft.photoUri?.let { viewModel.identifyMedicinePhoto(it, context) } },
                    onFoodTimingChange = { viewModel.updateDraft(foodTiming = it) },
                    onTimeChange = { viewModel.updateDraftTimes(it) },
                    onInstructionChange = { viewModel.updateDraft(instruction = it) },
                    onNameChange = { viewModel.updateDraft(name = it) },
                    onFormChange = { viewModel.updateDraft(form = it) },
                    onPurposeChange = { viewModel.updateDraft(purpose = it) },
                    step = addStep,
                    onStepChange = { addStep = it },
                    uncertainFields = emptyList(),
                    clarifyingQuestions = emptyList(),
                    confidence = draft.aiConfidence,
                    scheduleType = scheduleType,
                    onScheduleTypeChange = { scheduleType = it },
                    timingMode = timingMode,
                    onTimingModeChange = { timingMode = it },
                    onManualAdd = { addStep = AddMedicineStep.Review }
                )
            } else {
                when (currentTab) {
                    MedacTab.TODAY -> {
                        MedRemindDashboardScreen(
                            schedule = viewModel.todaySchedule(),
                            doseLogs = doseLogs,
                            onTakeDose = { item ->
                                viewModel.markDoseTaken(item.medicineId, item.medicineName, item.time)
                            },
                            onNavigateToAddMedication = {
                                viewModel.clearDraft()
                                activeMedRemindScreen = "add_medication"
                            },
                            onNavigateToCalendar = { activeMedRemindScreen = "calendar" },
                            onNavigateToHistory = { activeMedRemindScreen = "history" },
                            onNavigateToRefill = { activeMedRemindScreen = "refill" },
                            onNavigateToNotifications = { activeMedRemindScreen = "notifications" },
                            onNavigateToProfile = { currentTab = MedacTab.PROFILE }
                        )
                    }
                    MedacTab.MEDICINES -> MedRemindMedicinesScreen(
                        medicines = medicines,
                        onAddMedicine = {
                            viewModel.clearDraft()
                            viewModel.clearAiError()
                            activeMedRemindScreen = "add_medication"
                        },
                        onMedicineClick = { detailMedicine = it },
                        onUploadMedicinePhoto = { showPhotoOptionsDialogFor = it }
                    )
                    MedacTab.REMINDERS -> RemindersScreen(
                        activeReminders = viewModel.reminderEntries(active = true),
                        pausedReminders = viewModel.reminderEntries(active = false),
                        onToggleReminder = { entry, enabled -> viewModel.setReminderEnabled(entry, enabled) },
                        onEditReminder = { entry -> reminderEditRequest = entry },
                        quietStart = quietHours.first,
                        quietEnd = quietHours.second,
                        onQuietChange = { start, end -> viewModel.setQuietHours(start, end) },
                        discreet = discreetMode,
                        onDiscreetChange = { discreetMode = it }
                    )
                    MedacTab.PROFILE -> ProfileScreen(
                        medicineCount = medicines.size,
                        activeReminderCount = viewModel.reminderEntries(active = true).size,
                        username = (authState as? AuthUiState.LoggedIn)?.username,
                        onLogout = { authViewModel.logout(context) },
                        onNavigate = { profileSub = it }
                    )
                }
            }
        }
    }

    showPhotoOptionsDialogFor?.let { targetMed ->
        UpdateMedicinePhotoDialog(
            medicine = targetMed,
            onDismiss = { showPhotoOptionsDialogFor = null },
            onTakePhoto = {
                photoUpdateMedicine = targetMed
                showPhotoOptionsDialogFor = null
                photoUpdateCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onChooseGallery = {
                photoUpdateMedicine = targetMed
                showPhotoOptionsDialogFor = null
                photoUpdateGalleryLauncher.launch("image/*")
            },
            onRemovePhoto = if (!targetMed.cardImageUri.isNullOrBlank() || targetMed.photoUris.isNotEmpty()) {
                {
                    val updated = viewModel.removeMedicinePhoto(targetMed.id)
                    if (detailMedicine?.id == targetMed.id && updated != null) {
                        detailMedicine = updated
                    }
                    showPhotoOptionsDialogFor = null
                }
            } else null
        )
    }

    if (showMedRemindSuccessDialog) {
        MedRemindSuccessDialog(onDismiss = { showMedRemindSuccessDialog = false })
    }
}

private fun createPhotoUri(context: Context): Uri? {
    return try {
        val dir = File(context.filesDir, "medicine_photos").apply { mkdirs() }
        val file = File(dir, "medicine_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (_: Exception) { null }
}
