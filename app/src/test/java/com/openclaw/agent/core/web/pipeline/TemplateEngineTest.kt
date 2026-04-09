package com.openclaw.agent.core.web.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TemplateEngineTest {

    private lateinit var engine: TemplateEngine

    @Before
    fun setup() {
        engine = TemplateEngine()
    }

    // --- evaluateString ---

    @Test
    fun `plain string without template returns as-is`() {
        assertEquals("hello world", engine.evaluateString("hello world", emptyMap()))
    }

    @Test
    fun `resolves simple variable`() {
        val ctx = mapOf<String, Any?>("index" to 5)
        assertEquals("5", engine.evaluateString("\${{ index }}", ctx))
    }

    @Test
    fun `resolves nested variable`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("title" to "Kotlin rocks"))
        assertEquals("Kotlin rocks", engine.evaluateString("\${{ item.title }}", ctx))
    }

    @Test
    fun `resolves args variable`() {
        val ctx = mapOf<String, Any?>("args" to mapOf("limit" to 10))
        assertEquals("10", engine.evaluateString("\${{ args.limit }}", ctx))
    }

    @Test
    fun `mixed text and template`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("id" to 42))
        assertEquals(
            "https://api.example.com/item/42.json",
            engine.evaluateString("https://api.example.com/item/\${{ item.id }}.json", ctx)
        )
    }

    @Test
    fun `missing variable returns empty string`() {
        assertEquals("", engine.evaluateString("\${{ item.missing }}", mapOf("item" to mapOf("a" to 1))))
    }

    @Test
    fun `arithmetic expression`() {
        val ctx = mapOf<String, Any?>("index" to 0)
        assertEquals("1", engine.evaluateString("\${{ index + 1 }}", ctx))
    }

    // --- evaluate (raw expression) ---

    @Test
    fun `evaluate integer literal`() {
        assertEquals(42, engine.evaluate("42", emptyMap()))
    }

    @Test
    fun `evaluate string literal`() {
        assertEquals("hello", engine.evaluate("'hello'", emptyMap()))
    }

    @Test
    fun `evaluate boolean literals`() {
        assertEquals(true, engine.evaluate("true", emptyMap()))
        assertEquals(false, engine.evaluate("false", emptyMap()))
    }

    @Test
    fun `evaluate addition`() {
        val ctx = mapOf<String, Any?>("index" to 3)
        assertEquals(4, engine.evaluate("index + 1", ctx))
    }

    // --- evaluateBoolean ---

    @Test
    fun `comparison greater than`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("score" to 150))
        assertTrue(engine.evaluateBoolean("\${{ item.score > 100 }}", ctx))
    }

    @Test
    fun `comparison less than`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("score" to 50))
        assertFalse(engine.evaluateBoolean("\${{ item.score > 100 }}", ctx))
    }

    @Test
    fun `truthy check on non-null string`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("title" to "Hello"))
        assertTrue(engine.evaluateBoolean("\${{ item.title }}", ctx))
    }

    @Test
    fun `falsy check on null`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("title" to null))
        assertFalse(engine.evaluateBoolean("\${{ item.title }}", ctx))
    }

    @Test
    fun `logical AND`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("title" to "Hi", "score" to 10))
        assertTrue(engine.evaluateBoolean("\${{ item.title && item.score }}", ctx))
    }

    @Test
    fun `logical NOT`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("deleted" to null))
        assertTrue(engine.evaluateBoolean("\${{ !item.deleted }}", ctx))
    }

    // --- pipe filters ---

    @Test
    fun `join pipe filter`() {
        val ctx = mapOf<String, Any?>("item" to mapOf("tags" to listOf("kotlin", "android")))
        assertEquals("kotlin, android", engine.evaluateString("\${{ item.tags | join(', ') }}", ctx))
    }
}
