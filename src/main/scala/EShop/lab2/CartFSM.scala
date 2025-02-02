package EShop.lab2

import EShop.lab2.CartFSM.Status
import akka.actor.{LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartFSM {

  object Status extends Enumeration {
    type Status = Value
    val Empty, NonEmpty, InCheckout = Value
  }

  def props() = Props(new CartFSM())
}

class CartFSM extends LoggingFSM[Status.Value, Cart] {
  import EShop.lab2.CartFSM.Status._
  import EShop.lab2.CartActor._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val cartTimerDuration: FiniteDuration = 1 seconds

  startWith(Empty, Cart.empty)

  when(Empty) {
    case Event(AddItem(item), _) =>
      goto(NonEmpty) using Cart(item :: Nil)
    case Event(GetItems, _) =>
      sender ! Cart.empty
      stay
  }

  when(NonEmpty, stateTimeout = cartTimerDuration) {
    case Event(RemoveItem(item), cart: Cart) if cart.hasOnyThisItem(item) =>
      goto(Empty) using Cart.empty
    case Event(RemoveItem(item), cart: Cart) if cart.contains(item) =>
      stay using cart.removeItem(item)
    case Event(AddItem(item), cart: Cart) =>
      stay using cart.addItem(item)
    case Event(StartCheckout, cart: Cart) =>
      val checkoutRef = context.actorOf(CheckoutFSM.props(self), "checkout")
      checkoutRef ! Checkout.StartCheckout
      sender() ! CheckoutStarted(checkoutRef, cart)
      goto(InCheckout) using cart
    case Event(ExpireCart | StateTimeout, _) =>
      goto(Empty) using Cart.empty
    case Event(GetItems, cart: Cart) =>
      sender ! cart
      stay
  }

  when(InCheckout) {
    case Event(CancelCheckout, cart: Cart) => goto(NonEmpty) using cart
    case Event(CloseCheckout, _)           => goto(Empty) using Cart.empty
  }

}
