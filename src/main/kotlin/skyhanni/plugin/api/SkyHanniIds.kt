package skyhanni.plugin.api

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object SkyHanniIds {

    const val NOTIFICATION_GROUP = "SkyHanni Plugin"

    val SKYHANNI_PACKAGE = FqName("at.hannibal2.skyhanni")
    val CONFIG_PACKAGE = FqName("at.hannibal2.skyhanni.config")

    private val eventPackage = FqName("at.hannibal2.skyhanni.api.event")
    private val modulePackage = FqName("at.hannibal2.skyhanni.skyhannimodule")
    private val repoPatternPackage = FqName("at.hannibal2.skyhanni.utils.repopatterns")
    private val moulconfigAnnotations = FqName("io.github.notenoughupdates.moulconfig.annotations")

    val SKYHANNI_EVENT = ClassId(eventPackage, Name.identifier("SkyHanniEvent"))
    val HANDLE_EVENT = ClassId(eventPackage, Name.identifier("HandleEvent"))
    val PRIMARY_FUNCTION = ClassId(modulePackage, Name.identifier("PrimaryFunction"))
    val SKYHANNI_MODULE = ClassId(modulePackage, Name.identifier("SkyHanniModule"))

    val REPO_PATTERN = ClassId(repoPatternPackage, Name.identifier("RepoPattern"))
    val REPO_PATTERN_COMPANION = REPO_PATTERN.createNestedClassId(Name.identifier("Companion"))
    val REPO_PATTERN_GROUP = ClassId(repoPatternPackage, Name.identifier("RepoPatternGroup"))
    val REPO_PATTERN_LIST = ClassId(repoPatternPackage, Name.identifier("RepoPatternList"))
    val REPO_PATTERN_PATTERN = factoryIds("pattern")
    val REPO_PATTERN_LIST_FN = factoryIds("list")
    val REPO_PATTERN_FACTORIES = REPO_PATTERN_PATTERN + REPO_PATTERN_LIST_FN + factoryIds("group") + factoryIds("exclusiveGroup")

    val CONFIG_UPDATER_MIGRATOR = ClassId(CONFIG_PACKAGE, Name.identifier("ConfigUpdaterMigrator"))
    val CONFIG_FIX_EVENT = CONFIG_UPDATER_MIGRATOR.createNestedClassId(Name.identifier("ConfigFixEvent"))
    val CONFIG_VERSION = CallableId(CONFIG_UPDATER_MIGRATOR, Name.identifier("CONFIG_VERSION"))
    val CONFIG_FIX_PATH_FUNCTIONS = setOf("move", "transform", "add", "remove")
        .map { CallableId(CONFIG_FIX_EVENT, Name.identifier(it)) }
        .toSet()
    val CONFIG_FIX_PATH_PARAMS = setOf("fromPath", "toPath", "path", "oldPath").map(Name::identifier).toSet()

    val COMMAND_REGISTRATION_EVENT = ClassId(FqName("at.hannibal2.skyhanni.config.commands"), Name.identifier("CommandRegistrationEvent"))
    val REGISTER_BRIGADIER = CallableId(COMMAND_REGISTRATION_EVENT, Name.identifier("registerBrigadier"))

    val REGEX_UTILS = ClassId(FqName("at.hannibal2.skyhanni.utils"), Name.identifier("RegexUtils"))
    val REGEX_UTILS_GROUP_OR_NULL = CallableId(REGEX_UTILS, Name.identifier("groupOrNull"))
    val MATCHER = ClassId(FqName("java.util.regex"), Name.identifier("Matcher"))
    val MATCHER_GROUP = CallableId(MATCHER, Name.identifier("group"))
    val JAVA_PATTERN = ClassId(FqName("java.util.regex"), Name.identifier("Pattern"))
    val KOTLIN_REGEX = ClassId(FqName("kotlin.text"), Name.identifier("Regex"))

    val CONFIG_OPTION = ClassId(moulconfigAnnotations, Name.identifier("ConfigOption"))
    val CATEGORY = ClassId(moulconfigAnnotations, Name.identifier("Category"))
    val CONFIG_LINK = ClassId(moulconfigAnnotations, Name.identifier("ConfigLink"))
    val CONFIG_ANNOTATIONS = setOf(CONFIG_OPTION, CATEGORY, CONFIG_LINK)
    val MOULCONFIG_PROPERTY = ClassId(FqName("io.github.notenoughupdates.moulconfig.observer"), Name.identifier("Property"))

    val SKYHANNI_CONFIG = ClassId(CONFIG_PACKAGE, Name.identifier("SkyHanniConfig"))
    val PROFILE_STORAGE = ClassId(FqName("at.hannibal2.skyhanni.config.storage"), Name.identifier("ProfileSpecificStorage"))
    val PLAYER_STORAGE = ClassId(FqName("at.hannibal2.skyhanni.config.storage"), Name.identifier("PlayerSpecificStorage"))
    val ROOT_CONFIG_CLASSES = setOf(SKYHANNI_CONFIG, PROFILE_STORAGE, PLAYER_STORAGE)

    private fun factoryIds(name: String): Set<CallableId> = setOf(
        CallableId(REPO_PATTERN_COMPANION, Name.identifier(name)),
        CallableId(REPO_PATTERN_GROUP, Name.identifier(name)),
    )

    object Mixins {
        const val MIXIN = "org.spongepowered.asm.mixin.Mixin"
        const val SHADOW = "org.spongepowered.asm.mixin.Shadow"
        const val OVERWRITE = "org.spongepowered.asm.mixin.Overwrite"
        const val INJECT = "org.spongepowered.asm.mixin.injection.Inject"
        const val REDIRECT = "org.spongepowered.asm.mixin.injection.Redirect"
        const val MODIFY_ARG = "org.spongepowered.asm.mixin.injection.ModifyArg"
        const val MODIFY_ARGS = "org.spongepowered.asm.mixin.injection.ModifyArgs"
        const val MODIFY_VARIABLE = "org.spongepowered.asm.mixin.injection.ModifyVariable"
        const val MODIFY_CONSTANT = "org.spongepowered.asm.mixin.injection.ModifyConstant"
        const val WRAP_OPERATION = "com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation"
        const val WRAP_METHOD = "com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod"
        const val MODIFY_EXPRESSION_VALUE = "com.llamalad7.mixinextras.injector.ModifyExpressionValue"
        const val MODIFY_RETURN_VALUE = "com.llamalad7.mixinextras.injector.ModifyReturnValue"
        const val MODIFY_RECEIVER = "com.llamalad7.mixinextras.injector.ModifyReceiver"
        const val WRAP_WITH_CONDITION = "com.llamalad7.mixinextras.injector.WrapWithCondition"
        const val WRAP_WITH_CONDITION_V2 = "com.llamalad7.mixinextras.injector.v2.WrapWithCondition"

        val INJECTORS = setOf(
            INJECT,
            REDIRECT,
            MODIFY_ARG,
            MODIFY_ARGS,
            MODIFY_VARIABLE,
            MODIFY_CONSTANT,
            WRAP_OPERATION,
            WRAP_METHOD,
            MODIFY_EXPRESSION_VALUE,
            MODIFY_RETURN_VALUE,
            MODIFY_RECEIVER,
            WRAP_WITH_CONDITION,
            WRAP_WITH_CONDITION_V2,
        )
    }
}
