package BlnService

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{HttpApp, Route}

class Service(implicit s: ActorSystem) extends HttpApp {
  import ApiJsonProtocol._

  val dataAccess = new DataAccess

  override protected def routes: Route =
    pathPrefix("api") {
      path("linkUserToCell") {
        post {
          formFields("ctn".as[Ctn], "cellId".as[CellId]) { (ctn, cellId) =>
            onSuccess(dataAccess.linkWithCell(cellId, ctn)) {
              complete("OK")
            }
          }
        }
      } ~
      path("connectedUsers") {
        parameters("cellId".as[CellId]) { cellId =>
          rejectEmptyResponse {
            onSuccess(dataAccess.getCtns(cellId)) { r =>
              complete(r)
            }
          }
        }
      }
    }
}