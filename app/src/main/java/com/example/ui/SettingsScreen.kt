package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Goal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val goal by viewModel.goalState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val healthConnectConnected by viewModel.healthConnectConnected.collectAsStateWithLifecycle()
    
    val healthPermissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.checkHealthConnect()
    }
    
    LaunchedEffect(Unit) {
        viewModel.checkHealthConnect()
    }
    
    var cals by remember(goal) { mutableStateOf(goal.targetCalories.toString()) }
    var protein by remember(goal) { mutableStateOf(goal.targetProtein.toString()) }
    var carbs by remember(goal) { mutableStateOf(goal.targetCarbs.toString()) }
    var fat by remember(goal) { mutableStateOf(goal.targetFat.toString()) }
    
    var remindersEnabled by remember(goal) { mutableStateOf(goal.remindersEnabled) }
    var reminderHour by remember(goal) { mutableStateOf(goal.reminderHour) }
    var reminderMinute by remember(goal) { mutableStateOf(goal.reminderMinute) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Daily Goals & Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Macronutrient Targets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(value = cals, onValueChange = { cals = it }, label = { Text("Daily Calories") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Protein (g)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("Carbs (g)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Fat (g)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable Daily Meal Log Reminder", modifier = Modifier.weight(1f))
                Switch(checked = remindersEnabled, onCheckedChange = { remindersEnabled = it })
            }
            
            if (remindersEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reminderHour.toString(), 
                        onValueChange = { reminderHour = it.toIntOrNull() ?: reminderHour }, 
                        label = { Text("Hour (0-23)") }, 
                        modifier = Modifier.weight(1f), 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = reminderMinute.toString(), 
                        onValueChange = { reminderMinute = it.toIntOrNull() ?: reminderMinute }, 
                        label = { Text("Minute (0-59)") }, 
                        modifier = Modifier.weight(1f), 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.updateGoal(
                        calories = cals.toIntOrNull() ?: 2000,
                        protein = protein.toIntOrNull() ?: 150,
                        carbs = carbs.toIntOrNull() ?: 200,
                        fat = fat.toIntOrNull() ?: 65,
                        remindersEnabled = remindersEnabled,
                        reminderHour = reminderHour,
                        reminderMinute = reminderMinute
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Accounts & Integrations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // Auth
            if (authState == null) {
                Button(onClick = { viewModel.signInWithGoogle(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google")
                }
                if (authError != null) {
                    Text(authError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Signed in as: $authState", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { viewModel.signOut() }) {
                        Text("Sign Out")
                    }
                }
            }

            // Health Connect
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Health Connect Sync")
                if (healthConnectConnected) {
                    Text("Connected", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else {
                    Button(onClick = { healthPermissionsLauncher.launch(viewModel.getHealthConnectPermissions()) }) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
