package com.openclaw.agent.core.web.pipeline

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PipelineRunnerTest {

    private lateinit var server: MockWebServer
    private lateinit var runner: PipelineRunner

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        runner = PipelineRunner(OkHttpClient(), TemplateEngine())
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `fetch and map pipeline`() = runTest {
        server.enqueue(MockResponse().setBody("""
            [
                {"title": "Article A", "score": 100},
                {"title": "Article B", "score": 50}
            ]
        """.trimIndent()))

        val pipeline = YamlPipelineDef(
            site = "test", name = "cmd", description = "test",
            pipeline = listOf(
                PipelineStep.Fetch(url = server.url("/api").toString()),
                PipelineStep.MapStep(mapOf(
                    "title" to "\${{ item.title }}",
                    "rank" to "\${{ index + 1 }}"
                ))
            )
        )

        val result = runner.execute(pipeline, emptyMap())
        assertTrue(result.success)
        assertEquals(2, result.data.size)
        assertEquals("Article A", result.data[0]["title"])
        assertEquals(1, result.data[0]["rank"])
        assertEquals("Article B", result.data[1]["title"])
        assertEquals(2, result.data[1]["rank"])
    }

    @Test
    fun `select step extracts nested array`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"items": [{"name": "A"}, {"name": "B"}], "total": 2}
        """.trimIndent()))

        val pipeline = YamlPipelineDef(
            site = "test", name = "cmd", description = "test",
            pipeline = listOf(
                PipelineStep.Fetch(url = server.url("/api").toString()),
                PipelineStep.Select("items"),
                PipelineStep.MapStep(mapOf("name" to "\${{ item.name }}"))
            )
        )

        val result = runner.execute(pipeline, emptyMap())
        assertTrue(result.success)
        assertEquals(2, result.data.size)
        assertEquals("A", result.data[0]["name"])
    }

    @Test
    fun `limit step truncates results`() = runTest {
        server.enqueue(MockResponse().setBody("""
            [{"n":1},{"n":2},{"n":3},{"n":4},{"n":5}]
        """.trimIndent()))

        val pipeline = YamlPipelineDef(
            site = "test", name = "cmd", description = "test",
            args = mapOf("limit" to ArgDef(type = "int", default = 3)),
            pipeline = listOf(
                PipelineStep.Fetch(url = server.url("/api").toString()),
                PipelineStep.Limit("\${{ args.limit }}")
            )
        )

        val result = runner.execute(pipeline, mapOf("limit" to 3))
        assertTrue(result.success)
        assertEquals(3, result.data.size)
    }

    @Test
    fun `filter step removes items`() = runTest {
        server.enqueue(MockResponse().setBody("""
            [{"title":"A","score":150},{"title":"B","score":50},{"title":"C","score":200}]
        """.trimIndent()))

        val pipeline = YamlPipelineDef(
            site = "test", name = "cmd", description = "test",
            pipeline = listOf(
                PipelineStep.Fetch(url = server.url("/api").toString()),
                PipelineStep.Filter("\${{ item.score > 100 }}")
            )
        )

        val result = runner.execute(pipeline, emptyMap())
        assertTrue(result.success)
        assertEquals(2, result.data.size)
    }

    @Test
    fun `network error returns failure`() = runTest {
        // Server is up but returns 500
        server.enqueue(MockResponse().setResponseCode(500))

        val pipeline = YamlPipelineDef(
            site = "test", name = "cmd", description = "test",
            pipeline = listOf(
                PipelineStep.Fetch(url = server.url("/fail").toString())
            )
        )

        val result = runner.execute(pipeline, emptyMap())
        assertFalse(result.success)
        assertNotNull(result.error)
    }
}
