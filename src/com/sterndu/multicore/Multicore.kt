@file:JvmName("MulticoreKt")
package com.sterndu.multicore

import java.util.concurrent.*
import java.util.logging.Level

class CircularFixedSizeBuffer<E>(
	val capacity: Int
): List<E> {
	private val internalData: ArrayDeque<E> = ArrayDeque(capacity)
	override val size: Int get() = internalData.size
	override fun isEmpty(): Boolean = internalData.isEmpty()
	override fun contains(element: E): Boolean = internalData.contains(element)
	override fun iterator(): Iterator<E> = internalData.iterator()
	override fun containsAll(elements: Collection<E>): Boolean = internalData.containsAll(elements)
	override fun get(index: Int): E = internalData.get(index)
	override fun indexOf(element: E): Int = internalData.indexOf(element)
	override fun lastIndexOf(element: E): Int = internalData.lastIndexOf(element)
	override fun listIterator(): ListIterator<E> = internalData.listIterator()
	override fun listIterator(index: Int): ListIterator<E> = internalData.listIterator(index)
	override fun subList(fromIndex: Int, toIndex: Int): List<E> = internalData.subList(fromIndex, toIndex)

	fun add(element: E) {
		if (size >= capacity) {
			internalData.removeFirst()
		}
		internalData.addLast(element)
	}
}

abstract class Task(
	open val millis: Long = 0,
	val isFixedDelay: Boolean = false,
	open var nextRun: Long = 0,
	val canRunSimultaneously: Boolean = false,
) {

	internal val startTimes: CircularFixedSizeBuffer<Long> = CircularFixedSizeBuffer(20)
	internal val runTimes: CircularFixedSizeBuffer<Long> = CircularFixedSizeBuffer(20)
	internal val _exceptions: CircularFixedSizeBuffer<Exception> = CircularFixedSizeBuffer(10)
	val exceptions: List<Exception> get() = _exceptions.toList()
	protected abstract val operation: () -> Unit

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

	internal val internalOperation: () -> Unit get() = operation
}

@Deprecated("Use Multicore instead", ReplaceWith("Multicore"))
typealias MultiCore = Multicore

object Multicore: MulticoreSpec {

	private class ManagedTask(
		val key: String,
		millis: Long = 0,
		isFixedDelay: Boolean = false,
		nextRun: Long = 0,
		canRunSimultaneously: Boolean = false,
		val clazz: Class<*>,
		override val operation: () -> Unit,
	): Task(millis, isFixedDelay, nextRun, canRunSimultaneously)

	private lateinit var ses: ScheduledThreadPoolExecutor

	private val tasks: MutableList<Task> = CopyOnWriteArrayList()
	private val scheduledTasks: MutableMap<Any, ScheduledFuture<*>> = HashMap()

	val logger = LoggingUtil.getLogger("MultiCore")

	private val proxyKernel: (Task, () -> Unit) -> Unit = { task, runnable ->
		val st = System.currentTimeMillis()
		try {
			task.startTimes.add(st)
			runnable()
			task.runTimes.add(System.currentTimeMillis() - st)
		} catch (e: Exception) {
			logger.log(Level.WARNING, "Multicore", e)
			task.runTimes.add(System.currentTimeMillis() - st)
			task._exceptions.add(e)
		}
	}

	private val kernel: (Task) -> Unit = { task ->
		proxyKernel(task, task.internalOperation)
	}


	override fun start() {
		tasks.clear()
		scheduledTasks.clear()

		ses = Executors.newScheduledThreadPool(
			Runtime.getRuntime().availableProcessors(),
			Thread.ofVirtual().factory()
		) as ScheduledThreadPoolExecutor
		ses.allowCoreThreadTimeOut(true)
		ses.scheduleWithFixedDelay({
			// DEBUG logger.info("Doing House keeping")
			try {
				for (task in tasks) {
					when (task) {
						is TaskHandler -> {
							while (task.hasTask) {
								val internalOperation = task.internalOperation
								ses.schedule(
									{ proxyKernel(task, internalOperation) },
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
	}

	init {
		start()
		
		Runtime.getRuntime().addShutdownHook(Thread { stop() })
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

	override fun scheduleTaskWithFixedDelay(key: String, delay: Long, millis: Long, task: () -> Unit): Boolean {
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

	override fun scheduleTaskAtFixedRate(key: String, delay: Long, millis: Long, canRunSimultaneously: Boolean, task: () -> Unit): Boolean {
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

	@Suppress("NOTHING_TO_INLINE")
	private inline fun getCallingClass(): Class<*> {
		return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
	}

	private fun checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded(): Int {
		return amountOfAvailableTasks
	}

	val activeThreadsCount: Int get() = ses.activeCount

	val amountOfAvailableTasks: Int get() = scheduledTasks.size

	val simultaneousThreadsCount: Int get() = ses.maximumPoolSize

	override fun addTaskHandler(taskHandler: TaskHandler) {
		tasks.add(taskHandler)
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()
	}

	override fun removeTaskHandler(taskHandler: TaskHandler): Boolean {
		return tasks.remove(taskHandler)
	}

	override fun stop() {
		ses.shutdown()
	}

	override val isStopping: Boolean get() = ses.isTerminating
	override val isStopped: Boolean get() = ses.isTerminated

	@Synchronized
	fun setSimultaneousThreads(amount: Int) {
		ses.corePoolSize = amount
	}
}
