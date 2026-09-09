package me.zippert.dialoglite.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        // Campos null nao sao emitidos: em /day/bulk_update, "ausente" significa
        // "nao mexe nisso" — mandar null explicito mudaria a semantica.
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    fun create(baseUrlInterceptor: BaseUrlInterceptor): DiaLogApi {
        val client = OkHttpClient.Builder()
            // Timeouts curtos de proposito: fora da mesh o endpoint fica
            // inalcancavel o tempo todo, e isso e estado normal — nao vale
            // segurar a UI por 30s pra descobrir.
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(baseUrlInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(BaseUrlInterceptor.PLACEHOLDER)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DiaLogApi::class.java)
    }
}
