package com.openclaw.agent.core.web

import org.junit.Assert.*
import org.junit.Test

class AdapterRegistryTest {

    @Test
    fun `register and retrieve adapter`() {
        val registry = AdapterRegistry()
        val adapter = FakeAdapter("test", "Test Site")
        registry.register(adapter)

        assertEquals(adapter, registry.getAdapter("test"))
        assertEquals(1, registry.getAllAdapters().size)
    }

    @Test
    fun `getAdapter returns null for unknown site`() {
        val registry = AdapterRegistry()
        assertNull(registry.getAdapter("unknown"))
    }

    @Test
    fun `register multiple adapters`() {
        val registry = AdapterRegistry()
        registry.register(FakeAdapter("a", "Site A"))
        registry.register(FakeAdapter("b", "Site B"))

        assertEquals(2, registry.getAllAdapters().size)
        assertNotNull(registry.getAdapter("a"))
        assertNotNull(registry.getAdapter("b"))
    }
}

private class FakeAdapter(
    override val site: String,
    override val displayName: String
) : WebAdapter {
    override val authStrategy = AuthStrategy.PUBLIC
    override val commands = emptyList<AdapterCommand>()
    override suspend fun execute(command: String, args: Map<String, Any>) =
        AdapterResult(success = true)
}
