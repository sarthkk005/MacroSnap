package com.example.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val isLoading by viewModel.isLoading.collectAsState()
    val recognizedFood by viewModel.recognizedFood.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            selectedImageBitmap = bitmap
            viewModel.analyzeFood(null, bitmap)
        }
    }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.analyzeFood("Barcode: ${result.contents}", null)
        }
    }

    val selectImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            selectedImageBitmap = bitmap
            viewModel.analyzeFood(null, bitmap)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add Food") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("Search Food or Enter Manually") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.analyzeFood(textInput, null) }),
                trailingIcon = {
                    IconButton(onClick = { viewModel.analyzeFood(textInput, null) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search AI")
                    }
                }
            )
            
            Text("OR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { takePictureLauncher.launch() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Camera")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pic")
                }
                Button(onClick = { selectImageLauncher.launch("image/*") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Filled.Image, contentDescription = "Gallery")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gal")
                }
                Button(onClick = { 
                    val options = ScanOptions()
                    options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    options.setPrompt("Scan a barcode")
                    options.setCameraId(0)
                    options.setBeepEnabled(false)
                    options.setBarcodeImageEnabled(true)
                    barcodeLauncher.launch(options)
                }, modifier = Modifier.weight(1.5f), contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Barcode")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Barcode")
                }
            }

            if (isLoading) {
                CircularProgressIndicator()
                Text("Analyzing food with Gemini AI...")
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            selectedImageBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "Selected Food", modifier = Modifier.size(200.dp))
            }

            recognizedFood?.let { food ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AI Recognition Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        var name by remember { mutableStateOf(food.name) }
                        var cal by remember { mutableStateOf(food.calories.toString()) }
                        var pro by remember { mutableStateOf(food.protein.toString()) }
                        var carb by remember { mutableStateOf(food.carbs.toString()) }
                        var fat by remember { mutableStateOf(food.fat.toString()) }

                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = cal, onValueChange = { cal = it }, label = { Text("Calories") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = pro, onValueChange = { pro = it }, label = { Text("Protein (g)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = carb, onValueChange = { carb = it }, label = { Text("Carbs (g)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Fat (g)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }

                        Button(
                            onClick = {
                                viewModel.saveMeal(
                                    name = name,
                                    calories = cal.toIntOrNull() ?: 0,
                                    protein = pro.toIntOrNull() ?: 0,
                                    carbs = carb.toIntOrNull() ?: 0,
                                    fat = fat.toIntOrNull() ?: 0
                                )
                                textInput = ""
                                selectedImageBitmap = null
                                navController.navigateUp()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Log Meal")
                        }
                    }
                }
            }
        }
    }
}
