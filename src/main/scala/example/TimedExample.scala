package example

import futures.{ NamedFuture, TimedFutures }
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Example of recording created-to-completed times for named Futures.
 */
object TimedExample extends App {

  // explicitly named Future
  def one = NamedFuture("one") {
    delay(min = 2.seconds, max = 3.seconds)
    "a"
  }

  // function for flatMap, will be named using anonymous function class
  def two = NamedFuture.from((value: String) => Future {
    delay(min = 4.seconds, max = 5.seconds)
    value + "b"
  })

  val result = one flatMap two flatMap { value =>
    // not named, no time recorded, other times propagated
    Future {
      delay(min = 1.second, max = 2.seconds)
      value + "c"
    }
  } map { value =>
    // mapped so we can await all callbacks, could be onComplete
    println(s"result: ${value}")
    // print summary of timed futures at the end
    println(TimedFutures.summary)
  }

  Await.ready(result, 20.seconds)

  def delay(min: Duration, max: Duration): Unit = {
    import java.util.concurrent.ThreadLocalRandom.{ current => random }
    Thread.sleep(random.nextLong(min.toMillis, max.toMillis))
  }

}
