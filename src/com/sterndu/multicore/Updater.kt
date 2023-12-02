@file:JvmName("Updater")
package com.sterndu.multicore

import com.sterndu.multicore.MultiCore.TaskHandler
import com.sterndu.util.interfaces.ThrowingConsumer
import com.sterndu.util.interfaces.ThrowingRunnable
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

object Updater : TaskHandler() {
	/**
	 * Holds data about an updater task
	 */
	internal data class Information(
		val millis: Long = 0L,
		val times: MutableList<Long> = ArrayList(),
		val clazz: Class<*>,
		val tr: ThrowingRunnable
	) {
		fun avgFreq(): Double {
			return if (times.size < 2) Double.POSITIVE_INFINITY else
				times.mapIndexed { index, value -> if (index > 0) value - times[index - 1] else 0 }.drop(1).average()
		}
	}

	internal val logger: Logger = LoggingUtil.getLogger("Updater")

	private val interrupted: MutableList<Exception> = ArrayList()

	internal val taskInformationMap = ConcurrentHashMap<Any, Information>()

	internal val r: (Information) -> Unit = { information ->
		try {
			// DEBUG logger.info("Running task ${taskInformationMap.entries.first { it.value === information }.key}")
			val times = information.times
			val lastRun = if (times.isEmpty()) 0 else times.last()
			val curr = System.currentTimeMillis()
			if (curr - lastRun >= information.millis) {
				information.tr.run()
				if (times.size >= 20) times.removeAt(0)
				times.add(curr)
			}
		} catch (e: Exception) {
			synchronized(interrupted) {
				interrupted.add(e)
			}
		}
	}

	/**
	 * Instantiates a new updater.
	 */
	init {
		MultiCore.addTaskHandler(this)
	}

	/**
	 * @param key the key
	 * @param i the i
	 */
	private fun add(key: Any, i: Information) {
		taskInformationMap[key] = i
	}

	/**
	 * Gets the task.
	 *
	 * @return the task
	 */
	override fun getTask(): ThrowingConsumer<TaskHandler> {
		return ThrowingConsumer {  }
	}

	/**
	 * Checks for task.
	 *
	 * @return true, if successful
	 */
	override fun hasTask(): Boolean {
		return taskInformationMap.isNotEmpty()
	}

	/**
	 * Adds a task to be run periodically
	 *
	 * @param R the type of the task, must be either ThrowingRunnable or Runnable
	 * @param r the task to run
	 * @param key the key of the task
	 */
	fun <R> add(r: R, key: Any) {
		try {
			val caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
			when (r) {
				is ThrowingRunnable -> add(key, Information(clazz = caller, tr = r))
				is Runnable -> add(key, Information(clazz = caller, tr = r::run))
				else -> throw NotImplementedError()
			}
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
		}
	}

	/**
	 * Adds a task to be run periodically after 'millis' milliseconds
	 *
	 * @param R the type of the task, must be either ThrowingRunnable or Runnable
	 * @param r the task to run
	 * @param key the key of the task
	 * @param millis the millis
	 */
	fun <R> add(r: R, key: Any, millis: Long) {
		try {
			val caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
			when (r) {
				is ThrowingRunnable -> add(key, Information(millis, clazz = caller, tr = r))
				is Runnable -> add(key, Information(millis, clazz = caller, tr = r::run))
				else -> throw NotImplementedError()
			}
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
		}
	}

	/**
	 * Change target freq.
	 *
	 * @param key the key
	 * @param millis the millis
	 * @return true, if successful
	 */
	fun changeTargetFreq(key: Any, millis: Long): Boolean {
		return try {
			val caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
			val i = taskInformationMap[key]
			(i != null && i.clazz == caller) && taskInformationMap.replace(key, Information(millis, i.times, caller, i.tr) ) != null
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
			false
		}
	}

	/**
	 * Gets the avg exec freq.
	 *
	 * @param key the key
	 * @return the avg exec freq
	 */
	fun getAvgExecFreq(key: Any): Double {
		return try {
			val caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
			val i = taskInformationMap[key]
			if (i == null || i.clazz != caller) 0.0 else i.avgFreq()
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
			0.0
		}
	}

	val exceptions: List<Exception>
		get() = interrupted

	/**
	 * Removes the task corresponding to the given key
	 *
	 * @param key the key of the task to remove
	 * @return true, if successful
	 */
	fun remove(key: Any): Boolean {
		return try {
			val caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
			val i = taskInformationMap[key]
			logger.fine("$key $caller $i")
			(i != null && i.clazz == caller) && taskInformationMap.remove(key) != null
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
			false
		}
	}

	/**
	 * Removes the all.
	 */
	fun removeAll() {
		try {
			val caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
			taskInformationMap.entries.removeIf { (_, value): Map.Entry<Any, Information> -> value.clazz == caller }
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
		}
	}

	fun printAll(logger: Logger) {
		this.taskInformationMap.forEach { (key, value) -> logger.fine("Print All: $key $value")}
	}

}
