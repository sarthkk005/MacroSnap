package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.AppDatabase
import com.example.data.Goal
import com.example.data.MacroRepository
import com.example.data.Meal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Calendar
import com.example.auth.AuthManager
import com.example.health.HealthConnectManager

data class RecognizedFood(
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MacroRepository
    private val healthConnectManager = HealthConnectManager(application)
    
    val authState = AuthManager.userState
    val authError = AuthManager.errorState
    val healthConnectConnected = healthConnectManager.isConnected

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MacroRepository(db)
        
        // Initialize default goal if null
        viewModelScope.launch {
            repository.goal.collect { currentGoal ->
                if (currentGoal == null) {
                    repository.updateGoal(Goal())
                }
                _goalState.value = currentGoal ?: Goal()
            }
        }
        
        // Streak Logic Verification
        viewModelScope.launch {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            repository.goal.collect { currentGoal ->
                if (currentGoal != null) {
                    val lastActiveDate = currentGoal.lastActiveDate
                    val cal = Calendar.getInstance().apply { timeInMillis = lastActiveDate }
                    val lastActiveDay = cal.get(Calendar.DAY_OF_YEAR)
                    
                    if (today - lastActiveDay > 1 && lastActiveDate > 0) {
                        // missed a day, reset streak
                        repository.updateGoal(currentGoal.copy(streakDays = 0))
                    }
                }
            }
        }
    }

    val todaysMeals: StateFlow<List<Meal>> = repository.todaysMeals.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _goalState = MutableStateFlow(Goal())
    val goalState: StateFlow<Goal> = _goalState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _recognizedFood = MutableStateFlow<RecognizedFood?>(null)
    val recognizedFood: StateFlow<RecognizedFood?> = _recognizedFood

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    private val _aiSuggestions = MutableStateFlow<String?>(null)
    val aiSuggestions: StateFlow<String?> = _aiSuggestions

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearRecognizedFood() {
        _recognizedFood.value = null
    }

    fun saveMeal(name: String, calories: Int, protein: Int, carbs: Int, fat: Int, imageUri: String? = null) {
        viewModelScope.launch {
            val nextMealId = System.currentTimeMillis()
            val meal = Meal(
                name = name,
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                photoUri = imageUri
            )
            repository.addMeal(meal)
            
            // Sync to Health Connect
            if (healthConnectManager.isConnected.value) {
                healthConnectManager.writeMealInfo(meal)
            }
            
            // Update Activity/Streak
            val g = _goalState.value
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val cal = Calendar.getInstance().apply { timeInMillis = g.lastActiveDate }
            val lastActiveDay = cal.get(Calendar.DAY_OF_YEAR)
            
            if (lastActiveDay != today) {
                repository.updateGoal(g.copy(
                    streakDays = g.streakDays + 1,
                    lastActiveDate = System.currentTimeMillis()
                ))
            } else if (g.lastActiveDate == 0L) {
                repository.updateGoal(g.copy(
                    streakDays = 1,
                    lastActiveDate = System.currentTimeMillis()
                ))
            }
            
            clearRecognizedFood()
        }
    }

    fun deleteMeal(meal: Meal) {
        viewModelScope.launch {
            repository.deleteMeal(meal)
        }
    }

    fun updateGoal(calories: Int, protein: Int, carbs: Int, fat: Int, remindersEnabled: Boolean, reminderHour: Int, reminderMinute: Int) {
        viewModelScope.launch {
            repository.updateGoal(
                _goalState.value.copy(
                    targetCalories = calories,
                    targetProtein = protein,
                    targetCarbs = carbs,
                    targetFat = fat,
                    remindersEnabled = remindersEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute
                )
            )
        }
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun analyzeFood(text: String?, bitmap: Bitmap?) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                
                val parts = mutableListOf<Part>()
                val isBarcode = text?.startsWith("Barcode:") == true
                val promptText = if (isBarcode) {
                    "Identify the food product with this barcode: $text and provide its nutritional information per serving. Return a JSON object with strictly these keys: \"name\" (String), \"calories\" (Int), \"protein\" (Int), \"carbs\" (Int), \"fat\" (Int). Do not include any other markdown formatting, just raw JSON data."
                } else {
                    "Analyze this food item (text: \"${text ?: ""}\" and/or image). Return a JSON object with strictly these keys: \"name\" (String), \"calories\" (Int), \"protein\" (Int), \"carbs\" (Int), \"fat\" (Int). Do not include any other markdown formatting, just raw JSON data."
                }
                parts.add(Part(text = promptText))
                
                if (bitmap != null) {
                    parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64())))
                }

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = parts))
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    val cleanJson = responseText.trim()
                    val startIndex = cleanJson.indexOf('{')
                    val endIndex = cleanJson.lastIndexOf('}')
                    if (startIndex != -1 && endIndex != -1) {
                        val jsonStr = cleanJson.substring(startIndex, endIndex + 1)
                        val json = JSONObject(jsonStr)
                        _recognizedFood.value = RecognizedFood(
                            name = json.optString("name", "Unknown Food"),
                            calories = json.optInt("calories", 0),
                            protein = json.optInt("protein", 0),
                            carbs = json.optInt("carbs", 0),
                            fat = json.optInt("fat", 0)
                        )
                    } else {
                        _errorMessage.value = "Failed to parse API response."
                    }
                } else {
                    _errorMessage.value = "Failed to analyze food."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateSuggestions(currentCalories: Int, currentProtein: Int, currentCarbs: Int, currentFat: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val goal = _goalState.value
                val remCals = goal.targetCalories - currentCalories
                val prompt = "My daily goal: ${goal.targetCalories} kcal. Current intake: $currentCalories kcal, $currentProtein g Protein, $currentCarbs g Carbs, $currentFat g Fat.  Suggest 2-3 brief meal ideas to hit the remaining $remCals calories."
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                _aiSuggestions.value = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No suggestions found."
                
            } catch (e: Exception) {
                _aiSuggestions.value = "Error generating suggestions."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithGoogle(context: android.content.Context) {
        viewModelScope.launch {
            AuthManager.signIn(context)
        }
    }

    fun signOut() {
        AuthManager.signOut()
    }

    fun getHealthConnectPermissions() = healthConnectManager.permissions

    fun checkHealthConnect() {
        viewModelScope.launch {
            healthConnectManager.checkPermissions()
        }
    }
}
