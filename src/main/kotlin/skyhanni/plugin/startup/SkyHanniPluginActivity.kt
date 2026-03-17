package skyhanni.plugin.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class SkyHanniPluginActivity : ProjectActivity {

    override suspend fun execute(project: Project) = Unit
}
