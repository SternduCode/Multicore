@file:JvmName("CustomMultiCore")
package com.sterndu.multicore

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import java.util.logging.Level

object CustomMultiCore {

	private val taskHandlers: MutableList<TaskHandler> = ArrayList()

	private val threads: MutableMap<Thread, CustomMultiCoreThreadState> = HashMap()

	private val queue = ArrayDeque<Pair<TaskHandler, Runnable>>()

	private val simThreadsLock = Any()

	var maxSimultaneousThreads = Runtime.getRuntime().availableProcessors()
		private set

	private val logger = LoggingUtil.getLogger("CustomMulticore")

	private val kernel = { state: CustomMultiCoreThreadState ->
		while (!shutdown.get() && !state.shouldShutdown) {
			val (taskHandler, trowingRunnable) = task ?: run {
				//logger.info("About to leave ${Thread.currentThread().name} active: $activeThreadsCount queued: $amountOfAvailableTasks hasTask: ${thread.hasTask} state: ${thread.state} states: ${threads.joinToString { "${it.name}: ${it.state}" }}")
				LockSupport.park()
				NullTaskHandler to NullTaskHandler.getTask()
			}
			val st = System.currentTimeMillis()
			state.hasTask = true
			try {
				trowingRunnable.run()
				taskHandler.addTime(System.currentTimeMillis() - st)
			} catch (e: Exception) {
				logger.log(Level.WARNING, "CustomMultiCore", e)
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

	init {
		repeat(Runtime.getRuntime().availableProcessors()) {
			val state = CustomMultiCoreThreadState()
			threads[makeThread("MultiCore-Worker=$threadNumber", state, kernel)] = state
			threadNumber++
		}
		Runtime.getRuntime().addShutdownHook(Thread { close() })
	}

	private val task: Pair<TaskHandler, Runnable>?
		get() {
			//logger.info("Im ${Thread.currentThread().name}")
			synchronized(queue) {
				synchronized(taskHandlers) {
					try {
						taskHandlers.forEach { taskHandler ->
							if (taskHandler is Updater) {
								queue.add(
									taskHandler to Runnable {
										taskHandler.taskInformationMap.map(Map.Entry<Any, Updater.Information>::value).forEach { information -> taskHandler.r(information) }
									}
								)
							} else {
								if (taskHandler.hasTask()) {
									taskHandler.getTask()?.let { task ->
										queue.add(taskHandler to task)
									}
								}
							}
						}
					} catch (e: Exception) {
						logger.log(Level.WARNING, "CustomMultiCore", e)
					}
				}

				val wantedChange = checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()

				//logger.finest("About to leave ${Thread.currentThread().name}: $wantedChange active: $activeThreadsCount queued: $amountOfAvailableTasks")

				return when {
					wantedChange >= 0 && queue.isNotEmpty() -> queue.removeFirst()
					wantedChange >= 0 -> NullTaskHandler to NullTaskHandler.getTask()
					activeThreadsCount > maxSimultaneousThreads -> {
						threads[Thread.currentThread()]?.let { it.shouldShutdown = true }
						NullTaskHandler to NullTaskHandler.getTask()
					}
					activeThreadsCount - amountOfExecutingThreads > 1 -> null
					else -> NullTaskHandler to NullTaskHandler.getTask()
				}
			}
		}


	private fun checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded(): Int {
		synchronized(simThreadsLock) {
			val threadsWanted = (amountOfAvailableTasks + amountOfExecutingThreads + 1).coerceAtMost(maxSimultaneousThreads)
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

	fun addTaskHandler(taskHandler: TaskHandler) {
		synchronized(taskHandlers) {
			taskHandlers.add(taskHandler)
		}
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()
	}

	fun close() {
		shutdown.set(true)
	}

	val amountOfAvailableTasks: Int get() = queue.size

	fun removeTaskHandler(taskHandler: TaskHandler): Boolean {
		synchronized(taskHandlers) {
			return taskHandlers.remove(taskHandler)
		}
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
