package models

import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import akka.actor.{Cancellable, ActorRef, Actor, Props}
import play.api.libs.iteratee._
import play.api.libs.json.JsValue
import concurrent.Future
import akka.pattern.ask
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.Play.current
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global



/**
 * Created with IntelliJ IDEA.
 * User: sschwets
 * Date: 31.01.13
 * Time: 17:43
 * To change this template use File | Settings | File Templates.
 */
object Tracker {
  implicit val timeout : Timeout = 1 seconds; // Timeout for "?" operator, when waiting for an answer

  lazy val defaultTracker : ActorRef = {
    val tracker=Akka.system.actorOf(Props[Tracker])
    tracker ! CreateTicker(tracker)
    tracker
  }

  private def jsonInputParser(clientId : String) : Iteratee[JsValue,_] =
    Iteratee.foreach[JsValue] { eventFromWebClient =>
  }

  private def emptyInputParser = Done[JsValue,Unit]((),Input.EOF)

  private def errorOutput(msg : String) =
    Enumerator[JsValue](JsObject(Seq("error" -> JsString(msg)))).andThen(Enumerator.enumInput(Input.EOF))

  def join(clientId : String) : Future[(Iteratee[JsValue,_], Enumerator[JsValue])] = {
    (defaultTracker ? Join(clientId)) map {
      case ConnectedToTracker(enumerator) => (jsonInputParser(clientId),enumerator)
      case ConnectionError(msg) => (emptyInputParser, errorOutput(msg))
    }
  }
}


case class Join(clientId : String)
case class ConnectedToTracker(trackerMessages : Enumerator[JsValue])
case class ConnectionError(msg : String)

case class CreateTicker(tracker : ActorRef)

case class UpdatePosition(pos : Position, clientId : String)

case class Position(lat : BigInt, long: BigInt, accuracy: Option[BigInt])


class Tracker extends Actor {
  case object Tick

  var myTracker : Option[ActorRef] = None
  var ticker : Option[Cancellable] = None

  val (enumerator, channel) = Concurrent.broadcast[JsValue]

  private def setTicker(interval : FiniteDuration) {
    ticker.map( _.cancel())
    myTracker.map{ tracker =>
      ticker = Some(Akka.system.scheduler.schedule(1 seconds, interval, tracker, Tick))
    }
  }

  override  def receive = {
    case Join(clientId) => {
      sender ! ConnectedToTracker(enumerator)
    }
    case CreateTicker(tracker) => {
      myTracker=Some(tracker)
      setTicker(1 seconds)
    }
  }
}

