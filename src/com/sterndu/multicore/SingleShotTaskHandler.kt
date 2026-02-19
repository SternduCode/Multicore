package com.sterndu.multicore

import java.util.*

object SingleShotTaskHandler: TaskHandler() {

    private val tasks: MutableList<Runnable> = LinkedList()

    init {
        MultiCore.addTaskHandler(this)
    }

    fun add(task: Runnable) {
        tasks.add(task)
    }

    override fun getTask(): Runnable? = tasks.removeFirstOrNull()

    override fun hasTask(): Boolean = tasks.isNotEmpty()
}