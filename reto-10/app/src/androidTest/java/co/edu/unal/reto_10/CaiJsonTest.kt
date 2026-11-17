package co.edu.unal.reto_10

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CaiJsonTest {
    private fun feature(id: Long, name: String) = """
        {"attributes":{"OBJECTID":$id,"CAINOMBRE":"$name","CAIIDENTIF":"compartido",
        "CAITELEFON":"001234","CAIIULOCAL":"13","CAIDIR_SIT":null},
        "geometry":{"x":-74.08,"y":4.65}}
    """.trimIndent()

    @Test fun realAndroidJsonPreservesObjectIdsAndTelephoneText() {
        val page = parsePage(JSONObject("""{"features":[${feature(1, "CAI Laches")},${feature(2, "CAI Mirador")}]}"""))
        assertEquals(listOf(1L, 2L), page.places.map { it.id })
        assertEquals("001234", page.places.first().phone)
        assertNull(page.places.first().address)
    }

    @Test fun distinguishesEmptyInvalidAndMalformed() {
        assertTrue(parsePage(JSONObject("""{"features":[]}""")).places.isEmpty())
        val invalid = JSONObject(feature(3, "CAI")).apply { getJSONObject("geometry").put("x", 999) }
        val page = parsePage(JSONObject().put("features", org.json.JSONArray().put(invalid)))
        assertEquals(1, page.discarded)
        assertEquals(listOf(3L), page.ids)
        assertTrue(runCatching { parsePage(JSONObject("{}")) }.isFailure)
        assertTrue(runCatching { parsePage(JSONObject("""{"error":{"code":400}}""")) }.exceptionOrNull() is CaiServiceException)
    }

    @Test fun readsTheDomainAndRejectsUnsafeLocality() {
        val metadata = JSONObject("""{"fields":[{"name":"CAIIULOCAL","domain":{"codedValues":[{"code":"13","name":"TEUSAQUILLO"}]}}]}""")
        assertEquals(Locality("13", "TEUSAQUILLO"), parseLocalities(metadata).single())
        assertEquals("CAIIULOCAL='13'", localityWhere(parseLocalities(metadata).single()))
        assertTrue(runCatching { localityWhere(Locality("13' OR 1=1", "x")) }.isFailure)
    }
}
