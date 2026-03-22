package com.falldetection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.falldetection.service.SensorMonitoringService
import com.falldetection.ui.AlertScreen
import com.falldetection.ui.EmergencyContactsScreen
import com.falldetection.ui.HomeScreen
import com.falldetection.ui.LogsScreen
import com.falldetection.viewmodel.AlertViewModel
import com.falldetection.viewmodel.ContactsViewModel
import com.falldetection.viewmodel.HomeScreenViewModel
import com.falldetection.viewmodel.LogsViewModel
import android.provider.ContactsContract
import android.net.Uri
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {

    private lateinit var sensorService: SensorMonitoringService
    private var isServiceBound = false

    private val homeScreenViewModel: HomeScreenViewModel by lazy {
        ViewModelProvider(this).get(HomeScreenViewModel::class.java)
    }

    private val alertViewModel: AlertViewModel by lazy {
        ViewModelProvider(this).get(AlertViewModel::class.java)
    }

    private val logsViewModel: LogsViewModel by lazy {
        ViewModelProvider(this).get(LogsViewModel::class.java)
    }

    private val contactsViewModel: ContactsViewModel by lazy {
        ViewModelProvider(this).get(ContactsViewModel::class.java)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorMonitoringService.LocalBinder
            sensorService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
        }
    }

    private val fallDetectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SensorMonitoringService.ACTION_FALL_DETECTED) {
                val event = intent.getParcelableExtra("event") as? com.falldetection.model.FallDetectionEvent
                val confidence = intent.getFloatExtra("confidence", 0f)
                val quantumConfidence = intent.getFloatExtra("quantumConfidence", 0f)
                if (event != null) {
                    alertViewModel.setFallAlert(event, confidence, quantumConfidence)
                    homeScreenViewModel.setFallDetected()
                }
            }
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startSensorService()
        }
    }

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        contactUri?.let { uri ->
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                    
                    val hasPhone = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
                    if (hasPhone) {
                        val pCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        pCursor?.use { pc ->
                            if (pc.moveToFirst()) {
                                val phone = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                contactsViewModel.addContact(name, phone)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FallDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(
                        homeScreenViewModel,
                        alertViewModel,
                        logsViewModel,
                        contactsViewModel,
                        onStartClicked = { requestPermissionsAndStart() },
                        onStopClicked = { stopSensorService() },
                        onImportContact = { pickContactLauncher.launch(null) }
                    )
                }
            }
        }

        // Request permissions
        requestPermissions()

        // Register broadcast receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                fallDetectionReceiver,
                IntentFilter(SensorMonitoringService.ACTION_FALL_DETECTED),
                Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                fallDetectionReceiver,
                IntentFilter(SensorMonitoringService.ACTION_FALL_DETECTED)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SensorMonitoringService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startSensorService()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(fallDetectionReceiver)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            if (alertViewModel.alertState.value.isFallDetected) {
                alertViewModel.dismissAlert()
                homeScreenViewModel.setSafe()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.INTERNET,
            Manifest.permission.VIBRATE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun requestPermissionsAndStart() {
        requestPermissions()
    }

    private fun startSensorService() {
        val intent = Intent(this, SensorMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        homeScreenViewModel.setMonitoring(true)
    }

    private fun stopSensorService() {
        val intent = Intent(this, SensorMonitoringService::class.java)
        stopService(intent)
        homeScreenViewModel.setMonitoring(false)
    }
}

@Composable
fun MainApp(
    homeViewModel: HomeScreenViewModel,
    alertViewModel: AlertViewModel,
    logsViewModel: LogsViewModel,
    contactsViewModel: ContactsViewModel,
    onStartClicked: () -> Unit,
    onStopClicked: () -> Unit,
    onImportContact: () -> Unit
) {
    val navController = rememberNavController()
    val showAlert = remember { mutableStateOf(false) }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(homeViewModel)
        }
        composable("contacts") {
            EmergencyContactsScreen(contactsViewModel, onImportContact)
        }
        composable("logs") {
            LogsScreen(logsViewModel)
        }
    }

    val alertState = alertViewModel.alertState.collectAsState()
    if (alertState.value.isFallDetected) {
        AlertScreen(alertViewModel, onDismiss = {
            homeViewModel.setSafe()
            navController.navigate("home")
        })
    }
}

@Composable
fun FallDetectionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}
