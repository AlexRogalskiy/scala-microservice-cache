import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Source
import spray.json.DefaultJsonProtocol

import scala.io.StdIn


case class CalculatedResult(actionId: Int, value: Int)

trait ActionJsonProtocol extends DefaultJsonProtocol {
  implicit val actionJson = jsonFormat2(CalculatedResult)
}

object ActionCache extends ActionJsonProtocol{
  val resultsDbMock = List(CalculatedResult(1, 5))

  implicit val system           = ActorSystem(Behaviors.empty, "my-system")
  implicit val executionContext = system.executionContext

  val route = pathPrefix("api" / "result") {
    (path(IntNumber) | parameter("id".as[Int])) { id =>
      val eventualString = Source
        .future(Http().singleRequest(HttpRequest(uri = s"http://localhost:8000/api/action/$id")))
        .flatMapConcat(_.entity.dataBytes)
        .runFold("") { case (acc, body) => acc + body.utf8String}
      get {
        complete(eventualString)
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind())                 // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
