package com.openclaw.agent.core.skill

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillParserTest {

    @Test
    fun parse_standardMarkdownSkill_returnsExpectedFields() {
        val content = """
            # Weather
            
            Query weather by city.
            
            ## Triggers
            - Weather
            - 天气
            
            ## System Prompt
            Use the weather tool.
            Prefer current conditions first.
            
            ## Required Tools
            - web_fetch
            - location
            
            ## Description
            查询天气、温度和未来预报。
        """.trimIndent()

        val skill = SkillParser.parse(content)

        assertThat(skill).isNotNull()
        assertThat(skill!!.name).isEqualTo("Weather")
        assertThat(skill.description).isEqualTo("查询天气、温度和未来预报。")
        assertThat(skill.triggers).containsExactly("weather", "天气").inOrder()
        assertThat(skill.systemPrompt).isEqualTo("Use the weather tool.\nPrefer current conditions first.")
        assertThat(skill.requiredTools).containsExactly("web_fetch", "location").inOrder()
    }

    @Test
    fun parse_skillWithYamlFrontmatter_prefersFrontmatterMetadata() {
        val content = """
            ---
            name: web-data
            description: Query structured data from popular websites using adapters
            triggers:
              - HackerNews
              - 微博
            required_tools:
              - list_web_adapters
              - query_web
            ---
            
            # Web Data Source Skill
            
            This body should not override frontmatter metadata.
            
            ## System Prompt
            Use query_web when the site is known.
        """.trimIndent()

        val skill = SkillParser.parse(content)

        assertThat(skill).isNotNull()
        assertThat(skill!!.name).isEqualTo("web-data")
        assertThat(skill.description).isEqualTo("Query structured data from popular websites using adapters")
        assertThat(skill.triggers).containsExactly("hackernews", "微博").inOrder()
        assertThat(skill.requiredTools).containsExactly("list_web_adapters", "query_web").inOrder()
        assertThat(skill.systemPrompt).isEqualTo("Use query_web when the site is known.")
    }

    @Test
    fun parse_missingTitleAndFrontmatterName_returnsNull() {
        val content = """
            No title here.
            
            ## Triggers
            - weather
        """.trimIndent()

        val skill = SkillParser.parse(content)

        assertThat(skill).isNull()
    }
}
