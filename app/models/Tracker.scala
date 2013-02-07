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
import org.joda.time.{DateTime, Period, Seconds, Instant}
import scala.collection.mutable.{HashMap => MutableHashMap}
import java.math.MathContext
import collection.parallel.immutable.ParSeq
import collection.parallel.mutable.ParArray
import collection.{mutable, parallel}
import collection.immutable.HashMap
import util.control.TailCalls.TailRec
import annotation.tailrec


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

trait MessageToTracker
case class Join(clientId : String) extends MessageToTracker

trait ResponseFromTracker
case class ConnectedToTracker(trackerMessages : Enumerator[JsValue]) extends ResponseFromTracker
case class ConnectionError(msg : String) extends ResponseFromTracker

case class CreateTicker(tracker : ActorRef)  extends MessageToTracker

case class UpdatePosition(where : SpaceCoordinate, clientId : String) extends MessageToTracker

case class SpaceCoordinate(lat : BigDecimal, long: BigDecimal, accuracy: Option[BigDecimal])
case class SpaceTimeCoordinate(who : String, where : SpaceCoordinate, when : DateTime)


class Tracker extends Actor {
  case object Tick extends MessageToTracker

  val coordinates = new MutableHashMap[String, SpaceTimeCoordinate]

  val coordinatesExpireAfter = Period.seconds(90)

  var myTracker : Option[ActorRef] = None
  var ticker : Option[Cancellable] = None

  var heatMapBuilder=new HeatMapBuilder(SpaceCoordinate(1,2,None),SpaceDimension(10,20),100)
  var maxCellsInHeatMap=50

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
    case UpdatePosition(where, who) => {
      coordinates.put(who,SpaceTimeCoordinate(who, where, DateTime.now()))
    }

    case Tick =>
      val expireTime = DateTime.now().minus(coordinatesExpireAfter)
      val expiredCoordinates = (coordinates filter (_._2.when.isBefore(expireTime)) map (_._1) toList)
      coordinates --= expiredCoordinates
      scala.concurrent.future{
        heatMapBuilder.calculateHeatMap(coordinates.values,maxCellsInHeatMap)

      }
  }

}

case class SpaceDimension(latLength : BigDecimal, longLength : BigDecimal)
case class MapCell(latIdx : Int, longIdx : Int)
case class HeatMapCell(cell : MapCell, count : Int)

case class HeatMap(origin : SpaceCoordinate, cellDimension : SpaceDimension, cells : List[HeatMapCell])

class HeatMapBuilder(val origin : SpaceCoordinate, val cellDimension : SpaceDimension, val cellsPerDimension : Int) {
  def calculateHeatMap(coordinates : Iterable[SpaceTimeCoordinate], maxNumberOfCells : Int = 50) : HeatMap = {
    val mc=MathContext.DECIMAL32

    @tailrec
    def sumCounts[T](x : Map[T,Int], y: Map[T,Int]) : Map[T,Int] = {
      if (x.size <= y.size)
        y ++ ( x map {case (idx, count) => (idx, x.getOrElse(idx,0) + count)} )
      else
        sumCounts(y,x)
    }
    def countDesc(x : (_,Int)) : Int = - x._2

    val heatMapCells=((coordinates par) map { case SpaceTimeCoordinate(_,SpaceCoordinate(lat,long,_),_) =>
      MapCell((lat / cellDimension.latLength).round(mc).toInt, (long / cellDimension.longLength).round(mc).toInt)
    } map { c : MapCell =>
      Map( (c, 1) )
    } reduce (sumCounts[MapCell] _) toSeq) sortBy (countDesc _) take maxNumberOfCells map {
      case (cell, count) => HeatMapCell(cell, count)
    }

    HeatMap(origin, cellDimension, heatMapCells.toList)
  }
}