@file:JvmName("LoggingUtil")
package com.sterndu.multicore

import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.logging.*

object LoggingUtil {

	private const val secondsOfADay = 86400000L

	private lateinit var consoleHandler: ConsoleHandler
	private lateinit var fileHandler: FileHandler

	private var initialized = false

	private fun init() {
		initialized = true

		consoleHandler = ConsoleHandler()
		consoleHandler.formatter = CustomFormatterConsole()
		fileHandler = FileHandler(String.format("logs/log-%td.%1\$tm.%1\$tY-%%u.log", ZonedDateTime.now()), true)
		fileHandler.level = Level.ALL
		fileHandler.formatter = CustomFormatterFile()

		var day = System.currentTimeMillis() - System.currentTimeMillis() % secondsOfADay

		Updater.add(Runnable {
			if ((System.currentTimeMillis() - System.currentTimeMillis() % secondsOfADay) > day) {
				synchronized(consoleHandler) {
					day = System.currentTimeMillis() - System.currentTimeMillis() % secondsOfADay
					val newFileHandler = FileHandler(String.format("logs/log-%td.%tm.%tY.log", ZonedDateTime.now()), true)
					newFileHandler.level = Level.ALL
					newFileHandler.formatter = CustomFormatterFile()
					val logManager = LogManager.getLogManager()
					for (name in logManager.loggerNames) {
						val logger = logManager.getLogger(name)
						logger.addHandler(newFileHandler)
						logger.removeHandler(fileHandler)
					}
					fileHandler = newFileHandler
				}
			}
		}, "LoggerFileHandlerUpdater", 1000)
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
		if (!File("./logs").exists() && !File("./logs").mkdir()) throw IOException("Unable to create directory logs")
		if (!initialized) {
			init()
		}

		synchronized(consoleHandler) {
			val logger = Logger.getLogger(name)
			logger.level = Level.ALL
			logger.useParentHandlers = false
			logger.addHandler(consoleHandler)
			logger.addHandler(fileHandler)

			return logger
		}
	}

	class CustomFormatterFile: Formatter() {

		override fun format(record: LogRecord): String {
			return String.format("[%1\$td.%1\$tm.%1\$tY %1\$tH:%1\$tM:%1\$tS.%1\$tL%1\$tz][%1\$tQ][%3\$s]: %4\$s: %5\$s %6\$s%n",
				Instant.ofEpochMilli(record.millis).atZone(ZoneId.systemDefault()), record.sourceClassName + "." + record.sourceMethodName, record.loggerName,
				record.level.name, record.message, record.thrown?.toString() ?: "")
		}

	}

	class CustomFormatterConsole: Formatter() {

		override fun format(record: LogRecord): String {
			return String.format("[%1\$td.%1\$tm.%1\$tY %1\$tH:%1\$tM:%1\$tS.%1\$tL%1\$tz][%3\$s]: %4\$s: %5\$s %6\$s%n",
				Instant.ofEpochMilli(record.millis).atZone(ZoneId.systemDefault()), record.sourceClassName + "." + record.sourceMethodName, record.loggerName,
				record.level.name, record.message, record.thrown?.toString() ?: "")
		}

	}

}