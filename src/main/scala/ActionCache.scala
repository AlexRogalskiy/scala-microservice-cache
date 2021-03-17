import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, _}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Sink, Source}
import spray.json.{DefaultJsonProtocol, enrichAny}

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}


case class CalculateResult(actionId: Int, value: Int)

trait ActionJsonProtocol extends DefaultJsonProtocol {
  implicit val actionJson = jsonFormat2(CalculateResult)
}

object ActionCache extends ActionJsonProtocol {
  var results = List(
    CalculateResult(1, 5),
    CalculateResult(2, 3),
    CalculateResult(3, 4)
  )

  def main(args: Array[String]): Unit = {
    implicit val system           = ActorSystem(Behaviors.empty, "my-system")
    implicit val executionContext = system.executionContext

    val personServerRoute =
      pathPrefix("api" / "action") {
        get {
          (path(IntNumber) | parameter('id.as[Int])) { id =>
//            val connectionFlow = Http().outgoingConnection(s"http://localhost:8000/api/action/2")
            val connectionFlow = Http().outgoingConnection(s"localhost", 8000, "/api/action/2")

            def oneOffRequest(request: HttpRequest): Future[HttpResponse] =
              Source.single(request).via(connectionFlow).runWith(Sink.head)

            oneOffRequest(HttpRequest()).onComplete {
              case Success(response) => println(s"Got successful response: $response")
              case Failure(ex) => println(s"Sending the request failed: $ex")
            }

            complete(
              HttpEntity(
                ContentTypes.`application/json`,
                results.find(_.actionId == id).toJson.prettyPrint
              )
            )
          } ~
          pathEndOrSingleSlash {
            complete(
              HttpEntity(
                ContentTypes.`application/json`,
                results.toJson.prettyPrint
              )
            )
          }
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(personServerRoute)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind())                 // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
