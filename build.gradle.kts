plugins {
	alias(libs.plugins.kotlinJvm)
	`maven-publish`
}

group = "com.sterndu"
version = "1.0.0"

sourceSets.main {
	java.srcDirs("src")
}

java {
	modularity.inferModulePath.set(true)
}

kotlin {
	jvmToolchain(libs.versions.jvm.get().toInt())
	compilerOptions {
		freeCompilerArgs.add("-jvm-default=enable")
	}
	sourceSets.main {
		kotlin.srcDirs("src")
	}
}

tasks.withType<Jar> {
	duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.named("compileJava", JavaCompile::class.java) {
	options.compilerArgumentProviders.add(CommandLineArgumentProvider {
		// Provide compiled Kotlin classes to javac – needed for Java/Kotlin mixed sources to work
		listOf("--patch-module", "com.sterndu.MultiCore=${sourceSets["main"].output.asPath}") // , "--enable-preview"
	})
}

tasks.register<Jar>("sourcesJar") {
	group = "build"
	description = "Generates sources jar"
	archiveClassifier.set("sources")  // creates `-sources.jar`
	from(sourceSets["main"].allSource) // include all Kotlin/Java sources
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
			artifact(tasks["sourcesJar"])
		}
	}
}