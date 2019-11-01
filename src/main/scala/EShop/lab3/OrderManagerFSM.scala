package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager.{AddItem, _}
import akka.actor.FSM
import EShop.lab3.OrderManager._
import EShop.lab3.Payment.DoPayment

class OrderManagerFSM extends FSM[State, Data] {
  import context._

  startWith(Uninitialized, Empty)

  when(Uninitialized) {
    case Event(AddItem(item), _) =>
      val actor = system.actorOf(CartActor.props(), "XDD")
      actor ! CartActor.AddItem(item)
      sender ! Done
      goto(Open) using CartData(actor)
  }

  when(Open) {
    case Event(AddItem(item), CartData(actor)) =>
      actor ! CartActor.AddItem(item)
      sender ! Done
      stay
    case Event(RemoveItem(item), CartData(actor)) =>
      actor ! CartActor.RemoveItem(item)
      sender ! Done
      stay
    case Event(Buy, CartData(actor)) =>
      actor ! CartActor.StartCheckout
      goto(InCheckout) using CartDataWithSender(actor, sender)
  }

  when(InCheckout) {
    case Event(CartActor.CheckoutStarted(checkoutRef, _), CartDataWithSender(_, senderRef)) =>
      senderRef ! Done
      stay() using InCheckoutData(checkoutRef)

    case Event(SelectDeliveryAndPaymentMethod(delivery, payment), InCheckoutData(checkoutRef)) =>
      checkoutRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutRef ! Checkout.SelectPayment(payment)
      goto(InPayment) using InCheckoutDataWithSender(checkoutRef, sender)
  }

  when(InPayment) {
    case Event(Checkout.PaymentStarted(paymentRef), InCheckoutDataWithSender(_, senderRef)) =>
      senderRef ! Done
      stay() using InPaymentData(paymentRef)
    case Event(Pay, InPaymentData(paymentRef)) =>
      paymentRef ! DoPayment
      stay using InPaymentDataWithSender(paymentRef, sender)
    case Event(Payment.PaymentConfirmed, InPaymentDataWithSender(_, senderRef)) =>
      senderRef ! Done
      goto(Finished)
  }

  when(Finished) {
    case _ =>
      sender ! "order manager finished job"
      stay()
  }

}
