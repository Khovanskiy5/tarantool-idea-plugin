package com.khovanskiy.tarantool.project

import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.dsl.builder.panel
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.TarantoolIcons
import com.khovanskiy.tarantool.tt.TtCli
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Генератор каталога проекта для IDE семейства PyCharm/WebStorm.
 *
 * В IntelliJ IDEA мастер New Project собирается из другого расширения —
 * см. [TarantoolNewProjectWizard]; этот класс оставлен для совместимости
 * с IDE, где раздел Generators строится из directoryProjectGenerator.
 */
class TarantoolProjectGenerator : DirectoryProjectGeneratorBase<String>(), DumbAware {

    override fun getName(): String = "Tarantool"

    override fun getLogo(): Icon = TarantoolIcons.Tarantool

    override fun createPeer(): ProjectGeneratorPeer<String> = TarantoolGeneratorPeer()

    override fun validate(baseDirPath: String): ValidationResult {
        val tt = TtCli.resolve(null)
        return if (File(tt).isAbsolute) {
            ValidationResult.OK
        } else {
            ValidationResult(TarantoolBundle.message("project.generator.no.tt"))
        }
    }

    override fun generateProject(project: Project, baseDir: VirtualFile, template: String, module: Module) {
        TtScaffolder.scaffold(project, baseDir.toNioPath(), template)
    }
}

/** Панель мастера: выбор встроенного шаблона tt create. */
class TarantoolGeneratorPeer : GeneratorPeerImpl<String>() {

    private val templateCombo = ComboBox(TtScaffolder.TEMPLATES)

    private val panel: JComponent = panel {
        row(TarantoolBundle.message("project.generator.template")) {
            cell(templateCombo)
        }
    }

    override fun getSettings(): String = templateCombo.selectedItem as String

    override fun getComponent(
        myLocationField: TextFieldWithBrowseButton,
        checkValid: Runnable,
    ): JComponent = panel

    override fun buildUI(settingsStep: SettingsStep) {
        settingsStep.addSettingsField(TarantoolBundle.message("project.generator.template"), templateCombo)
    }
}
