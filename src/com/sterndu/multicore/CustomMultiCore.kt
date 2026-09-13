@file:JvmName("CustomMultiCore")
package com.sterndu.multicore

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import java.util.logging.Level

// TODO convert to new structure like multicore
/*
 *
 * All added to tasks
 *
 *
 */

object CustomMultiCore: MulticoreSpec {

	private class ManagedTask(
		val key: String,
		millis: Long = 0,
		isFixedDelay: Boolean = false,
		nextRun: Long = 0,
		canRunSimultaneously: Boolean = false,
		val clazz: Class<*>,
		override val operation: () -> Unit,
	): Task(millis, isFixedDelay, nextRun, canRunSimultaneously)

	private val tasks: MutableList<Task> = CopyOnWriteArrayList()

	private val threads: MutableMap<Thread, CustomMultiCoreThreadState> = HashMap()

	private val queue = ConcurrentLinkedQueue<Pair<Task, () -> Unit>>()

	private val inFlightTasks: MutableList<Task> = CopyOnWriteArrayList()

	private val simThreadsLock = Any()

	var maxSimultaneousThreads = Runtime.getRuntime().availableProcessors()
		private set

	private val logger = LoggingUtil.getLogger("CustomMulticore")

	private val kernel = { state: CustomMultiCoreThreadState ->
		while (!shutdown.get() && !state.shouldShutdown) {
			val (task, runnable) = getTask() ?: run {
				//logger.info("About to leave ${Thread.currentThread().name} active: $activeThreadsCount queued: $amountOfAvailableTasks hasTask: ${thread.hasTask} state: ${thread.state} states: ${threads.joinToString { "${it.name}: ${it.state}" }}")
				LockSupport.park()
				NullTaskHandler to NullTaskHandler.internalOperation
			}
			state.hasTask = true
			val st = System.currentTimeMillis()

			if (task !is NullTaskHandler) {
				try {
					task.startTimes.add(st)
					runnable()
					task.runTimes.add(System.currentTimeMillis() - st)
				} catch (e: Exception) {
					logger.log(Level.WARNING, "CustomMultiCore", e)
					task.runTimes.add(System.currentTimeMillis() - st)
					task._exceptions.add(e)
				}
			}

			if (task in inFlightTasks) {
				if (task.isRepeating && task.isFixedDelay) {
					task.nextRun = System.currentTimeMillis() + task.millis
				}
				inFlightTasks.remove(task)
			}
			state.hasTask = false
			try {
				Thread.sleep(0, 1)
			} catch (e: InterruptedException) {
				logger.log(Level.WARNING, "CustomMultiCore", e)
			}
		}
	}

	private val shutdown: AtomicBoolean = AtomicBoolean(false)

	private var threadNumber: ULong = 0uL

	override fun start() {
		tasks.clear()
		queue.clear()
		inFlightTasks.clear()
		shutdown.set(false)
		Thread({
			while (!shutdown.get()) {
				try {
					val now = System.currentTimeMillis()
					for (task in tasks) {
						when (task) {
							is TaskHandler -> {
								if (task.hasTask) {
									if (task.isFixedDelay) {
										if (task !in inFlightTasks && (task.nextRun - now) <= 0) {
											inFlightTasks.add(task)
											queue.add(task to task.internalOperation)
										}
									} else {
										if (task.canRunSimultaneously) {
											if ((task.nextRun - now) <= 0) {
												task.nextRun += task.millis
												queue.add(task to task.internalOperation)
											}
										} else {
											if (task !in inFlightTasks && (task.nextRun - now) <= 0) {
												task.nextRun += task.millis
												inFlightTasks.add(task)
												queue.add(task to task.internalOperation)
											}
										}
									}
								}
							}
							else -> {
								if (task.isRepeating) {
									if (task.isFixedDelay) {
										if (task !in inFlightTasks && (task.nextRun - now) <= 0) {
											inFlightTasks.add(task)
											queue.add(task to task.internalOperation)
										}
									} else {
										if (task.canRunSimultaneously) {
											if ((task.nextRun - now) <= 0) {
												task.nextRun += task.millis
												queue.add(task to task.internalOperation)
											}
										} else {
											if (task !in inFlightTasks && (task.nextRun - now) <= 0) {
												task.nextRun += task.millis
												inFlightTasks.add(task)
												queue.add(task to task.internalOperation)
											}
										}
									}
								} else {
									if ((task.nextRun - now) <= 0) {
										tasks.remove(task)
										queue.add(task to task.internalOperation)
									}
								}
							}
						}
					}

					var budget = (maxSimultaneousThreads - amountOfQueuedTasks).coerceAtLeast(0)
					val greedy = tasks.filterIsInstance<TaskHandler>().filter { !it.isFixedDelay && it.canRunSimultaneously }
					var rr = 0
					while (budget > 0) {
						var added = false
						for (i in greedy.indices) {
							val h = greedy[(rr + i) % greedy.size]
							if (budget == 0) break
							if (h.hasTask && (h.nextRun - now) <= 0) {
								h.nextRun += h.millis
								queue.add(h to h.internalOperation)
								budget--
								added = true
							}
						}
						rr++
						if (!added) break   // nothing eligible this pass — avoid spinning
					}

					checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()

				} catch (e: Exception) {
					logger.log(Level.WARNING, "CustomMulticore ${e.javaClass.simpleName} ${e.message} ${e.cause}", e)
				}

				Thread.sleep(1)
			}
		}, "CustomMulticore-Housekeeping").apply {
			isDaemon = true
			start()
		}
	}

	init {
		repeat(Runtime.getRuntime().availableProcessors()) {
			val state = CustomMultiCoreThreadState()
			threads[makeThread("MultiCore-Worker=$threadNumber", state, kernel)] = state
			threadNumber++
		}

		Runtime.getRuntime().addShutdownHook(Thread { stop() })
	}

	private fun getTask(): Pair<Task, () -> Unit>? {
			//logger.info("Im ${Thread.currentThread().name}")
			val wantedChange = checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()

			//logger.finest("About to leave ${Thread.currentThread().name}: $wantedChange active: $activeThreadsCount queued: $amountOfAvailableTasks")

			return when {
				wantedChange >= 0 && queue.isNotEmpty() -> queue.poll()
				wantedChange >= 0 -> NullTaskHandler to NullTaskHandler.internalOperation
				activeThreadsCount > maxSimultaneousThreads -> {
					threads[Thread.currentThread()]?.let { it.shouldShutdown = true }
					NullTaskHandler to NullTaskHandler.internalOperation
				}
				activeThreadsCount - amountOfExecutingThreads > 1 -> null
				else -> NullTaskHandler to NullTaskHandler.internalOperation
			}
		}


	private fun checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded(): Int {
		synchronized(simThreadsLock) {
			val threadsWanted = (amountOfQueuedTasks + amountOfExecutingThreads).coerceAtMost(maxSimultaneousThreads)
			val activeThreadsCount = activeThreadsCount
			val wantedChange = threadsWanted - activeThreadsCount
			if (activeThreadsCount < threadsWanted) {
				startThreads(wantedChange)
			}
			return wantedChange
		}
	}

	private val activeThreadsCount: Int
		get() = synchronized(simThreadsLock) {
			threads.count { (thread, state) ->
				thread.isAlive && thread.state != Thread.State.WAITING && !state.shouldShutdown
			}
		}

	private val amountOfExecutingThreads: Int
		get() = synchronized(simThreadsLock) {
			threads.count { (thread, state) ->
				thread.isAlive && state.hasTask
			}
		}

	@Suppress("NOTHING_TO_INLINE")
	private inline fun getCallingClass(): Class<*> {
		return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
	}

	override fun scheduleTask(delay: Long, task: () -> Unit): Boolean {
		return tasks.add(ManagedTask(
			key = "",
			millis = 0,
			isFixedDelay = false,
			nextRun = System.currentTimeMillis() + delay,
			canRunSimultaneously = false,
			clazz = getCallingClass(),
			operation = task
		))
	}

	override fun scheduleTaskWithFixedDelay(
		key: String,
		delay: Long,
		millis: Long,
		task: () -> Unit
	): Boolean {
        return !(key.isBlank() || key in tasks.filterIsInstance<ManagedTask>().map { it.key }) && tasks.add(ManagedTask(
            key = key,
            millis = millis,
            isFixedDelay = true,
            nextRun = System.currentTimeMillis() + delay,
            canRunSimultaneously = false,
            clazz = getCallingClass(),
            operation = task
        ))
    }

	override fun scheduleTaskAtFixedRate(
		key: String,
		delay: Long,
		millis: Long,
		canRunSimultaneously: Boolean,
		task: () -> Unit
	): Boolean {
        return !(key.isBlank() || key in tasks.filterIsInstance<ManagedTask>().map { it.key }) && tasks.add(ManagedTask(
            key = key,
            millis = millis,
            isFixedDelay = false,
            nextRun = System.currentTimeMillis() + delay,
            canRunSimultaneously = canRunSimultaneously,
            clazz = getCallingClass(),
            operation = task
        ))
    }

	override fun removeTask(key: String): Boolean {
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

	override fun getAverageExecutionFrequency(key: String): Double? {
		val caller = getCallingClass()
		return tasks.filterIsInstance<ManagedTask>()
			.singleOrNull { it.clazz == caller && it.key == key }
			?.averageFrequency()
	}

	override fun getAverageExecutionTime(key: String): Double? {
		val caller = getCallingClass()
		return tasks.filterIsInstance<ManagedTask>()
			.singleOrNull { it.clazz == caller && it.key == key }
			?.averageRunTime()
	}

	override fun addTaskHandler(taskHandler: TaskHandler) {
		tasks.add(taskHandler)
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()
	}

	@Deprecated("Use stop() instead", ReplaceWith("stop()"))
	fun close() {
		stop()
	}

	val amountOfQueuedTasks: Int get() = queue.size // if this gets to expensive use atomic int instead to count size

	val amountOfTotalTasks: Int get() = tasks.size

	override fun removeTaskHandler(taskHandler: TaskHandler): Boolean {
		return tasks.remove(taskHandler)
	}

	override fun stop() {
		shutdown.set(true)
	}

	override val isStopping: Boolean
		get() = shutdown.get() && !isStopped

	override val isStopped: Boolean
		get() = threads.keys.none {
            it.state !in listOf(
                Thread.State.TERMINATED,
                Thread.State.WAITING,
                Thread.State.NEW
            )
        }

    private fun cleanupThreads() {
		synchronized(simThreadsLock) {
			threads.entries.removeIf { (thread, _) -> thread.state == Thread.State.TERMINATED }
		}
	}

	fun setMaxSimultaneousThreads(amount: Int) {
		synchronized(simThreadsLock) {
			maxSimultaneousThreads = amount.coerceAtLeast(1)
			cleanupThreads()
			checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()
		}
	}

	private fun startThreads(amount: Int) {
		synchronized(simThreadsLock) {
			var remaining = amount
			for (th in threads.filter { (thread, _) -> thread.state == Thread.State.WAITING }) {
				LockSupport.unpark(th.key)
				remaining--
				if (remaining == 0) break
			}
			if (remaining > 0) {
				cleanupThreads()
				for (th in threads.map(Map.Entry<Thread, CustomMultiCoreThreadState>::key).filterNot(Thread::isAlive)) {
					th.start()
					remaining--
					if (remaining == 0) break
				}
			}
			if (remaining > 0) {
				for (i in 1..remaining) {
					val state = CustomMultiCoreThreadState()
					val th = makeThread("MultiCore-Worker=$threadNumber", state, kernel)
					threads[th] = state
					threadNumber++
					th.start()
					remaining--
					if (remaining == 0) break
				}
			}
		}
	}
}
