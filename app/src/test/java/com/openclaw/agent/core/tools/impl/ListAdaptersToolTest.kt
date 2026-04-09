package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.web.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test

class ListAdaptersToolTest {

    @Test
    fun `name is list_web_adapters`() {
        val registry = AdapterRegistry()
        val tool = ListAdaptersTool(registry)
        assertEquals("list_web_adapters", tool.name)
    }

    @Test
    fun `returns empty message when no adapters`() = runTest {
        val registry = AdapterRegistry()
        val tool = ListAdaptersTool(registry)
        val result = tool.execute(buildJsonObject {})
        assertTrue(result.success)
        assertTrue(result.content.contains("No web adapters"))
    }

    @Test
    fun `lists registered adapters`() = runTest {
        val registry = AdapterRegistry()
        registry.register(object : WebAdapter {
            override val site = "testsite"
            override val displayName = "Test Site"
            override val authStrategy = AuthStrategy.PUBLIC
            override val commands = listOf(
                AdapterCommand("cmd1", "A command", listOf(
                    CommandArg("q", ArgType.STRING, required = true)
                ))
            )
            override suspend fun execute(command: String, args: Map<String, Any>) =
                AdapterResult(success = true)
        })

        val tool = ListAdaptersTool(registry)
        val result = tool.execute(buildJsonObject {})

        assertTrue(result.success)
        assertTrue(result.content.contains("Test Site"))
        assertTrue(result.content.contains("testsite"))
        assertTrue(result.content.contains("cmd1"))
    }
}
