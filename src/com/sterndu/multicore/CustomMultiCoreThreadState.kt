package com.sterndu.multicore

data class CustomMultiCoreThreadState(var hasTask: Boolean = false, var shouldShutdown: Boolean = false)
