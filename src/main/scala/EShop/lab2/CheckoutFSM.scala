package EShop.lab2

import EShop.lab2.Checkout.Data
import EShop.lab2.CheckoutFSM.Status
import akka.actor.{ActorRef, Cancellable, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM)
}

class CheckoutFSM extends LoggingFSM[Status.Value, Data] {
  import EShop.lab2.Checkout._
  import EShop.lab2.CheckoutFSM.Status._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private val scheduler = context.system.scheduler

  startWith(NotStarted, Uninitialized)

  when(NotStarted) {
    case Event(StartCheckout, _) =>
      val xd = schedule(checkoutTimerDuration, ExpireCheckout)
      goto(SelectingDelivery) using SelectingDeliveryStarted(xd)
  }

  when(SelectingDelivery) {
    case Event(SelectDeliveryMethod(_), _)         => goto(SelectingPaymentMethod)
    case Event(CancelCheckout | ExpireCheckout, _) => goto(Cancelled)
  }

  when(SelectingPaymentMethod) {
    case Event(SelectPayment(_), SelectingDeliveryStarted(timer)) =>
      timer.cancel()
      val xd = schedule(paymentTimerDuration, ExpirePayment)
      goto(ProcessingPayment) using ProcessingPaymentStarted(xd)
    case Event(CancelCheckout | ExpireCheckout, _) => goto(Cancelled)
  }

  when(ProcessingPayment) {
    case Event(ReceivePayment, _) => goto(Closed)
    //ExpireCheckout wg diagramu nie powinien tutaj byc handlowany?
    case Event(CancelCheckout | ExpirePayment | ExpireCheckout, _) => goto(Cancelled)
  }

  when(Cancelled) {
    case _ => stay
  }

  when(Closed) {
    case _ => stay
  }

  private def schedule(time: FiniteDuration, message: Command): Cancellable =
    scheduler.scheduleOnce(delay = time, receiver = self, message = ExpireCheckout)(
      context.system.dispatcher
    )

}
