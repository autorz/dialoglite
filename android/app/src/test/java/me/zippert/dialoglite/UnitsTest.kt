package me.zippert.dialoglite

import me.zippert.dialoglite.data.BulkErrorPolicy
import me.zippert.dialoglite.data.remote.BaseUrlInterceptor
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
        assertEquals("http://ymir.local/", BaseUrlInterceptor.parse("ymir.local").toString())
    }

    @Test
    fun `preserva porta, https e prefixo de caminho`() {
        assertEquals("http://100.70.38.0:8000/", BaseUrlInterceptor.parse("100.70.38.0:8000").toString())
        assertEquals("https://dialog.zippert.me/", BaseUrlInterceptor.parse("https://dialog.zippert.me").toString())
        assertEquals("https://host/ponto/", BaseUrlInterceptor.parse("https://host/ponto").toString())
    }

    @Test
    fun `vazio vira null`() {
        assertNull(BaseUrlInterceptor.parse("   "))
    }
}
