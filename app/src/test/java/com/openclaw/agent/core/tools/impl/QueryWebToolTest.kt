package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.web.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.*
import org.junit.Test

class QueryWebToolTest {

    @Test
    fun `name is query_web`() {
        val tool = QueryWebTool(AdapterRegistry())
        assertEquals("query_web", tool.name)
    }

    @Test
    fun `missing site returns error`() = runTest {
        val tool = QueryWebTool(AdapterRegistry())
        val result = tool.execute(buildJsonObject {
            put("command", "top")
        })
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("site"))
    }

    @Test
    fun `missing command returns error`() = runTest {
        val tool = QueryWebTool(AdapterRegistry())
        val result = tool.execute(buildJsonObject {
            put("site", "hackernews")
        })
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("command"))
    }

    @Test
    fun `unknown site returns error`() = runTest {
        val tool = QueryWebTool(AdapterRegistry())
        val result = tool.execute(buildJsonObject {
            put("site", "nonexistent")
            put("command", "top")
        })
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("Unknown site"))
    }

    @Test
    fun `dispatches to correct adapter`() = runTest {
        val registry = AdapterRegistry()
        registry.register(object : WebAdapter {
            override val site = "mock"
            override val displayName = "Mock"
            override val authStrategy = AuthStrategy.PUBLIC
            override val commands = listOf(AdapterCommand("greet", "Say hello"))
            override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
                val name = args["name"] as? String ?: "world"
                return AdapterResult(
                    success = true,
                    data = listOf(mapOf("greeting" to "Hello, $name!"))
                )
            }
        })

        val tool = QueryWebTool(registry)
        val result = tool.execute(buildJsonObject {
            put("site", "mock")
            put("command", "greet")
            putJsonObject("args") {
                put("name", "Hou")
            }
        })

        assertTrue(result.success)
        assertTrue(result.content.contains("Hello, Hou!"))
    }

    @Test
    fun `adapter error is forwarded`() = runTest {
        val registry = AdapterRegistry()
        registry.register(object : WebAdapter {
            override val site = "fail"
            override val displayName = "Fail"
            override val authStrategy = AuthStrategy.PUBLIC
            override val commands = emptyList<AdapterCommand>()
            override suspend fun execute(command: String, args: Map<String, Any>) =
                AdapterResult(success = false, error = "Something broke")
        })

        val tool = QueryWebTool(registry)
        val result = tool.execute(buildJsonObject {
            put("site", "fail")
            put("command", "anything")
        })

        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("Something broke"))
    }
}
