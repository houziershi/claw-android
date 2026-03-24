package com.openclaw.agent.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    @Before
    fun setup() {
        registry = ToolRegistry()
    }

    @Test
    fun `register and retrieve tool`() {
        val tool = FakeTool("test_tool", "A test tool")
        registry.register(tool)

        val retrieved = registry.getTool("test_tool")
        assertNotNull(retrieved)
        assertEquals("test_tool", retrieved!!.name)
    }

    @Test
    fun `getTool returns null for unknown tool`() {
        assertNull(registry.getTool("nonexistent"))
    }

    @Test
    fun `getAllTools returns all registered tools`() {
        registry.register(FakeTool("tool_a", "Tool A"))
        registry.register(FakeTool("tool_b", "Tool B"))
        registry.register(FakeTool("tool_c", "Tool C"))

        val all = registry.getAllTools()
        assertEquals(3, all.size)
        assertTrue(all.any { it.name == "tool_a" })
        assertTrue(all.any { it.name == "tool_b" })
        assertTrue(all.any { it.name == "tool_c" })
    }

    @Test
    fun `getToolCount returns correct count`() {
        assertEquals(0, registry.getToolCount())
        registry.register(FakeTool("a", "A"))
        assertEquals(1, registry.getToolCount())
        registry.register(FakeTool("b", "B"))
        assertEquals(2, registry.getToolCount())
    }

    @Test
    fun `unregister removes tool`() {
        registry.register(FakeTool("tool_x", "X"))
        assertNotNull(registry.getTool("tool_x"))

        registry.unregister("tool_x")
        assertNull(registry.getTool("tool_x"))
        assertEquals(0, registry.getToolCount())
    }

    @Test
    fun `register overwrites existing tool with same name`() {
        registry.register(FakeTool("dup", "First"))
        registry.register(FakeTool("dup", "Second"))

        assertEquals(1, registry.getToolCount())
        assertEquals("Second", registry.getTool("dup")!!.description)
    }

    @Test
    fun `toToolDefinitions converts all tools`() {
        registry.register(FakeTool("t1", "Tool 1"))
        registry.register(FakeTool("t2", "Tool 2"))

        val defs = registry.toToolDefinitions()
        assertEquals(2, defs.size)
        assertTrue(defs.any { it.name == "t1" && it.description == "Tool 1" })
        assertTrue(defs.any { it.name == "t2" && it.description == "Tool 2" })
    }
}

/** Simple fake Tool for testing */
class FakeTool(
    override val name: String,
    override val description: String,
    private val result: ToolResult = ToolResult(success = true, content = "ok")
) : Tool {
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
    }

    var lastArgs: JsonObject? = null
        private set

    override suspend fun execute(args: JsonObject): ToolResult {
        lastArgs = args
        return result
    }
}
