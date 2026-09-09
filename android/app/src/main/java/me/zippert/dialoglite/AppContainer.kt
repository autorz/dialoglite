package me.zippert.dialoglite

import android.content.Context
import me.zippert.dialoglite.data.DayRepository
import me.zippert.dialoglite.data.local.DiaLogDatabase
import me.zippert.dialoglite.data.prefs.AppPreferences
import me.zippert.dialoglite.data.remote.ApiFactory
import me.zippert.dialoglite.data.remote.BaseUrlInterceptor

/**
 * DI manual. O grafo tem quatro objetos e um escopo so (o processo); Hilt
 * traria processamento de anotacao e tempo de build sem nada em troca aqui.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database by lazy { DiaLogDatabase.build(appContext) }
    private val prefs by lazy { AppPreferences(appContext) }
    private val baseUrlInterceptor by lazy { BaseUrlInterceptor() }
    private val api by lazy { ApiFactory.create(baseUrlInterceptor) }

    val repository: DayRepository by lazy {
        DayRepository(
            dao = database.dao(),
            prefs = prefs,
            api = api,
            baseUrlInterceptor = baseUrlInterceptor,
        )
    }
}
