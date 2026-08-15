package com.khovanskiy.tarantool.debugger

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.khovanskiy.tarantool.TarantoolBundle
import java.util.concurrent.TimeUnit

/**
 * Общая половина обоих сценариев отладки: дождаться, пока запущенный
 * процесс Tarantool откроет порт, и подключить к нему IDE.
 *
 * Ожидание вынесено в фоновую задачу: процесс поднимается не мгновенно
 * (кластеру нужно применить конфигурацию), а держать в это время EDT
 * нельзя. Порт не «прощупывается» подключением — см. DebugLaunch.
 */
object DebugAttach {

    /**
     * @param sessionName имя сессии в окне Debug
     * @param alive       жив ли процесс: если он упал, ждать больше нечего
     */
    fun whenListening(
        project: Project,
        launch: DebugLaunch,
        sessionName: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        alive: () -> Boolean = { true },
    ) {
        object : Task.Backgroundable(
            project,
            TarantoolBundle.message("debug.progress.waiting", launch.port),
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                val listening = launch.awaitListening(timeoutMillis) { alive() && !indicator.isCanceled }
                ApplicationManager.getApplication().invokeLater(
                    {
                        if (listening) {
                            attach(project, launch, sessionName)
                        } else if (!indicator.isCanceled) {
                            launch.cleanup()
                            notify(project, TarantoolBundle.message("debug.error.not.listening", launch.port))
                        }
                    },
                    ModalityState.any(),
                )
            }
        }.queue()
    }

    private fun attach(project: Project, launch: DebugLaunch, sessionName: String) {
        val error = EmmySession.start(project, sessionName, launch.host, launch.port) {
            // Сессия стартовала — отпускаем придержанный запуск приложения,
            // но не в тот же миг: точки останова IDE отправляет агенту сразу
            // после подключения, и приложение, отпущенное раньше, успело бы
            // проскочить стартовый код с ещё не установленными точками.
            AppExecutorUtil.getAppScheduledExecutorService()
                .schedule({ launch.markIdeConnected() }, BREAKPOINTS_DELIVERY_MILLIS, TimeUnit.MILLISECONDS)
        }
        if (error != null) {
            // Без отладчика приложение всё равно должно стартовать:
            // маркер отпускает загрузчик, не дожидаясь таймаута.
            launch.markIdeConnected()
            notify(project, error)
        }
    }

    fun notify(project: Project, content: String, type: NotificationType = NotificationType.WARNING) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(TarantoolBundle.message("debug.notification.title"), content, type)
            .notify(project)
    }

    private const val DEFAULT_TIMEOUT_MILLIS = 60_000L

    /** Запас на доставку точек останова агенту по локальному сокету. */
    private const val BREAKPOINTS_DELIVERY_MILLIS = 500L
}
