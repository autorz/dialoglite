package me.zippert.dialoglite.data.prefs

import kotlinx.coroutines.flow.Flow

/**
 * Existe pra que a sequencia de sync (ver [me.zippert.dialoglite.data.DayRepository])
 * possa ser testada em JVM puro, sem Android — e ela e justamente a parte que
 * nao pode regredir.
 */
interface PreferencesSource {
    val baseUrl: Flow<String?>
    suspend fun setBaseUrl(value: String)
}
