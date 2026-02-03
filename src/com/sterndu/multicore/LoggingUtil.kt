@file:JvmName("LoggingUtil")
package com.sterndu.multicore

import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.locks.StampedLock
import java.util.logging.*

object LoggingUtil {

	private const val LOGFILE_PATTERN = $$"logs/log-%tY.%1$tm.%1$td-%%u.log"

	private const val SECONDS_OF_A_DAY = 86400000L

	private lateinit var consoleHandler: ConsoleHandler
	private var fileHandler: FileHandler? = null

	private val lock = StampedLock()

	private var logToFile: Boolean = false

	fun setLogToFile(): Boolean {
		return if (!initialized) {
			logToFile = true
			true
		} else {
			false
		}
	}

	private var initialized = false

	private fun init() {
		consoleHandler = ConsoleHandler()
		consoleHandler.formatter = CustomFormatterConsole()
		if (logToFile) {
			fileHandler = FileHandler(String.format(LOGFILE_PATTERN, ZonedDateTime.now()), true)
				.apply {
					level = Level.FINER
					formatter = CustomFormatterFile()
				}
		}

		var day = System.currentTimeMillis() - System.currentTimeMillis() % SECONDS_OF_A_DAY

		if (logToFile) {
			Updater.add("LoggerFileHandlerUpdater", 1000) {
                if ((System.currentTimeMillis() - System.currentTimeMillis() % SECONDS_OF_A_DAY) > day) {
                    var stamp = 0L
                    try {
                        stamp = lock.writeLock()

                        day = System.currentTimeMillis() - System.currentTimeMillis() % SECONDS_OF_A_DAY
                        val newFileHandler = FileHandler(String.format(LOGFILE_PATTERN, ZonedDateTime.now()), true)
                        newFileHandler.level = fileHandler!!.level
                        newFileHandler.formatter = CustomFormatterFile()
                        val logManager = LogManager.getLogManager()
                        for (name in logManager.loggerNames) {
                            val logger = logManager.getLogger(name)
                            logger.addHandler(newFileHandler)
                            logger.removeHandler(fileHandler!!)
                        }
                        fileHandler!!.flush()
                        fileHandler!!.close()
                        fileHandler = newFileHandler
                    } finally {
                        lock.unlock(stamp)
                    }
                }
            }
        }

		initialized = true
	}

	@JvmStatic
	fun main(args: Array<String>) {
		val logger = getLogger("Hi")
		logger.log(Level.INFO, "Logging")
		logger.log(Level.WARNING, "Warning")
		logger.fine("Uff this is fine")

		val logger2 = getLogger("Ho")

		logger2.info("Miau")
		logger.warning("FFs")
		logger2.severe("Severe")

		println(LogManager.getLogManager().loggerNames.toList())
	}

	@Throws(IOException::class)
	fun getLogger(name: String): Logger {
		if (logToFile && (!File("./logs").exists() && !File("./logs").mkdir())) throw IOException("Unable to create directory logs")

		if (!initialized) {
			synchronized(this) {
				if (!initialized) {
					init()
				}
			}
		}

		var stamp = 0L
		try {
			stamp = lock.writeLock()

			val logger = Logger.getLogger(name)
			logger.level = Level.ALL
			logger.useParentHandlers = false
			if (!logger.handlers.contains(consoleHandler)) {
				logger.addHandler(consoleHandler)
			}
			if (logToFile && !logger.handlers.contains(fileHandler)) logger.addHandler(fileHandler)

			return logger
		} finally {
			lock.unlock(stamp)
		}
	}

	class CustomFormatterFile: Formatter() {

		override fun format(record: LogRecord): String {
			return String.format(
                $$"[%1$td.%1$tm.%1$tY %1$tH:%1$tM:%1$tS.%1$tL%1$tz][%1$tQ][%3$s]: %4$s: %5$s %6$s%n",
				Instant.ofEpochMilli(record.millis).atZone(ZoneId.systemDefault()),
				record.sourceClassName + "." + record.sourceMethodName,
				record.loggerName,
				record.level.name,
				record.message,
				if (record.thrown != null)
					record.thrown.message.plus("\n")
						.plus(record.thrown.stackTrace.joinToString("\n"))
				else ""
			)
		}

	}

	class CustomFormatterConsole: Formatter() {

		override fun format(record: LogRecord): String {
			return String.format(
                $$"[%1$td.%1$tm.%1$tY %1$tH:%1$tM:%1$tS.%1$tL%1$tz][%3$s]: %4$s: %5$s %6$s%n",
				Instant.ofEpochMilli(record.millis).atZone(ZoneId.systemDefault()),
				record.sourceClassName + "." + record.sourceMethodName,
				record.loggerName,
				record.level.name,
				record.message,
				if (record.thrown != null)
					record.thrown.message.plus("\n")
						.plus(record.thrown.stackTrace.joinToString("\n"))
				else ""
			)
		}

	}

}