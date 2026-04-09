package com.openclaw.agent.core.web.pipeline

import org.junit.Test

class SimpleYamlParserDebugTest {

    @Test
    fun `debug parse`() {
        val parser = SimpleYamlParser()
        val yaml = """
site: test
name: hello
description: A test
strategy: public
browser: false
pipeline:
  - fetch:
      url: https://example.com/api
  - select: items
  - map:
      title: item.title
      score: item.score
  - filter: item.score > 10
  - limit: 5
columns: [rank, title, score]
""".trimIndent()

        val def = parser.parse(yaml)
        println("Parsed: $def")
        println("Pipeline size: ${def?.pipeline?.size}")
        def?.pipeline?.forEachIndexed { i, step ->
            println("  Step $i: $step")
        }
    }
}
