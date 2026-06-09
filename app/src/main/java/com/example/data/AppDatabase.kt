package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val photoUri: String? = null
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: Int = 1,
    val targetCalories: Int = 2000,
    val targetProtein: Int = 150,
    val targetCarbs: Int = 200,
    val targetFat: Int = 65,
    val streakDays: Int = 0,
    val lastActiveDate: Long = 0,
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 12,
    val reminderMinute: Int = 0
)

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getMealsForDate(startOfDay: Long): Flow<List<Meal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: Meal)

    @Delete
    suspend fun deleteMeal(meal: Meal)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE id = 1")
    fun getGoal(): Flow<Goal?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateGoal(goal: Goal)
}

@Database(entities = [Meal::class, Goal::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macrosnap_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
