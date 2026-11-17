package co.edu.unal.reto_10

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PaginationTest {
    @Test fun followsTransferLimitWithStableIdAndCountsInvalidRecords() = runBlocking {
        val cursors = mutableListOf<Long>()
        val data = collectCaiPages(3) { lastId ->
            cursors += lastId
            if (lastId < 0) CaiPage(emptyList(), listOf(1, 2), 2, true)
            else CaiPage(emptyList(), listOf(3), 1, false)
        }
        assertEquals(listOf(-1L, 2L), cursors)
        assertEquals(3, data.received)
        assertEquals(3, data.discarded)
    }

    @Test fun missingPagesAndRepeatedIdsFailInsteadOfPublishingPartialData() = runBlocking {
        assertTrue(runCatching {
            collectCaiPages(2) { id ->
                if (id < 0) CaiPage(emptyList(), listOf(1), 1, true)
                else CaiPage(emptyList(), emptyList(), 0, false)
            }
        }.exceptionOrNull() is CaiFormatException)
        assertTrue(runCatching {
            collectCaiPages(2) { CaiPage(emptyList(), listOf(1), 1, true) }
        }.exceptionOrNull() is CaiFormatException)
    }
}
