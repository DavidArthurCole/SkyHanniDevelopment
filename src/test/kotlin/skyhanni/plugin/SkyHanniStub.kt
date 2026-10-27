package skyhanni.plugin

import skyhanni.plugin.api.SkyHanniIds

enum class SkyHanniStub(val files: List<Pair<String, String>>) {
    EVENT(
        "at/hannibal2/skyhanni/api/event/SkyHanniEvent.kt",
        """
        package at.hannibal2.skyhanni.api.event

        import kotlin.reflect.KClass

        abstract class SkyHanniEvent protected constructor()

        annotation class HandleEvent(
            val eventType: KClass<out SkyHanniEvent> = SkyHanniEvent::class,
            val eventTypes: Array<KClass<out SkyHanniEvent>> = [],
            val priority: Int = 0,
        )
        """.trimIndent(),
    ),
    MODULE(
        "at/hannibal2/skyhanni/skyhannimodule/SkyHanniModule.kt",
        """
        package at.hannibal2.skyhanni.skyhannimodule

        annotation class PrimaryFunction(val value: String)

        annotation class SkyHanniModule(val devOnly: Boolean = false)
        """.trimIndent(),
    ),
    REPO_PATTERN(
        "at/hannibal2/skyhanni/utils/repopatterns/RepoPattern.kt",
        """
        package at.hannibal2.skyhanni.utils.repopatterns

        import kotlin.reflect.KProperty

        sealed class RepoPattern {
            operator fun getValue(thisRef: Any?, property: KProperty<*>): java.util.regex.Pattern = TODO()

            companion object {
                fun pattern(key: String, fallback: String): RepoPattern = TODO()
                fun list(key: String, vararg fallbacks: String): RepoPatternList = TODO()
                fun group(prefix: String): RepoPatternGroup = TODO()
                fun exclusiveGroup(prefix: String): RepoPatternGroup = TODO()
            }
        }

        open class RepoPatternGroup {
            fun pattern(key: String, fallback: String): RepoPattern = TODO()
            fun list(key: String, vararg fallbacks: String): RepoPatternList = TODO()
            fun group(prefix: String): RepoPatternGroup = TODO()
            fun exclusiveGroup(prefix: String): RepoPatternGroup = TODO()
        }

        class RepoPatternList {
            operator fun getValue(thisRef: Any?, property: KProperty<*>): List<java.util.regex.Pattern> = TODO()
        }
        """.trimIndent(),
    ),
    CONFIG_MIGRATOR(
        "at/hannibal2/skyhanni/config/ConfigUpdaterMigrator.kt",
        """
        package at.hannibal2.skyhanni.config

        object ConfigUpdaterMigrator {
            const val CONFIG_VERSION = 149

            class ConfigFixEvent {
                fun move(since: Int, fromPath: String, toPath: String, transform: (Any) -> Any = { it }) {}
                fun transform(since: Int, path: String, transform: (Any) -> Any = { it }) {}
                fun add(since: Int, path: String, value: () -> Any) {}
                fun remove(since: Int, oldPath: String) {}
            }
        }
        """.trimIndent(),
    ),
    CONFIG_ROOTS(
        listOf(
            "at/hannibal2/skyhanni/config/SkyHanniConfig.kt" to """
            package at.hannibal2.skyhanni.config

            class SkyHanniConfig
            """.trimIndent(),
            "at/hannibal2/skyhanni/config/storage/ProfileSpecificStorage.kt" to """
            package at.hannibal2.skyhanni.config.storage

            class ProfileSpecificStorage
            """.trimIndent(),
            "at/hannibal2/skyhanni/config/storage/PlayerSpecificStorage.kt" to """
            package at.hannibal2.skyhanni.config.storage

            class PlayerSpecificStorage
            """.trimIndent(),
        ),
    ),
    MOULCONFIG(
        listOf(
            "io/github/notenoughupdates/moulconfig/annotations/Annotations.kt" to """
            package io.github.notenoughupdates.moulconfig.annotations

            import kotlin.reflect.KClass

            annotation class ConfigOption(val name: String, val desc: String)

            annotation class Category(val name: String, val desc: String)

            annotation class ConfigLink(val owner: KClass<*>, val field: String)
            """.trimIndent(),
            "io/github/notenoughupdates/moulconfig/observer/Property.kt" to """
            package io.github.notenoughupdates.moulconfig.observer

            class Property<T> {
                companion object {
                    fun <T> of(value: T): Property<T> = TODO()
                }
            }
            """.trimIndent(),
        ),
    ),
    COMMANDS(
        "at/hannibal2/skyhanni/config/commands/CommandRegistrationEvent.kt",
        """
        package at.hannibal2.skyhanni.config.commands

        class CommandRegistrationEvent {
            fun registerBrigadier(name: String, builder: BrigadierBuilder.() -> Unit) {}
        }

        class BrigadierBuilder {
            var aliases: List<String> = emptyList()
        }
        """.trimIndent(),
    ),
    REGEX_UTILS(
        "at/hannibal2/skyhanni/utils/RegexUtils.kt",
        """
        package at.hannibal2.skyhanni.utils

        object RegexUtils {
            fun java.util.regex.Matcher.groupOrNull(groupName: String): String? = null
        }
        """.trimIndent(),
    ),
    MIXIN(
        listOf(
            javaAnnotation(SkyHanniIds.Mixins.MIXIN, "Class<?>[] value() default {};", "String[] targets() default {};"),
            javaAnnotation(SkyHanniIds.Mixins.SHADOW),
            javaAnnotation(SkyHanniIds.Mixins.OVERWRITE),
            javaAnnotation("org.spongepowered.asm.mixin.injection.At", "String value();", "String target() default \"\";"),
        ) + SkyHanniIds.Mixins.INJECTORS.map {
            javaAnnotation(
                it,
                "String[] method() default {};",
                "org.spongepowered.asm.mixin.injection.At[] at() default {};",
            )
        },
    ),
    ;

    constructor(path: String, source: String) : this(listOf(path to source))
}

private fun javaAnnotation(fqn: String, vararg members: String): Pair<String, String> {
    val packageName = fqn.substringBeforeLast('.')
    val simpleName = fqn.substringAfterLast('.')
    val body = members.joinToString("") { "    $it\n" }
    val source = """
        package $packageName;

        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;

        @Retention(RetentionPolicy.RUNTIME)
        public @interface $simpleName {
        $body}
    """.trimIndent()
    return fqn.replace('.', '/') + ".java" to source
}
