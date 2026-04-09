package com.openclaw.agent.core.skill

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(assetDir = "src/main/assets")
class SkillEngineTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var engine: SkillEngine
    private lateinit var userSkillsDir: File

    @Before
    fun setUp() {
        engine = SkillEngine(context)
        userSkillsDir = File(context.filesDir, "memory/skills")
        userSkillsDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        userSkillsDir.deleteRecursively()
    }

    @Test
    fun loadSkills_loadsUserInstalledSkills() {
        val skillDir = File(userSkillsDir, "custom-weather").apply { mkdirs() }
        File(skillDir, "SKILL.md").writeText(
            """
            # Custom Weather

            Reads weather from user storage.

            ## Triggers
            - storm

            ## System Prompt
            Use the custom weather logic.

            ## Required Tools
            - custom_weather
            """.trimIndent()
        )

        engine.loadSkills()

        val skills = engine.getAllSkills()

        assertThat(skills.map { it.name }).contains("Custom Weather")
    }

    @Test
    fun loadFromUserDir_userSkillOverridesExistingSkillWithSameName() {
        val skillDir = File(userSkillsDir, "weather").apply { mkdirs() }
        File(skillDir, "SKILL.md").writeText(
            """
            # Weather
            
            Custom weather override.
            
            ## Triggers
            - storm
            
            ## System Prompt
            Use the custom weather logic.
            
            ## Required Tools
            - custom_weather
            """.trimIndent()
        )

        setSkills(
            Skill(
                name = "Weather",
                description = "Built-in weather skill",
                triggers = listOf("weather"),
                systemPrompt = "builtin weather prompt",
                requiredTools = listOf("web_fetch"),
            )
        )

        invokeLoadFromUserDir()

        val weatherSkill = engine.getAllSkills().single { it.name == "Weather" }
        assertThat(weatherSkill.description).isEqualTo("Custom weather override.")
        assertThat(weatherSkill.triggers).containsExactly("storm")
        assertThat(weatherSkill.requiredTools).containsExactly("custom_weather")
    }

    @Test
    fun matchSkill_prefersHigherScoreAndHonorsDisabledState() {
        setSkills(
            Skill(
                name = "Weather",
                description = "Weather skill",
                triggers = listOf("weather"),
                systemPrompt = "weather prompt",
                requiredTools = emptyList(),
            ),
            Skill(
                name = "Weather Plus",
                description = "More specific weather skill",
                triggers = listOf("weather", "forecast"),
                systemPrompt = "forecast prompt",
                requiredTools = emptyList(),
            ),
        )

        val matched = engine.matchSkill("please give me the weather forecast")
        assertThat(matched?.name).isEqualTo("Weather Plus")

        engine.setSkillEnabled("Weather Plus", false)

        val fallback = engine.matchSkill("please give me the weather forecast")
        assertThat(fallback?.name).isEqualTo("Weather")
    }

    @Test
    fun buildSkillContext_includesSkillHeaderAndPrompt() {
        val skill = Skill(
            name = "Web Data",
            description = "Query structured sources",
            triggers = listOf("web"),
            systemPrompt = "Use query_web first.",
            requiredTools = listOf("query_web"),
        )

        val contextText = engine.buildSkillContext(skill)

        assertThat(contextText).contains("## Active Skill: Web Data")
        assertThat(contextText).contains("Use query_web first.")
    }

    private fun setSkills(vararg skills: Skill) {
        val field = SkillEngine::class.java.getDeclaredField("skills")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(engine) as MutableList<Skill>
        list.clear()
        list.addAll(skills)
    }

    private fun invokeLoadFromUserDir() {
        val method = SkillEngine::class.java.getDeclaredMethod("loadFromUserDir")
        method.isAccessible = true
        method.invoke(engine)
    }
}
