package com.openclaw.agent.core.web.pipeline

import com.openclaw.agent.core.web.*

/**
 * Wraps a YamlPipelineDef as a WebAdapter.
 * Multiple YamlAdapters with the same site can be grouped into a MultiCommandYamlAdapter.
 */
class YamlAdapter(
    private val def: YamlPipelineDef,
    private val runner: PipelineRunner
) : WebAdapter {

    override val site: String = def.site
    override val displayName: String = def.site.replaceFirstChar { it.uppercase() }
    override val authStrategy: AuthStrategy = when (def.strategy) {
        "cookie" -> AuthStrategy.COOKIE
        "header" -> AuthStrategy.HEADER
        "api_key" -> AuthStrategy.API_KEY
        else -> AuthStrategy.PUBLIC
    }
    override val commands: List<AdapterCommand> = listOf(
        AdapterCommand(
            name = def.name,
            description = def.description,
            args = def.args.map { (name, argDef) ->
                CommandArg(
                    name = name,
                    type = when (argDef.type.lowercase()) {
                        "int", "integer" -> ArgType.INT
                        "bool", "boolean" -> ArgType.BOOLEAN
                        else -> ArgType.STRING
                    },
                    required = argDef.required,
                    default = argDef.default,
                    description = argDef.description
                )
            }
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        if (command != def.name) {
            return AdapterResult(success = false, error = "Unknown command: $command")
        }
        val mergedArgs = mergeWithDefaults(args)
        return runner.execute(def, mergedArgs)
    }

    private fun mergeWithDefaults(args: Map<String, Any>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        def.args.forEach { (name, argDef) ->
            argDef.default?.let { default ->
                result[name] = when {
                    argDef.type.lowercase() in listOf("int", "integer") ->
                        default.toString().toIntOrNull() ?: default
                    else -> default
                }
            }
        }
        result.putAll(args)
        return result
    }
}

/**
 * Groups multiple YamlAdapters (same site, different commands) into one WebAdapter.
 */
class MultiCommandYamlAdapter(
    override val site: String,
    private val commandAdapters: Map<String, YamlAdapter>
) : WebAdapter {

    override val displayName: String = site.replaceFirstChar { it.uppercase() }
    override val authStrategy: AuthStrategy =
        commandAdapters.values.firstOrNull()?.authStrategy ?: AuthStrategy.PUBLIC

    override val commands: List<AdapterCommand> =
        commandAdapters.values.flatMap { it.commands }

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        val adapter = commandAdapters[command]
            ?: return AdapterResult(success = false, error = "Unknown command: $command")
        return adapter.execute(command, args)
    }
}
