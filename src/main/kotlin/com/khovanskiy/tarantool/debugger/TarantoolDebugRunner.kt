package com.khovanskiy.tarantool.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.run.TarantoolRunConfiguration

/**
 * Кнопка Debug у конфигурации «Tarantool».
 *
 * Раньше её не было вовсе: графическую отладку давал только сценарий
 * «допиши в код require('emmy_debug'), запусти с переменной окружения,
 * потом вручную нажми Debug у отдельной конфигурации». Теперь всё делает
 * раннер: скрипт стартует с чанком -e, который поднимает отладчик до
 * первой строки пользовательского кода, а IDE подключается сама, как
 * только порт открыт. Пользовательский файл при этом не меняется.
 */
class TarantoolDebugRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID &&
            profile is TarantoolRunConfiguration &&
            profile.useEmmyDebugger

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val launch = environment.getUserData(DebugLaunch.KEY)
        val result = state.execute(environment.executor, this) ?: return null
        val descriptor = RunContentBuilder(result, environment).showRunContent(environment.contentToReuse)

        if (launch == null) {
            return descriptor
        }
        if (!EmmySession.available()) {
            launch.markIdeConnected()
            DebugAttach.notify(environment.project, TarantoolBundle.message("debug.error.no.emmylua"))
            return descriptor
        }

        val handler = result.processHandler
        DebugAttach.whenListening(
            project = environment.project,
            launch = launch,
            sessionName = TarantoolBundle.message("debug.session.name", environment.runProfile.name),
            alive = { handler == null || !handler.isProcessTerminated },
        )
        return descriptor
    }

    private companion object {
        const val RUNNER_ID = "TarantoolEmmyDebugRunner"
    }
}
