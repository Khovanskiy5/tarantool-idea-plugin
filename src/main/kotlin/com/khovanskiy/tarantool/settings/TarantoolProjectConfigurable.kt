package com.khovanskiy.tarantool.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.khovanskiy.tarantool.TarantoolBundle
import javax.swing.JComponent

/**
 * Проектная страница Settings → Tools → Tarantool → Режим запуска:
 * где живут инстансы — локально, в Docker или в Kubernetes.
 */
class TarantoolProjectConfigurable(private val project: Project) : Configurable {

    private val modeCombo = ComboBox(TarantoolRunMode.entries.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { mode -> modeLabel(mode) }
    }
    private val dockerPrefixField = JBTextField()
    private val namespaceField = JBTextField()
    private val selectorField = JBTextField()
    private val consoleField = JBTextField()

    override fun getDisplayName(): String = TarantoolBundle.message("settings.project.display.name")

    override fun createComponent(): JComponent = panel {
        row(TarantoolBundle.message("settings.project.mode")) {
            cell(modeCombo)
        }
        group(TarantoolBundle.message("settings.project.docker.group")) {
            row(TarantoolBundle.message("settings.project.docker.prefix")) {
                cell(dockerPrefixField)
                    .align(AlignX.FILL)
                    .comment(TarantoolBundle.message("settings.project.docker.prefix.comment"))
            }
        }
        group(TarantoolBundle.message("settings.project.kube.group")) {
            row(TarantoolBundle.message("settings.project.kube.namespace")) {
                cell(namespaceField)
                    .align(AlignX.FILL)
            }
            row(TarantoolBundle.message("settings.project.kube.selector")) {
                cell(selectorField)
                    .align(AlignX.FILL)
                    .comment(TarantoolBundle.message("settings.project.kube.selector.comment"))
            }
            row(TarantoolBundle.message("settings.project.kube.console")) {
                cell(consoleField)
                    .align(AlignX.FILL)
                    .comment(TarantoolBundle.message("settings.project.kube.console.comment"))
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = TarantoolProjectSettings.getInstance(project)
        return modeCombo.selectedItem != settings.mode ||
            dockerPrefixField.text.trim() != settings.dockerExecPrefix ||
            namespaceField.text.trim() != settings.kubernetesNamespace ||
            selectorField.text.trim() != settings.kubernetesPodSelector ||
            consoleField.text.trim() != settings.kubernetesConsoleCommand
    }

    override fun apply() {
        val settings = TarantoolProjectSettings.getInstance(project)
        settings.mode = modeCombo.selectedItem as? TarantoolRunMode ?: TarantoolRunMode.LOCAL
        settings.dockerExecPrefix = dockerPrefixField.text.trim()
        settings.kubernetesNamespace = namespaceField.text.trim()
        settings.kubernetesPodSelector = selectorField.text.trim()
        settings.kubernetesConsoleCommand = consoleField.text.trim()
    }

    override fun reset() {
        val settings = TarantoolProjectSettings.getInstance(project)
        modeCombo.selectedItem = settings.mode
        dockerPrefixField.text = settings.dockerExecPrefix
        namespaceField.text = settings.kubernetesNamespace
        selectorField.text = settings.kubernetesPodSelector
        consoleField.text = settings.kubernetesConsoleCommand
    }

    private fun modeLabel(mode: TarantoolRunMode): String = when (mode) {
        TarantoolRunMode.LOCAL -> TarantoolBundle.message("settings.project.mode.local")
        TarantoolRunMode.DOCKER -> TarantoolBundle.message("settings.project.mode.docker")
        TarantoolRunMode.KUBERNETES -> TarantoolBundle.message("settings.project.mode.kubernetes")
    }
}
