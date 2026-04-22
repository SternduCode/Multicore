@file:JvmName("Updater")
package com.sterndu.multicore

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

@Deprecated("Use RepeatingTaskHandler instead", ReplaceWith("RepeatingTaskHandler"))
typealias Updater = RepeatingTaskHandler

@Deprecated("Use Multicore instead", ReplaceWith("MultiCore"))
object RepeatingTaskHandler: TaskHandler() {

	internal data class Information(
		val millis: Long = 1L,
		val times: MutableList<Long> = mutableListOf(0),
		val clazz: Class<*>,
		val runnable: Runnable
	)

	private val logger: Logger = LoggingUtil.getLogger("RepeatingTaskHandler")

	internal val taskInformationMap: MutableMap<Any, Information> = ConcurrentHashMap<Any, Information>()

	internal val r: (Information) -> Unit = { information ->
		try {
			// DEBUG logger.info("Running task ${taskInformationMap.entries.first { it.value === information }.key}")
			val times = information.times
			val lastRun = if (times.isEmpty()) 0 else times.last()
			val curr = System.currentTimeMillis()
			if (curr - lastRun >= information.millis) {
				information.runnable.run()
				if (times.size >= 20) times.removeAt(0)
				times.add(curr)
			}
		} catch (e: Exception) {
			logger.log(Level.WARNING, "RepeatingTaskHandler", e)
		}
	}

	override fun getTask(): () -> Unit {
		return NullTaskHandler.nullTask
	}

	override fun hasTask(): Boolean {
		return false
	}

	/**
	 * Adds a task to be run periodically
	 *
	 * @param key the key of the task
	 * @param task the task to run
	 */
	@Deprecated("Use the function on Multicore", ReplaceWith("Multicore.scheduleTaskAtFixedRate(key, 0, task)"))
	@Suppress("NOTHING_TO_INLINE")
	inline fun add(key: Any, task: Runnable) {
		MultiCore.scheduleTaskAtFixedRate(key.toString(), millis = 0, task = task::run)
	}

	/**
	 * Adds a task to be run periodically every 'millis' milliseconds
	 *
	 * @param key the key of the task
	 * @param millis the frequency
	 * @param task the task to run
	 */
	@Deprecated("Use the function on Multicore", ReplaceWith("Multicore.scheduleTaskAtFixedRate(key, millis, task)"))
	@Suppress("NOTHING_TO_INLINE")
	inline fun add(key: Any, millis: Long, task: Runnable) {
		MultiCore.scheduleTaskAtFixedRate(key.toString(), millis = millis, task = task::run)
	}

	/**
	 * Removes the task corresponding to the given key
	 *
	 * @param key the key of the task to remove
	 * @return true, if successful
	 */
	@Deprecated("Use the function on Multicore", ReplaceWith("Multicore.removeTask(key)"))
	@Suppress("NOTHING_TO_INLINE")
	inline fun remove(key: Any): Boolean {
		return MultiCore.removeTask(key.toString())
	}

	fun printAll(logger: Logger) {
		this.taskInformationMap.forEach { (key, value) -> logger.fine("Print All: $key $value")}
	}

}
