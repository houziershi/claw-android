package com.openclaw.agent.core.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolRouterTest {

    private lateinit var registry: ToolRegistry
    private lateinit var router: ToolRouter

    @Before
    fun setup() {
        registry = ToolRegistry()
        router = ToolRouter(registry)
    }

    @Test
    fun `routes to correct tool and returns result`() = runTest {
        val tool = FakeTool("my_tool", "desc", ToolResult(true, "hello world"))
        registry.register(tool)

        val result = router.execute("my_tool", buildJsonObject { put("key", "value") })
        assertTrue(result.success)
        assertEquals("hello world", result.content)
        // Verify args were passed through
        assertNotNull(tool.lastArgs)
        assertEquals("value", tool.lastArgs!!["key"]?.toString()?.trim('"'))
    }

    @Test
    fun `unknown tool returns error with available tools list`() = runTest {
        registry.register(FakeTool("tool_a", "A"))
        registry.register(FakeTool("tool_b", "B"))

        val result = router.execute("nonexistent", buildJsonObject {})
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Unknown tool"))
        assertTrue(result.errorMessage!!.contains("tool_a"))
        assertTrue(result.errorMessage!!.contains("tool_b"))
    }

    @Test
    fun `tool exception is caught and returned as error`() = runTest {
        val crashingTool = object : Tool {
            override val name = "crash"
            override val description = "crashes"
            override val parameterSchema: JsonObject = buildJsonObject { put("type", "object") }
            override suspend fun execute(args: JsonObject): ToolResult {
                throw RuntimeException("boom!")
            }
        }
        registry.register(crashingTool)

        val result = router.execute("crash", buildJsonObject {})
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("boom!"))
    }

    @Test
    fun `empty registry returns error for any tool`() = runTest {
        val result = router.execute("anything", buildJsonObject {})
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("Unknown tool"))
    }

    @Test
    fun `passes empty args correctly`() = runTest {
        val tool = FakeTool("simple", "simple tool")
        registry.register(tool)

        val result = router.execute("simple", buildJsonObject {})
        assertTrue(result.success)
        assertNotNull(tool.lastArgs)
        assertEquals(0, tool.lastArgs!!.size)
    }
}
