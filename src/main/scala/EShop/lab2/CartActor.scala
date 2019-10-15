package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {
  import CartActor._
  import context._

  val cartTimerDuration = 5 seconds

  private def scheduleTimer =
    system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)(context.system.dispatcher)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      become(nonEmpty(Cart.empty.addItem(item), scheduleTimer))
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case RemoveItem(item) if cart.hasOnyThisItem(item) =>
      timer.cancel()
      become(empty)
    case RemoveItem(item) if cart.contains(item) =>
      timer.cancel()
      become(nonEmpty(cart.removeItem(item), scheduleTimer))
    case AddItem(item) =>
      timer.cancel()
      become(nonEmpty(cart.addItem(item), scheduleTimer))
    case StartCheckout =>
      timer.cancel()
      become(inCheckout(cart))
    case ExpireCart => become(empty)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CloseCheckout  => become(empty)
    case CancelCheckout => become(nonEmpty(cart, scheduleTimer))
  }

}
