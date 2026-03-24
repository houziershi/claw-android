package com.openclaw.agent.core.tools.impl

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class CurrentTimeToolTest {

    private val tool = CurrentTimeTool()

    @Test
    fun `name is get_current_time`() {
        assertEquals("get_current_time", tool.name)
    }

    @Test
    fun `execute returns success`() = runTest {
        val result = tool.execute(buildJsonObject {})
        assertTrue(result.success)
        assertNull(result.errorMessage)
    }

    @Test
    fun `result contains current year`() = runTest {
        val result = tool.execute(buildJsonObject {})
        val year = Calendar.getInstance().get(Calendar.YEAR).toString()
        assertTrue("Should contain current year", result.content.contains(year))
    }

    @Test
    fun `result contains timezone info`() = runTest {
        val result = tool.execute(buildJsonObject {})
        assertTrue("Should contain 'Timezone'", result.content.contains("Timezone"))
    }

    @Test
    fun `result contains Current time prefix`() = runTest {
        val result = tool.execute(buildJsonObject {})
        assertTrue(result.content.startsWith("Current time:"))
    }

    @Test
    fun `parameterSchema is valid object type`() {
        assertEquals("object", tool.parameterSchema["type"]?.toString()?.trim('"'))
    }
}
