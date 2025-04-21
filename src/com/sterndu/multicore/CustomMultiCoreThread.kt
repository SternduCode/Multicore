package com.sterndu.multicore

fun makeThread(name: String, state: CustomMultiCoreThreadState, task: (CustomMultiCoreThreadState) -> Unit): Thread {
	return Thread.ofPlatform().name(name).unstarted {
		task(state)
	}
}