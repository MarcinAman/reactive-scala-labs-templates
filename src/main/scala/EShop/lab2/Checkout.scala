package EShop.lab2

import EShop.lab2.CartActor.CloseCheckout
import EShop.lab2.Checkout._
import EShop.lab3.Payment
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout(cart))
}

class Checkout(
  cartActor: ActorRef
) extends Actor {
  import context._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  def receive: Receive = LoggingReceive.withLabel("receive") {
    case StartCheckout =>
      val timer = schedule(checkoutTimerDuration, ExpireCheckout)
      become(selectingDelivery(timer))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive.withLabel("selectingDelivery") {
    case SelectDeliveryMethod(_)         => become(selectingPaymentMethod(timer))
    case CancelCheckout | ExpireCheckout => become(cancelled)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive.withLabel("selectingPaymentMethod") {
    case SelectPayment(method) =>
      timer.cancel()
      val paymentTimer   = schedule(paymentTimerDuration, ExpirePayment)
      val paymentService = context.actorOf(Payment.props(method, sender, self), "XDDDDDD")
      sender ! PaymentStarted(paymentService)
      context become processingPayment(paymentTimer)
    case CancelCheckout | ExpireCheckout => become(cancelled)
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive.withLabel("processingPayment") {
    case ReceivePayment =>
      timer.cancel()
      cartActor ! CloseCheckout
      context become closed
    case CancelCheckout | ExpirePayment | ExpireCheckout => become(cancelled)
  }

  def cancelled: Receive = LoggingReceive.withLabel("cancelled") {
    case e => log.error("Payment cancelled", e.toString)
  }

  def closed: Receive = LoggingReceive.withLabel("closed") {
    case e => log.error("Payment closed", e.toString)
  }

  private def schedule(time: FiniteDuration, message: Command): Cancellable =
    scheduler.scheduleOnce(delay = time, receiver = self, message = ExpireCheckout)(
      context.system.dispatcher
    )
}
