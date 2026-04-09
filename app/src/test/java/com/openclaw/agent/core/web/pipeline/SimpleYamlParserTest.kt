package com.openclaw.agent.core.web.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SimpleYamlParserTest {

    private lateinit var parser: SimpleYamlParser

    @Before
    fun setup() {
        parser = SimpleYamlParser()
    }

    @Test
    fun `parse minimal YAML`() {
        val yaml = """
            site: test
            name: hello
            description: A test
            strategy: public
            browser: false
            pipeline:
              - fetch:
                  url: https://example.com/api
        """.trimIndent()

        val def = parser.parse(yaml)
        assertNotNull(def)
        assertEquals("test", def!!.site)
        assertEquals("hello", def.name)
        assertEquals("A test", def.description)
        assertEquals("public", def.strategy)
        assertFalse(def.browser)
        assertEquals(1, def.pipeline.size)
        assertTrue(def.pipeline[0] is PipelineStep.Fetch)
        assertEquals("https://example.com/api", (def.pipeline[0] as PipelineStep.Fetch).url)
    }

    @Test
    fun `parse args with defaults`() {
        val yaml = """
            site: test
            name: cmd
            description: desc
            args:
              limit:
                type: int
                default: 10
                description: Max items
              query:
                type: string
                required: true
                description: Search query
            pipeline:
              - fetch:
                  url: https://example.com
        """.trimIndent()

        val def = parser.parse(yaml)
        assertNotNull(def)
        assertEquals(2, def!!.args.size)
        assertEquals("int", def.args["limit"]?.type)
        assertEquals("10", def.args["limit"]?.default)
        assertTrue(def.args["query"]?.required == true)
    }

    @Test
    fun `parse pipeline with multiple steps`() {
        val yaml = """
            site: hn
            name: top
            description: Top stories
            pipeline:
              - fetch:
                  url: https://api.example.com/top.json
              - select: items
              - map:
                  title: item.title
                  score: item.score
              - filter: item.score > 10
              - limit: 5
        """.trimIndent()

        val def = parser.parse(yaml)
        assertNotNull(def)
        assertEquals(5, def!!.pipeline.size)
        assertTrue(def.pipeline[0] is PipelineStep.Fetch)
        assertTrue(def.pipeline[1] is PipelineStep.Select)
        assertTrue(def.pipeline[2] is PipelineStep.MapStep)
        assertTrue(def.pipeline[3] is PipelineStep.Filter)
        assertTrue(def.pipeline[4] is PipelineStep.Limit)

        assertEquals("items", (def.pipeline[1] as PipelineStep.Select).field)
        assertEquals(2, (def.pipeline[2] as PipelineStep.MapStep).fields.size)
    }

    @Test
    fun `parse inline columns list`() {
        val yaml = """
            site: test
            name: cmd
            description: desc
            pipeline:
              - fetch:
                  url: https://example.com
            columns: [rank, title, score]
        """.trimIndent()

        val def = parser.parse(yaml)
        assertNotNull(def)
        assertEquals(listOf("rank", "title", "score"), def!!.columns)
    }

    @Test
    fun `returns null for missing site`() {
        val yaml = """
            name: cmd
            description: desc
            pipeline:
              - fetch:
                  url: https://example.com
        """.trimIndent()

        assertNull(parser.parse(yaml))
    }

    @Test
    fun `returns null for missing name`() {
        val yaml = """
            site: test
            description: desc
            pipeline:
              - fetch:
                  url: https://example.com
        """.trimIndent()

        assertNull(parser.parse(yaml))
    }

    @Test
    fun `parse v2ex-style YAML`() {
        val yaml = """
            site: v2ex
            name: hot
            description: V2EX hot topics
            strategy: public
            browser: false
            args:
              limit:
                type: int
                default: 20
                description: Number of topics
            pipeline:
              - fetch:
                  url: https://www.v2ex.com/api/topics/hot.json
              - map:
                  rank: index + 1
                  title: item.title
                  replies: item.replies
              - limit: args.limit
            columns: [rank, title, replies]
        """.trimIndent()

        val def = parser.parse(yaml)
        assertNotNull(def)
        assertEquals("v2ex", def!!.site)
        assertEquals("hot", def.name)
        assertEquals(3, def.pipeline.size)
        assertEquals(3, def.columns.size)
    }
}
