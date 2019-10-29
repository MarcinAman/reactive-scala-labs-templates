package EShop.lab3

import EShop.lab2.CartActor.{AddItem, CheckoutStarted, GetItems, RemoveItem, StartCheckout}
import EShop.lab2.{Cart, CartActor, CartFSM}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CartTest
  extends TestKit(ActorSystem("CartTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  val content = "XDDDD"
  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val actorRef = TestActorRef(CartActor.props())

    actorRef ! AddItem(content)
    actorRef.receive(GetItems, self)

    expectMsg(Cart(Seq(content)))
  }

  it should "be empty after adding and removing the same item" in {
    val actorRef = TestActorRef(CartActor.props())

    actorRef ! AddItem(content)
    actorRef ! RemoveItem(content)

    actorRef.receive(GetItems, self)

    expectMsg(Cart.empty)
  }

  it should "start checkout" in {
    val actorRef = TestActorRef(CartActor.props())

    actorRef ! AddItem(content)
    actorRef ! StartCheckout

    expectMsgPF() {
      case _: CheckoutStarted => println("works")
    }
  }
}
