package com.sterndu.multicore

object SingleShotTaskHandler: TaskHandler() {

    @Deprecated("Use the function on Multicore", ReplaceWith("Multicore.scheduleTask(task)"))
    @Suppress("NOTHING_TO_INLINE")
    inline fun add(task: Runnable) {
        Multicore.scheduleTask(task = task::run)
    }

    @Deprecated("Use the function on Multicore", ReplaceWith("Multicore.scheduleTask(task)"))
    @Suppress("NOTHING_TO_INLINE")
    inline fun add(noinline task: () -> Unit) {
        Multicore.scheduleTask(task = task)
    }

    override fun getTask(): () -> Unit = NullTaskHandler.nullTask

    override val hasTask: Boolean = false
}