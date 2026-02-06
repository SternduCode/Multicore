package com.sterndu.multicore

fun makeThread(name: String, state: CustomMultiCoreThreadState, task: (CustomMultiCoreThreadState) -> Unit): Thread {
	return Thread.ofVirtual().name(name).unstarted {
		task(state)
	}
}