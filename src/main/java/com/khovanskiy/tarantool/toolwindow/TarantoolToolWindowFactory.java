package com.khovanskiy.tarantool.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

/**
 * Панель «Tarantool»: состояние инстансов окружения tt с действиями
 * запуска, остановки, перезапуска и подключения консоли.
 *
 * Java, а не Kotlin: Kotlin-реализация интерфейса с default-методами
 * генерирует мосты, которые Plugin Verifier считает переопределением
 * устаревших и экспериментальных методов.
 */
public final class TarantoolToolWindowFactory implements ToolWindowFactory, DumbAware {

    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        InstancesPanel panel = new InstancesPanel(project);
        Content content = toolWindow.getContentManager().getFactory().createContent(panel, "", false);
        // Панель — Disposable: с ней умирает и таймер автообновления.
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
        panel.refresh();
    }
}
