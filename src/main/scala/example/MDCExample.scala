package example

import org.slf4j.{ LoggerFactory, MDC }
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Example of MDC propagation for Futures.
 */
object MDCExample extends App {

  val log = LoggerFactory.getLogger(MDCExample.getClass)

  // transaction/request identifier
  MDC.put("id", "abc")

  // MDC value that demonstrates dataflow context propagation
  MDC.put("trace", "0")

  // append to the MDC "trace" value, to create a dataflow trace
  def trace(append: String) = MDC.put("trace", s"${MDC.get("trace")}.${append}")

  val result = Future {
    log.info("one")
    trace("1")
    "a"
  } map { value =>
    log.info("two")
    trace("2")
    value + "b"
  } flatMap { value =>
    log.info("three")
    trace("3")
    Future {
      log.info("four")
      trace("4")
      value + "c"
    }
  } map { value =>
    // mapped so we can await all callbacks, could be onComplete
    log.info(s"result: ${value}")
  }

  Await.ready(result, 5.seconds)

}
