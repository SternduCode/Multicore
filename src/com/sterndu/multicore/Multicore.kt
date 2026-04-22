@file:JvmName("MultiCore")
package com.sterndu.multicore

import java.util.concurrent.*
import java.util.logging.Level

abstract class Task(
	val millis: Long = 0,
	val isFixedDelay: Boolean = false,
	var nextRun: Long = 0,
	val canRunSimultaneously: Boolean = false,
) {

	internal val startTimes: MutableList<Long> = mutableListOf(0)
	internal val runTimes: MutableList<Long> = mutableListOf()
	protected abstract val task: () -> Unit

	init {
		require(millis >= 0) { "Millis cannot be negative." }
		require(nextRun >= 0) { "Next run cannot be negative." }
	}

	fun averageFrequency(): Double {
		return if (startTimes.size < 2) Double.POSITIVE_INFINITY else
			startTimes.mapIndexed { index, value -> if (index > 0) value - startTimes[index - 1] else 0 }.drop(1).average()
	}

	fun averageRunTime(): Double {
		return runTimes.average()
	}

	val isRepeating: Boolean get() = millis != 0L

	internal val internalTask: () -> Unit get() = task
}

typealias MultiCore = Multicore

object Multicore {

	private class ManagedTask(
		val key: String,
		millis: Long = 0,
		isFixedDelay: Boolean = false,
		nextRun: Long = 0,
		canRunSimultaneously: Boolean = false,
		val clazz: Class<*>,
		override val task: () -> Unit,
	): Task(millis, isFixedDelay, nextRun, canRunSimultaneously)

	val logger = LoggingUtil.getLogger("MultiCore")

	private val ses = Executors.newScheduledThreadPool(
		Runtime.getRuntime().availableProcessors(),
		Thread.ofVirtual().factory()
	) as ScheduledThreadPoolExecutor

	private val tasks: MutableList<Task> = CopyOnWriteArrayList()
	private val scheduledTasks: MutableMap<Any, ScheduledFuture<*>> = HashMap()

	private val proxyKernel: (Task, () -> Unit) -> Unit = { task, runnable ->
		val st = System.currentTimeMillis()
		try {
			task.startTimes.add(st)
			runnable()
			var et = System.currentTimeMillis()
			et -= st
			task.runTimes.add(et)
			while (task.startTimes.size >= 20) {
				task.startTimes.removeAt(0)
			}
			while (task.runTimes.size >= 20) {
				task.runTimes.removeAt(0)
			}
		} catch (e: Exception) {
			logger.log(Level.WARNING, "Multicore", e)
		}
	}

	private val kernel: (Task) -> Unit = { task ->
		proxyKernel(task, task.internalTask)
	}

	init {
		ses.allowCoreThreadTimeOut(true)
		ses.scheduleWithFixedDelay({
			// DEBUG logger.info("Doing House keeping")
			try {
				for (task in tasks) {
					when (task) {
						is TaskHandler -> {
							while (task.hasTask()) {
								ses.schedule(
									{ proxyKernel(task, task.internalTask) },
									(task.nextRun - System.currentTimeMillis()).coerceAtLeast(0),
									TimeUnit.MILLISECONDS,
								)
							}
						}
						else -> {
							if (task.isRepeating) {
								val future = if (task.isFixedDelay) ses.scheduleWithFixedDelay(
									{ kernel(task) },
									(task.nextRun - System.currentTimeMillis()).coerceAtLeast(0),
									task.millis.coerceAtLeast(1),
									TimeUnit.MILLISECONDS
								) else {
									if (task.canRunSimultaneously) {
										if ((task.nextRun - System.currentTimeMillis()) <= 0) {
											task.nextRun += task.millis
											ses.schedule(
												{ kernel(task) },
												0,
												TimeUnit.MILLISECONDS,
											)
										} else {
											null
										}
									} else {
										ses.scheduleAtFixedRate(
											{ kernel(task) },
											(task.nextRun - System.currentTimeMillis()).coerceAtLeast(0),
											task.millis.coerceAtLeast(1),
											TimeUnit.MILLISECONDS
										)
									}
								}

								if (future != null) {
									scheduledTasks[task] = future
								}
							} else {
								ses.schedule(
									{ kernel(task) },
									(task.nextRun - System.currentTimeMillis()).coerceAtLeast(0),
									TimeUnit.MILLISECONDS,
								)
								tasks.remove(task)
							}
						}
					}
				}
				if (scheduledTasks.entries.removeIf { (_, future) -> future.isDone || future.isCancelled } && "true" == System.getProperty("debug")) {
					logger.info("Removed a task")
				}
			} catch (e: Exception) {
				logger.log(Level.WARNING, "MultiCore ${e.javaClass.simpleName} ${e.message} ${e.cause}", e)
			}
		}, 0, 1, TimeUnit.MILLISECONDS)
		
		Runtime.getRuntime().addShutdownHook(Thread { stop() })
	}

	fun scheduleTask(delay: Long = 0, task: () -> Unit): Boolean {
		return tasks.add(ManagedTask(
			key = "",
			millis = 0,
			isFixedDelay = false,
			nextRun = System.currentTimeMillis() + delay,
			canRunSimultaneously = false,
			clazz = getCallingClass(),
			task = task
		))
	}

	fun scheduleTaskWithFixedDelay(key: String, delay: Long = 0, millis: Long = 0, task: () -> Unit): Boolean {
		if (key.isBlank() || key in tasks.filterIsInstance<ManagedTask>().map { it.key }) {
			return false
		}
		return tasks.add(ManagedTask(
			key = key,
			millis = millis,
			isFixedDelay = true,
			nextRun = System.currentTimeMillis() + delay,
			canRunSimultaneously = false,
			clazz = getCallingClass(),
			task = task
		))
	}

	fun scheduleTaskAtFixedRate(key: String, delay: Long = 0, millis: Long = 0, canRunSimultaneously: Boolean = false, task: () -> Unit): Boolean {
		if (key.isBlank() || key in tasks.filterIsInstance<ManagedTask>().map { it.key }) {
			return false
		}
		return tasks.add(ManagedTask(
			key = key,
			millis = millis,
			isFixedDelay = false,
			nextRun = System.currentTimeMillis() + delay,
			canRunSimultaneously = canRunSimultaneously,
			clazz = getCallingClass(),
			task = task
		))
	}

	fun removeTask(key: String): Boolean {
		var result = false
		val caller = getCallingClass()
        tasks.filterIsInstance<ManagedTask>()
			.singleOrNull { it.clazz == caller && it.key == key }
			?.let {
				result = tasks.remove(it)
				logger.fine("remove $key $caller $it")
			}

		return result
	}

	fun getAverageExecutionFrequency(key: String): Double? {
		val caller = getCallingClass()
		return tasks.filterIsInstance<ManagedTask>()
			.singleOrNull { it.clazz == caller && it.key == key }
			?.averageFrequency()
	}

	fun getAverageExecutionTime(key: String): Double? {
		val caller = getCallingClass()
        return tasks.filterIsInstance<ManagedTask>()
			.singleOrNull { it.clazz == caller && it.key == key }
			?.averageRunTime()
	}

	@Suppress("NOTHING_TO_INLINE")
	private inline fun getCallingClass(): Class<*> {
		return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
	}

	private fun checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded(): Int {
		return amountOfAvailableTasks
	}

	private val activeThreadsCount: Int
		get() = ses.activeCount

	fun addTaskHandler(taskHandler: TaskHandler) {
		this.tasks.add(taskHandler)
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()
	}

	fun stop() {
		ses.shutdown()
	}

	val amountOfAvailableTasks: Int
		get() {
			return this.scheduledTasks.size
		}

	fun getSimultaneousThreads(): Int {
		return ses.maximumPoolSize
	}

	fun getActiveThreads() = ses.activeCount

	fun removeTaskHandler(taskHandler: TaskHandler): Boolean {
		return tasks.remove(taskHandler)
	}

	@Synchronized
	fun setSimultaneousThreads(amount: Int) {
		ses.corePoolSize = amount
	}
}
