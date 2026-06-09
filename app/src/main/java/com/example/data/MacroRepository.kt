package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class MacroRepository(private val db: AppDatabase) {

    private fun getStartOfDayMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    val todaysMeals: Flow<List<Meal>> = db.mealDao().getMealsForDate(getStartOfDayMillis())
    
    val goal: Flow<Goal?> = db.goalDao().getGoal()

    suspend fun addMeal(meal: Meal) {
        db.mealDao().insertMeal(meal)
    }

    suspend fun deleteMeal(meal: Meal) {
        db.mealDao().deleteMeal(meal)
    }

    suspend fun updateGoal(goal: Goal) {
        db.goalDao().updateGoal(goal)
    }
}
