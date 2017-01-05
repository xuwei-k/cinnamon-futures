package futures

import cinnamon.instrument.scala.future.{ ScalaFutureInstrumentation, ScalaFutureMetadata }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

/**
 * Support named futures by setting thread local label,
 * to be picked up by TimedFutureInstrumentation.
 */
object NamedFuture {
  private[this] val localLabel = new ThreadLocal[String]

  /**
   * Set the named Future label for a block of code.
   */
  def withLabel[T](name: String)(body: => T): T = {
    localLabel.set(name)
    val result = body
    localLabel.remove()
    result
  }

  /**
   * Get the current named Future label.
   */
  def label: String = localLabel.get

  /**
   * Schedule an explicitly named Future.
   */
  def apply[T](name: String)(body: => T)(implicit executor: ExecutionContext): Future[T] = {
    withLabel(name)(Future(body))
  }

  /**
   * Automatic name from function class name.
   */
  def from[A, B](f: A => Future[B]): A => Future[B] = {
    (a: A) => withLabel(f.getClass.getName) { f(a) }
  }

}

/**
 * Mark the creation of a named/timed Future.
 */
case class TimedFutureCreated(name: String, createdTime: Long = System.nanoTime)

/**
 * Mark the completion of a named/timed Future.
 */
case class TimedFuture(name: String, duration: Duration)

object TimedFuture {
  /**
   * Mark the completion of a Future, given the name and creation timestamp.
   */
  def apply(name: String, createdTime: Long): TimedFuture =
    TimedFuture(name, (System.nanoTime - createdTime).nanos)
}

/**
 * Accumulated timed Futures.
 */
case class TimedFutures(timed: Seq[TimedFuture]) {
  def +(future: TimedFuture): TimedFutures = TimedFutures(timed :+ future)
  def ++(futures: TimedFutures): TimedFutures = TimedFutures(timed ++ futures.timed)
}

/**
 * Propagation of timings via thread local context.
 */
object TimedFutures {
  private[this] val localTimedFutures = new ThreadLocal[TimedFutures]

  val empty = TimedFutures(Seq.empty)

  /**
   * Return all timed futures in the current context.
   */
  def current: TimedFutures = Option(localTimedFutures.get) getOrElse empty

  /**
   * Text summary of timed futures in the current context.
   */
  def summary: String = {
    current.timed map { future => s"${future.name}: ${future.duration.toMillis}ms" } mkString("\n")
  }

  /**
   * Add the timed completion of a named Future.
   * Appends a new timed Future to the existing timed Futures in the current context.
   * Returns the new TimedFutures to be used as a flow context.
   */
  def completed(name: String, createdTime: Long): TimedFutures = {
    current + TimedFuture(name, createdTime)
  }

  /**
   * Set the timed Futures in the current context.
   * Note: flow contexts are untyped and possibly null.
   */
  def set(context: AnyRef): Unit = context match {
    case timed: TimedFutures => localTimedFutures.set(timed)
    case _ => // ignore empty contexts
  }

  /**
   * Join multiple timed Future contexts and set as current context.
   * Note: flow contexts are untyped and possibly null.
   */
  def setAll(contexts: AnyRef*): Unit = {
    set((contexts collect { case timed: TimedFutures => timed }).foldLeft(empty)(_ ++ _))
  }

  /**
   * Clear timed futures from the current context.
   */
  def clear(): Unit = localTimedFutures.remove()
}

/**
 * Scala Future instrumentation for timing named futures.
 * ScalaFutureMetadata allows metadata to be attached to and extracted from Future objects.
 */
final class TimedFutureInstrumentation(metadata: ScalaFutureMetadata) extends ScalaFutureInstrumentation {

  /**
   * Attach the name and creation time to a Future.
   */
  override def futureCreated(future: Future[_]): Unit = {
    NamedFuture.label match {
      case name: String => metadata.attachTo(future, TimedFutureCreated(name))
      case _ => // ignore unnamed futures
    }
  }

  /**
   * If the Future has a name and creation time, append the created-to-completed duration to the flow context.
   */
  override def futureCompleting(future: Future[_]): AnyRef = {
    metadata.extractFrom(future) match {
      case TimedFutureCreated(name, createdTime) => TimedFutures.completed(name, createdTime)
      case _ => TimedFutures.current // pass on timed futures
    }
  }

  /**
   * Attach any accumulated timed Futures as the flow context.
   */
  override def futureCallbackAdded(future: Future[_], executor: ExecutionContext): AnyRef = {
    TimedFutures.current // pass on timed futures
  }

  /**
   * Empty method to show full SPI.
   */
  override def futureCallbackScheduled(future: Future[_], executor: ExecutionContext, callbackContext: AnyRef, completingContext: AnyRef): Unit = {
    // nothing to do on schedule
  }

  /**
   * Set the current context with any accumulated timed Futures from the callback or completing contexts.
   */
  override def futureCallbackStarted(future: Future[_], executor: ExecutionContext, callbackContext: AnyRef, completingContext: AnyRef): Unit = {
    TimedFutures.setAll(callbackContext, completingContext)
  }

  /**
   * Clear the current context of timed futures.
   */
  override def futureCallbackCompleted(future: Future[_], executor: ExecutionContext, callbackContext: AnyRef, completingContext: AnyRef): Unit = {
    TimedFutures.clear()
  }

  /**
   * Attach any accumulated timed Futures as the flow context.
   */
  override def futureRunnableScheduled(future: Future[_], executor: ExecutionContext): AnyRef = {
    TimedFutures.current // pass on timed futures
  }

  /**
   * Set the current context with any accumulated timed Futures.
   */
  override def futureRunnableStarted(future: Future[_], executor: ExecutionContext, context: AnyRef): Unit = {
    TimedFutures.set(context)
  }

  /**
   * Clear the current context of timed futures.
   */
  override def futureRunnableCompleted(future: Future[_], executor: ExecutionContext, context: AnyRef): Unit = {
    TimedFutures.clear()
  }
}

object TimedFutureInstrumentation {
  def create(metadata: ScalaFutureMetadata): ScalaFutureInstrumentation = new TimedFutureInstrumentation(metadata)
}
