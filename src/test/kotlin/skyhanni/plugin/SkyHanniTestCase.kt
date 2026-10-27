package skyhanni.plugin

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PathUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import java.io.File

abstract class SkyHanniTestCase : BasePlatformTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = SkyHanniProjectDescriptor

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        allowAnalysisOnEdt { super.runTestRunnable(testRunnable) }
    }

    fun addStubs(vararg stubs: SkyHanniStub = SkyHanniStub.entries.toTypedArray()) {
        stubs.flatMap { it.files }.forEach { (path, source) -> myFixture.addFileToProject(path, source) }
    }

    fun inspect(tool: Class<out LocalInspectionTool>, fileName: String, code: String): List<HighlightInfo> {
        myFixture.enableInspections(tool)
        myFixture.configureByText(fileName, code.trimIndent())
        return myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.INFORMATION && it.description != null }
    }

    fun intentionText(key: String, vararg params: Any): String = SkyHanniBundle.message(key, *params)

    private object SkyHanniProjectDescriptor : LightProjectDescriptor() {
        override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)
            val stdlib = File(PathUtil.getJarPathForClass(Unit::class.java))
            PsiTestUtil.addLibrary(model, "kotlin-stdlib", stdlib.parent, stdlib.name)
        }
    }
}
