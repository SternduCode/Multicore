@file:JvmName("MultiCore")
package com.sterndu.multicore

import java.util.concurrent.*
import java.util.logging.Level

object MultiCore {

	val logger = LoggingUtil.getLogger("MultiCore")

	private val ses = Executors.newScheduledThreadPool(
		Runtime.getRuntime().availableProcessors(),
		Thread.ofVirtual().factory()
	) as ScheduledThreadPoolExecutor

	private val taskHandlers: MutableList<TaskHandler> = CopyOnWriteArrayList()

	private val scheduledTasks: MutableMap<Any, ScheduledFuture<*>> = HashMap()

	private val kernel: (TaskHandler, Runnable) -> Unit = { key, data ->
		val st = System.currentTimeMillis()
		try {
			data.run()
			var et = System.currentTimeMillis()
			et -= st
			key.addTime(et)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	init {
		ses.allowCoreThreadTimeOut(true)
		ses.scheduleWithFixedDelay({
			// DEBUG Updater.logger.info("Doing House keeping")
			try {
				for (taskHandler in taskHandlers) {
					when (taskHandler) {
						is RepeatingTaskHandler -> {
							taskHandler.taskInformationMap
								.map(Map.Entry<Any, RepeatingTaskHandler.Information>::value)
								.filter { it.runnable !in scheduledTasks }
								.forEach { information ->
									val future = ses.scheduleWithFixedDelay(
										{ kernel(taskHandler) { taskHandler.r(information) } },
										0,
										information.millis.coerceAtLeast(1),
										TimeUnit.MILLISECONDS
									)

									scheduledTasks[information.runnable] = future
								}
							cleanupNonExistentTasks(taskHandler)
						}

						else -> {
							while (taskHandler.hasTask()) {
								taskHandler.internalGetTask()?.let { task ->
									scheduledTasks[task] = ses.schedule({ kernel(taskHandler, task) }, 0, TimeUnit.MILLISECONDS)
								}
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

	private fun cleanupNonExistentTasks(taskHandler: Updater) {
		scheduledTasks
			.map(Map.Entry<Any, ScheduledFuture<*>>::key)
			.filterIsInstance<Runnable>()
			.filterNot {
				taskHandler.taskInformationMap
					.map(Map.Entry<Any, RepeatingTaskHandler.Information>::value)
					.map(RepeatingTaskHandler.Information::runnable)
					.any(it::equals)
			}
			.forEach {
				// DEBUG Updater.logger.info("Cleaned a task")
				scheduledTasks[it]!!.cancel(false)
				scheduledTasks.remove(it)
			}
	}

	private fun checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded(): Int {
		return amountOfAvailableTasks
	}

	private val activeThreadsCount: Int
		get() = ses.activeCount

	fun addTaskHandler(taskHandler: TaskHandler) {
		this.taskHandlers.add(taskHandler)
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()
	}

	fun stop() {
		ses.shutdown()
	}

	val amountOfAvailableTasks: Int
		get() {
			return this.taskHandlers.parallelStream()
				.mapToInt { t: TaskHandler -> if (t.hasTask()) 1 else 0 }
				.sum()
		}

	fun getSimultaneousThreads(): Int {
		return ses.maximumPoolSize
	}

	fun getActiveThreads() = ses.activeCount

	fun removeTaskHandler(taskHandler: TaskHandler): Boolean {
		return taskHandlers.remove(taskHandler)
	}

	@Synchronized
	fun setSimultaneousThreads(amount: Int) {
		ses.corePoolSize = amount
	}
}
