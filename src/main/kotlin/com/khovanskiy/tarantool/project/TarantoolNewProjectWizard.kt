package com.khovanskiy.tarantool.project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.Panel
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.TarantoolIcons
import java.nio.file.Path
import javax.swing.Icon

/**
 * Пункт «Tarantool» в мастере New Project (раздел Generators).
 *
 * Мастер собирает стандартные шаги (имя и расположение проекта) плюс выбор
 * шаблона tt create; после создания проекта каркас разворачивается
 * через [TtScaffolder].
 */
class TarantoolNewProjectWizard : GeneratorNewProjectWizard {

    override val id: String = "tarantool"

    override val name: String = "Tarantool"

    override val icon: Icon
        get() = TarantoolIcons.Tarantool

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::TemplateStep)

    private class TemplateStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

        private val templateCombo = ComboBox(TtScaffolder.TEMPLATES)

        override fun setupUI(builder: Panel) {
            builder.row(TarantoolBundle.message("project.generator.template")) {
                cell(templateCombo)
            }
        }

        override fun setupProject(project: Project) {
            val base = checkNotNull(baseData) { "шаг с именем и расположением проекта отсутствует" }
            val dir = Path.of(base.path, base.name)
            TtScaffolder.scaffold(project, dir, templateCombo.selectedItem as String)
        }
    }
}
