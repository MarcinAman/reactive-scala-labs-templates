package EShop.lab5

import EShop.lab5.ProductCatalog.GetItems
import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.{ask, PipeToSupport}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class ProductCatalogHttp(ac: ActorSystem) extends HttpApp {
  private implicit val timeout: Timeout     = Timeout(10 seconds)
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private val slave                         = ac.actorOf(Props(new RequestsHandler()), "XD")

  import EShop.lab5.ProductCatalog._
  override protected def routes: Route = {
    path("find-item") {
      post {
        entity(as[GetItems]) { query =>
          complete {
            (slave ? query).mapTo[Items]
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
        actor    <- context.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2554/user/productcatalog").resolveOne()
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

  productCatalogSystem.actorOf(
    ProductCatalog.props(new SearchService()),
    "productcatalog"
  )

  val server = new ProductCatalogHttp(httpActorSystem)
  server.startServer("localhost", 9001, httpActorSystem)
}
