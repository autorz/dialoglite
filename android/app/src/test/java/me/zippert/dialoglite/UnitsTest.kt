package me.zippert.dialoglite

import me.zippert.dialoglite.data.BulkErrorPolicy
import me.zippert.dialoglite.data.remote.BaseUrlInterceptor
import me.zippert.dialoglite.data.remote.BaseUrlValidation
import me.zippert.dialoglite.data.remote.CleartextPolicy
import me.zippert.dialoglite.util.TimeFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatsTest {

    @Test
    fun `corta os segundos que o servidor devolve`() {
        assertEquals("09:00", TimeFormats.toHourMinute("09:00:00"))
        assertEquals("23:59", TimeFormats.toHourMinute("23:59:59"))
    }

    @Test
    fun `idempotente em HH MM`() {
        assertEquals("09:00", TimeFormats.toHourMinute("09:00"))
    }

    @Test
    fun `vazio e nulo viram null`() {
        assertNull(TimeFormats.toHourMinute(null))
        assertNull(TimeFormats.toHourMinute(""))
        assertNull(TimeFormats.toHourMinute("   "))
    }

    @Test
    fun `valida HH MM`() {
        assertTrue(TimeFormats.isValidHourMinute("00:00"))
        assertTrue(TimeFormats.isValidHourMinute("23:59"))
        assertFalse(TimeFormats.isValidHourMinute("24:00"))
        assertFalse(TimeFormats.isValidHourMinute("09:60"))
        assertFalse(TimeFormats.isValidHourMinute("9:00"))
        assertFalse(TimeFormats.isValidHourMinute("09:00:00"))
    }
}

class BulkErrorPolicyTest {

    @Test
    fun `mais de 2 periodos e permanente`() {
        assertTrue(
            BulkErrorPolicy.isPermanent("mais de 2 períodos; horários não alterados — use edição avançada")
        )
    }

    @Test
    fun `data invalida e horario invalido sao permanentes`() {
        assertTrue(BulkErrorPolicy.isPermanent("data inválida"))
        assertTrue(BulkErrorPolicy.isPermanent("erro nos horários: time data '9' does not match"))
    }

    @Test
    fun `dia nao encontrado e transitorio`() {
        // Depois do GET api-history (auto_populate_days) costuma resolver;
        // bloquear na primeira tentativa perderia o lancamento.
        assertFalse(BulkErrorPolicy.isPermanent("dia não encontrado"))
    }
}

class BaseUrlParsingTest {

    @Test
    fun `assume http quando falta esquema`() {
        assertEquals("http://servidor.invalid/", BaseUrlInterceptor.parse("servidor.invalid").toString())
    }

    @Test
    fun `preserva porta, https e prefixo de caminho`() {
        assertEquals("http://10.1.2.3:8000/", BaseUrlInterceptor.parse("10.1.2.3:8000").toString())
        assertEquals("https://exemplo.invalid/", BaseUrlInterceptor.parse("https://exemplo.invalid").toString())
        assertEquals("https://exemplo.invalid/ponto/", BaseUrlInterceptor.parse("https://exemplo.invalid/ponto").toString())
    }

    @Test
    fun `vazio vira null`() {
        assertNull(BaseUrlInterceptor.parse("   "))
    }
}

/**
 * O `networkSecurityConfig` nao entende CIDR, entao o escopo real do cleartext
 * e decidido aqui. Estes testes sao o que impede a regra de virar decorativa.
 */
class CleartextPolicyTest {

    // Todos os enderecos daqui sao ficticios de proposito: o repositorio e
    // publico, e endereco real de peer da mesh nao tem por que ser versionado.

    @Test
    fun `IP da mesh netbird e privado`() {
        // 100.70.0.0/16 mora dentro do CGNAT 100.64.0.0/10.
        assertTrue(CleartextPolicy.isPrivateLiteral("100.70.0.10"))
        assertTrue(CleartextPolicy.isPrivateLiteral("100.64.0.1"))
        assertTrue(CleartextPolicy.isPrivateLiteral("100.127.255.254"))
    }

    @Test
    fun `faixas privadas classicas e loopback`() {
        assertTrue(CleartextPolicy.isPrivateLiteral("10.1.2.3"))
        assertTrue(CleartextPolicy.isPrivateLiteral("192.168.1.20"))
        assertTrue(CleartextPolicy.isPrivateLiteral("172.16.0.1"))
        assertTrue(CleartextPolicy.isPrivateLiteral("127.0.0.1"))
        assertTrue(CleartextPolicy.isPrivateLiteral("::1"))
        assertTrue(CleartextPolicy.isPrivateLiteral("fd00::1"))
    }

    @Test
    fun `IP publico e borda das faixas nao passam`() {
        assertFalse(CleartextPolicy.isPrivateLiteral("8.8.8.8"))
        assertFalse(CleartextPolicy.isPrivateLiteral("100.63.255.255")) // logo abaixo do CGNAT
        assertFalse(CleartextPolicy.isPrivateLiteral("100.128.0.0"))    // logo acima
        assertFalse(CleartextPolicy.isPrivateLiteral("172.32.0.1"))     // fora do /12
        assertFalse(CleartextPolicy.isPrivateLiteral("11.0.0.1"))
    }

    @Test
    fun `hostname nunca conta como literal privado`() {
        // Mesmo que resolva pra IP privado: o DNS muda e a checagem viraria
        // decorativa. Cleartext exige o literal.
        assertFalse(CleartextPolicy.isPrivateLiteral("localhost"))
        assertFalse(CleartextPolicy.isPrivateLiteral("exemplo.invalid"))
    }
}

class BaseUrlValidationTest {

    @Test
    fun `https em nome de host e aceito sem alarde`() {
        val v = BaseUrlInterceptor.validate("https://exemplo.invalid")
        assertTrue(v is BaseUrlValidation.Valid)
        assertFalse((v as BaseUrlValidation.Valid).cleartext)
    }

    @Test
    fun `http em IP da mesh e aceito e marcado como cleartext`() {
        val v = BaseUrlInterceptor.validate("http://100.70.0.10")
        assertTrue(v is BaseUrlValidation.Valid)
        assertTrue((v as BaseUrlValidation.Valid).cleartext)
    }

    @Test
    fun `IP da mesh sem esquema assume http e passa`() {
        val v = BaseUrlInterceptor.validate("100.70.0.10:8000")
        assertTrue(v is BaseUrlValidation.Valid)
        assertTrue((v as BaseUrlValidation.Valid).cleartext)
    }

    @Test
    fun `http em nome de host e recusado`() {
        val v = BaseUrlInterceptor.validate("http://exemplo.invalid")
        assertTrue(v is BaseUrlValidation.CleartextNotAllowed)
        assertEquals("exemplo.invalid", (v as BaseUrlValidation.CleartextNotAllowed).host)
    }

    @Test
    fun `http em IP publico e recusado`() {
        assertTrue(BaseUrlInterceptor.validate("http://8.8.8.8") is BaseUrlValidation.CleartextNotAllowed)
    }

    @Test
    fun `vazio e lixo tem estados proprios`() {
        assertTrue(BaseUrlInterceptor.validate("   ") is BaseUrlValidation.Empty)
        assertTrue(BaseUrlInterceptor.validate("http://") is BaseUrlValidation.Malformed)
    }
}
