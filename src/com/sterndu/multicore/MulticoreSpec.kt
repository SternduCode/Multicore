package com.sterndu.multicore

interface MulticoreSpec {
    fun scheduleTask(delay: Long = 0, task: () -> Unit): Boolean
    fun scheduleTaskWithFixedDelay(key: String, delay: Long = 0, millis: Long = 0, task: () -> Unit): Boolean
    fun scheduleTaskAtFixedRate(key: String, delay: Long = 0, millis: Long = 0, canRunSimultaneously: Boolean = false, task: () -> Unit): Boolean
    fun removeTask(key: String): Boolean
    fun getAverageExecutionFrequency(key: String): Double?
    fun getAverageExecutionTime(key: String): Double?
    fun addTaskHandler(taskHandler: TaskHandler)
    fun removeTaskHandler(taskHandler: TaskHandler): Boolean
    fun start()
    fun stop()
    val isStopping: Boolean
    val isStopped: Boolean
}