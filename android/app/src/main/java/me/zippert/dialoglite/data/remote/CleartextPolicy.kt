package me.zippert.dialoglite.data.remote

import okhttp3.HttpUrl
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Onde o app aceita falar HTTP em claro.
 *
 * Contexto: uma das instancias vai ser servida como `http://100.70.x.y` (IP da
 * mesh netbird, sem TLS — o trafego ja vai cifrado por dentro do WireGuard); a
 * outra e `https://<host>`. O mesmo APK atende as duas.
 *
 * POR QUE A REGRA MORA AQUI, E NAO SO NO network_security_config:
 * o `networkSecurityConfig` do Android casa por **hostname exato ou sufixo de
 * dominio** — ele nao entende faixa CIDR. Como o endereco e digitado pelo
 * usuario em runtime, nao da pra listar `100.70.0.0/16` la em build time, e a
 * unica alternativa declarativa seria liberar cleartext geral. Entao o XML
 * libera o transporte e QUEM ESTREITA O ESCOPO E ESTE OBJETO: cleartext so
 * para **literal de IP privado ou CGNAT**, nunca para nome nem para IP publico.
 *
 * A checagem roda em dois pontos de proposito — na tela de configuracao (pra
 * dar erro legivel) e no interceptor (pra que um valor gravado por uma versao
 * antiga, ou editado por fora, nao escape).
 */
object CleartextPolicy {

    /** `true` se a URL pode ser usada: ou e HTTPS, ou e cleartext pra IP privado. */
    fun isAllowed(url: HttpUrl): Boolean = !url.isCleartext() || isPrivateLiteral(url.host)

    fun HttpUrl.isCleartext(): Boolean = !isHttps

    /**
     * Faixas aceitas em cleartext. `100.64.0.0/10` (CGNAT) e a que cobre a mesh
     * netbird `100.70.0.0/16`; as demais cobrem uso na LAN de casa e loopback.
     *
     * Precisa ser LITERAL: um hostname nao serve, mesmo que resolva pra IP
     * privado — o DNS pode mudar e a checagem viraria decorativa.
     */
    fun isPrivateLiteral(host: String): Boolean {
        val address = parseLiteral(host) ?: return false
        return when (address) {
            is Inet4Address -> address.isIpv4Private()
            is Inet6Address -> address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isUniqueLocal()
            else -> false
        }
    }

    /**
     * `InetAddress.getByName` faria consulta DNS para um nome. Só queremos
     * literais, então filtramos antes pelo formato.
     */
    private fun parseLiteral(host: String): InetAddress? {
        val candidate = host.trim().removeSurrounding("[", "]")
        if (candidate.isEmpty()) return null
        val looksLikeLiteral = candidate.contains(':') ||
            candidate.all { it.isDigit() || it == '.' }
        if (!looksLikeLiteral) return null
        return runCatching { InetAddress.getByName(candidate) }.getOrNull()
    }

    private fun Inet4Address.isIpv4Private(): Boolean {
        val b = address.map { it.toInt() and 0xFF }
        return when {
            b[0] == 10 -> true                                   // 10.0.0.0/8
            b[0] == 127 -> true                                  // 127.0.0.0/8
            b[0] == 172 && b[1] in 16..31 -> true                // 172.16.0.0/12
            b[0] == 192 && b[1] == 168 -> true                   // 192.168.0.0/16
            b[0] == 169 && b[1] == 254 -> true                   // 169.254.0.0/16
            b[0] == 100 && b[1] in 64..127 -> true               // 100.64.0.0/10 (CGNAT / netbird)
            else -> false
        }
    }

    /** fc00::/7 — Unique Local Address. */
    private fun Inet6Address.isUniqueLocal(): Boolean = (address[0].toInt() and 0xFE) == 0xFC
}

/**
 * O endereco gravado usa HTTP em claro para um destino que nao e IP privado.
 * E erro de configuracao, nao de rede — nao adianta ficar tentando.
 */
class InsecureBaseUrlException(host: String) : IOException(
    "HTTP sem TLS só é permitido para IP privado ou da mesh; \"$host\" não é."
)
