package com.yaahua.vcam.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yaahua.vcam.ConfigManager
import com.yaahua.vcam.NotificationService
import com.yaahua.vcam.R
import com.yaahua.vcam.ui.theme.VCAMTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissionsStatus()
    }

    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionsStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val configManager = ConfigManager()
        if (configManager.migrateIfNeeded()) {
            Toast.makeText(this, getString(R.string.config_migrated), Toast.LENGTH_LONG).show()
        }

        if (configManager.getBoolean(ConfigManager.KEY_NOTIFICATION_CONTROL_ENABLED, false)) {
            try {
                startService(Intent(this, NotificationService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            VCAMTheme {
                MainApp()
            }
        }
    }

    @Composable
    fun MainApp() {
        val navController = rememberNavController()
        val items = listOf(Screen.Home, Screen.Settings)

        Scaffold(
            topBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                when (navBackStackEntry?.destination?.route) {
                    Screen.Home.route -> {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                    else -> {}
                }
            },
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleResId)) },
                            label = { Text(stringResource(screen.titleResId)) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(150)) },
                exitTransition = { fadeOut(animationSpec = tween(150)) },
                popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                popExitTransition = { fadeOut(animationSpec = tween(150)) }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        mainViewModel = mainViewModel,
                        onPermissionRequest = { checkAndRequestPermissions() }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = mainViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsStatus()
        mainViewModel.loadConfig()

        val configManager = ConfigManager()
        if (configManager.getBoolean(ConfigManager.KEY_OVERLAY_CONTROL_ENABLED, false)
            && Settings.canDrawOverlays(this)
        ) {
            try {
                startService(Intent(this, com.yaahua.vcam.NotificationService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkPermissionsStatus() {
        mainViewModel.updatePermissionStatus(hasStoragePermission())
    }

    private fun checkAndRequestPermissions() {
        if (!hasStoragePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.addCategory("android.intent.category.DEFAULT")
                        intent.data = Uri.parse("package:$packageName")
                        manageExternalStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        val intent = Intent()
                        intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                        manageExternalStorageLauncher.launch(intent)
                    }
                }
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                )
            }
        }
        
        // Notification permission (optional, only on Android13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return android.os.Environment.isExternalStorageManager()
        } else {
            return arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE).all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun initDirectory() {
        val cameraDir = File(android.os.Environment.getExternalStorageDirectory().absolutePath + "/DCIM/Camera1/")
        if (!cameraDir.exists()) cameraDir.mkdirs()
    }
}
