package me.zippert.dialoglite.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * O endereco base e digitado pelo usuario em runtime (sao duas instancias
 * distintas, uma por pessoa, atendidas pelo mesmo APK), entao o Retrofit sobe
 * com uma base placeholder e este interceptor reescreve host/porta/esquema/
 * prefixo a cada chamada.
 */
class BaseUrlInterceptor : Interceptor {

    private val current = AtomicReference<HttpUrl?>(null)

    fun setBaseUrl(raw: String?) {
        current.set(raw?.let { parse(it) })
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val base = current.get() ?: throw NoBaseUrlException()
        // Segunda linha de defesa: a tela de configuracao ja recusa cleartext
        // pra destino nao-privado, mas um valor gravado por uma versao antiga
        // (ou editado por fora) nao pode escapar por aqui.
        if (!CleartextPolicy.isAllowed(base)) throw InsecureBaseUrlException(base.host)
        val request = chain.request()

        // O path do Retrofit e relativo ("api/history"); ele foi resolvido
        // contra o placeholder, entao aqui reaproveitamos so a parte final.
        val relative = request.url.encodedPath.removePrefix(PLACEHOLDER_PATH).trimStart('/')

        val newUrl = base.newBuilder()
            .addPathSegments(relative)
            .query(request.url.query)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }

    companion object {
        /** Base fake exigida pelo Retrofit; nunca sai na rede. */
        const val PLACEHOLDER = "http://dialoglite.invalid/placeholder/"
        private const val PLACEHOLDER_PATH = "/placeholder"

        /**
         * Normaliza o que o usuario digitou. Aceita `host`, `host:8000`,
         * `http://host`, `https://host/dialog` — tudo vira uma HttpUrl com
         * barra final (senao `addPathSegments` come o ultimo segmento).
         */
        fun parse(raw: String): HttpUrl? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            val withSlash = if (withScheme.endsWith("/")) withScheme else "$withScheme/"
            return withSlash.toHttpUrlOrNull()
        }

        /**
         * Valida o que o usuario digitou. As duas formas convivem na mesma
         * configuracao: `https://<host>` e `http://<ip>[:porta]` da mesh.
         */
        fun validate(raw: String): BaseUrlValidation {
            if (raw.isBlank()) return BaseUrlValidation.Empty
            val url = parse(raw) ?: return BaseUrlValidation.Malformed
            if (url.host.isBlank()) return BaseUrlValidation.Malformed
            if (!CleartextPolicy.isAllowed(url)) return BaseUrlValidation.CleartextNotAllowed(url.host)
            return BaseUrlValidation.Valid(url, cleartext = !url.isHttps)
        }
    }
}

sealed interface BaseUrlValidation {
    data object Empty : BaseUrlValidation
    data object Malformed : BaseUrlValidation
    /** HTTP em claro para destino que nao e IP privado/mesh. */
    data class CleartextNotAllowed(val host: String) : BaseUrlValidation
    data class Valid(val url: HttpUrl, val cleartext: Boolean) : BaseUrlValidation
}

/** Nao ha endereco configurado ainda — estado normal no primeiro uso. */
class NoBaseUrlException : IOException("Endereço do servidor não configurado")
