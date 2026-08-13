package com.khovanskiy.tarantool.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import java.util.regex.Pattern

/**
 * Делает кликабельными упоминания Lua-файлов в выводе Tarantool.
 *
 * Покрывает оба формата: строки журнала («main/104/playground.lua
 * playground.lua:40 I> ...») и трейсбеки («src/model/users.lua:25: in
 * function 'get'»). Логгер печатает имя файла без каталога, поэтому помимо
 * рабочего каталога файл ищется по имени в индексе проекта.
 */
class TarantoolTracebackFilter(
    private val project: Project,
    private val workingDirectory: String,
) : Filter {

    private val pattern = Pattern.compile("""([A-Za-z0-9_\-./\\]+\.lua):(\d+)""")

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = pattern.matcher(line)
        val items = mutableListOf<Filter.ResultItem>()
        val lineStart = entireLength - line.length

        while (matcher.find()) {
            val path = matcher.group(1)
            val lineNumber = matcher.group(2).toIntOrNull() ?: continue
            val file = resolve(path) ?: continue
            items += Filter.ResultItem(
                lineStart + matcher.start(),
                lineStart + matcher.end(),
                OpenFileHyperlinkInfo(project, file, lineNumber - 1),
            )
        }

        return if (items.isEmpty()) null else Filter.Result(items)
    }

    private fun resolve(path: String): VirtualFile? {
        val fs = LocalFileSystem.getInstance()

        if (File(path).isAbsolute) {
            return fs.findFileByPath(path)
        }

        val relative = File(workingDirectory, path)
        if (relative.isFile) {
            fs.findFileByPath(relative.path)?.let { return it }
        }

        // Логгер Tarantool печатает только имя файла — ищем по индексу.
        // Однозначным считается единственное совпадение либо совпадение
        // по суффиксу пути.
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        val candidates = ApplicationManager.getApplication().runReadAction(
            Computable<Collection<VirtualFile>> {
                if (DumbService.isDumb(project)) {
                    emptyList()
                } else {
                    FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
                }
            }
        )
        candidates.singleOrNull()?.let { return it }
        return candidates.firstOrNull { it.path.endsWith("/$path") }
    }
}
