package EShop.lab5

import EShop.lab5.ProductCatalog.GetItems
import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.{ask, PipeToSupport}
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class ProductCatalogHttp(ac: ActorSystem) extends HttpApp {
  private implicit val timeout: Timeout     = Timeout(10 seconds)
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private val slaves                        = ac.actorOf(RoundRobinPool(3).props(Props[RequestsHandler]))

  import EShop.lab5.ProductCatalog._
  override protected def routes: Route = {
    path("find-item") {
      post {
        entity(as[GetItems]) { query =>
          complete {
            (slaves ? query).mapTo[Items]
          }
        }
      }
    }
  }
}

class RequestsHandler() extends Actor with PipeToSupport {
  private implicit val timeout: Timeout     = Timeout(10 seconds)
  private implicit val ec: ExecutionContext = ExecutionContext.global

  override def receive: Receive = {
    case get: GetItems =>
      (for {
        actor    <- context.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2554/user/productcatalog-*").resolveOne()
        response <- actor ? get
      } yield response).pipeTo(sender())
  }
}

object ProductCatalogHttp extends App {
  private val config  = ConfigFactory.load()
  val httpActorSystem = ActorSystem("api", config.getConfig("api").withFallback(config))

  private val productCatalogSystem = ActorSystem(
    "ProductCatalog",
    config.getConfig("productcatalog").withFallback(config)
  )

  val productCatalogWorkers = (0 until 6).map { i =>
    productCatalogSystem.actorOf(
      ProductCatalog.props(new SearchService()),
      "productcatalog-" + i
    )
  }

  val server = new ProductCatalogHttp(httpActorSystem)
  server.startServer("localhost", 9001, httpActorSystem)
}
