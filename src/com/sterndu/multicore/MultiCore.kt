@file:JvmName("MultiCore")
package com.sterndu.multicore

import com.sterndu.util.Entry
import com.sterndu.util.interfaces.ThrowingConsumer
import com.sterndu.util.interfaces.ThrowingRunnable
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

object MultiCore {
	/**
	 * The Class TaskHandler.
	 */
	abstract class TaskHandler {

		var prioMult: Double

		var lastAverageTime: Double
			protected set

		private val _times: MutableList<Long>

		val times: List<Long>
			get() = _times.toList()


		protected constructor() {
			prioMult = .1
			lastAverageTime = .0
			_times = ArrayList()
		}

		protected constructor(prioMult: Double) {
			this.prioMult = prioMult
			lastAverageTime = .0
			_times = ArrayList()
		}

		fun addTime(time: Long) {
			synchronized(_times) {
				_times.add(time)
				if (_times.size > 30) _times.removeAt(0)
			}
		}

		abstract fun getTask(): ThrowingConsumer<TaskHandler>?

		abstract fun hasTask(): Boolean
		val averageTime: Double
			get() {
				synchronized(_times) {
					return _times.average().also { lastAverageTime = it }
				}
			}

	}

	/** The ses.  */
	private val ses = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors()) as ScheduledThreadPoolExecutor

	private val scheduledTasks: MutableMap<Any, ScheduledFuture<*>> = HashMap()

	private val closeRequested = AtomicBoolean(false)

	/** The count.  */
	private var count = 0

	/** The task handler.  */
	private val taskHandler: MutableList<TaskHandler> = ArrayList()

	private val r: (TaskHandler, ThrowingConsumer<TaskHandler>) -> Unit = { key, data ->
		val st = System.currentTimeMillis()
		try {
			data.accept(key)
			var et = System.currentTimeMillis()
			et -= st
			key.addTime(et)
			key.averageTime
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	init {
		ses.maximumPoolSize = Runtime.getRuntime().availableProcessors()
		ses.scheduleWithFixedDelay({
			// DEBUG Updater.logger.info("Doing House keeping")
			try {
				for (taskHandler in taskHandler) {
					when (taskHandler) {
						is Updater -> {
							taskHandler.taskInformationMap
								.map(Map.Entry<Any, Updater.Information>::value)
								.filter { it.tr !in scheduledTasks }
								.forEach { information ->
									val future = ses.scheduleWithFixedDelay(
										{ r(taskHandler) { taskHandler.r(information) } },
										0,
										information.millis.coerceAtLeast(1),
										TimeUnit.MILLISECONDS
									)

									scheduledTasks[information.tr] = future
								}
							cleanupNonExistentTasks(taskHandler)
						}

						else -> {
							if (taskHandler.hasTask()) {
								val task = taskHandler.getTask()
								if (task != null) {
									val future = ses.schedule({ r(taskHandler, task) }, 0, TimeUnit.MILLISECONDS)

									scheduledTasks[task] = future
								}
							}
						}
					}
				}
				if (scheduledTasks.entries.removeIf { (_, future) ->
					future.isDone || future.isCancelled
				}) {
					// DEBUG Updater.logger.info("Removed a task")
				}
			} catch (e: Exception) {
				Updater.logger.log(Level.WARNING, "MultiCore ${e.javaClass.simpleName} ${e.message} ${e.cause}", e)
			}
		}, 0, 1, TimeUnit.MILLISECONDS)
		
		Runtime.getRuntime().addShutdownHook(Thread { close() })
	}

	private fun cleanupNonExistentTasks(taskHandler: Updater) {
		scheduledTasks
			.map(Map.Entry<Any, ScheduledFuture<*>>::key)
			.filterIsInstance<ThrowingRunnable>()
			.filterNot {
				taskHandler.taskInformationMap
					.map(Map.Entry<Any, Updater.Information>::value)
					.map(Updater.Information::tr)
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

	private fun reSort() {
		synchronized(this.taskHandler) {
			this.taskHandler.sortWith { d1: TaskHandler, d2: TaskHandler ->
				(d2.lastAverageTime * d2.prioMult).compareTo(d1.lastAverageTime * d1.prioMult)
			}
		}
	}

	fun addTaskHandler(taskHandler: TaskHandler) {
		synchronized(this.taskHandler) {
			this.taskHandler.add(taskHandler)
			reSort()
		}
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()
	}

	fun close() {
		ses.shutdown()
	}

	val amountOfAvailableTasks: Int
		get() {
			synchronized(this.taskHandler) {
				return this.taskHandler.parallelStream()
					.mapToInt { t: TaskHandler -> if (t.hasTask()) 1 else 0 }
					.sum()
			}
		}

	fun getSimultaneousThreads(): Int {
		return ses.maximumPoolSize
	}

	fun getActiveThreads() = ses.activeCount

	fun removeTaskHandler(taskHandler: TaskHandler): Boolean {
		synchronized(this.taskHandler) {
			val b = this.taskHandler.remove(taskHandler)
			reSort()
			return b
		}
	}

	@Synchronized
	fun setSimultaneousThreads(amount: Int) {
		ses.maximumPoolSize = amount
		ses.corePoolSize = amount
	}
}
