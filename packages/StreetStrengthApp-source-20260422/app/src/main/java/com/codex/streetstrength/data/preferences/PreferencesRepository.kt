package com.codex.streetstrength.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "street_strength_prefs")

data class UserPreferences(
    val defaultRestSeconds: Int = 90,
    val presetRestPrimary: Int = 60,
    val presetRestSecondary: Int = 90,
    val keepScreenOn: Boolean = true,
    val recentLoadKg: Double = 0.0,
    val activeGoalId: Long? = null,
    val activeCycleId: Long? = null,
    val favoriteTemplateIds: Set<Long> = emptySet(),
)

class PreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val defaultRestSeconds = intPreferencesKey("default_rest_seconds")
        val presetRestPrimary = intPreferencesKey("preset_rest_primary")
        val presetRestSecondary = intPreferencesKey("preset_rest_secondary")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val recentLoadKg = doublePreferencesKey("recent_load_kg")
        val activeGoalId = longPreferencesKey("active_goal_id")
        val activeCycleId = longPreferencesKey("active_cycle_id")
        val favoriteTemplateIds = stringSetPreferencesKey("favorite_template_ids")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            defaultRestSeconds = prefs[Keys.defaultRestSeconds] ?: 90,
            presetRestPrimary = prefs[Keys.presetRestPrimary] ?: 60,
            presetRestSecondary = prefs[Keys.presetRestSecondary] ?: 90,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            recentLoadKg = prefs[Keys.recentLoadKg] ?: 0.0,
            activeGoalId = prefs[Keys.activeGoalId],
            activeCycleId = prefs[Keys.activeCycleId],
            favoriteTemplateIds = prefs[Keys.favoriteTemplateIds]
                .orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet(),
        )
    }

    suspend fun setRecentLoadKg(value: Double) {
        context.dataStore.edit { it[Keys.recentLoadKg] = value }
    }

    suspend fun setActiveGoalId(goalId: Long) {
        context.dataStore.edit { it[Keys.activeGoalId] = goalId }
    }

    suspend fun setActiveCycleId(cycleId: Long) {
        context.dataStore.edit { it[Keys.activeCycleId] = cycleId }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.keepScreenOn] = enabled }
    }

    suspend fun replacePreferences(preferences: UserPreferences) {
        context.dataStore.edit { prefs ->
            prefs[Keys.defaultRestSeconds] = preferences.defaultRestSeconds
            prefs[Keys.presetRestPrimary] = preferences.presetRestPrimary
            prefs[Keys.presetRestSecondary] = preferences.presetRestSecondary
            prefs[Keys.keepScreenOn] = preferences.keepScreenOn
            prefs[Keys.recentLoadKg] = preferences.recentLoadKg
            preferences.activeGoalId?.let {
                prefs[Keys.activeGoalId] = it
            } ?: prefs.remove(Keys.activeGoalId)
            preferences.activeCycleId?.let {
                prefs[Keys.activeCycleId] = it
            } ?: prefs.remove(Keys.activeCycleId)
            prefs[Keys.favoriteTemplateIds] = preferences.favoriteTemplateIds.map { it.toString() }.toSet()
        }
    }

    suspend fun toggleFavoriteTemplate(templateId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.favoriteTemplateIds].orEmpty()
            val value = templateId.toString()
            prefs[Keys.favoriteTemplateIds] = if (value in current) {
                current - value
            } else {
                current + value
            }
        }
    }
}
