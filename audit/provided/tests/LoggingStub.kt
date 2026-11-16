package io.github.oshai.kotlinlogging
class DummyLogger {
  fun trace(msg: () -> Any?) {}
  fun info(msg: () -> Any?) {}
  fun warn(msg: () -> Any?) {}
  fun error(msg: () -> Any?) {}
  fun debug(msg: () -> Any?) {}
}
object KotlinLogging { fun logger(fn: () -> Unit): DummyLogger = DummyLogger() }
