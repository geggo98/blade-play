package models

import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import akka.actor.{Cancellable, ActorRef, Actor, Props}
import play.api.libs.iteratee._
import play.api.libs.json._
import concurrent.Future
import akka.pattern.ask
import play.api.Play.current
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import org.joda.time.{DateTime, Period}
import java.math.MathContext
import annotation.tailrec
import scala.collection.mutable
import play.api.libs.json.JsString
import scala.Some
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject


/**
 * Created with IntelliJ IDEA.
 * User: sschwets
 * Date: 31.01.13
 * Time: 17:43
 * To change this template use File | Settings | File Templates.
 */
object Tracker {
  implicit val timeout : Timeout = 1.seconds; // Timeout for "?" operator, when waiting for an answer

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

case class SpaceCoordinate(lat : BigDecimal, long: BigDecimal, accuracy: Option[BigDecimal]) {
  def toJson = JsObject(
    Seq( ("latitude", JsNumber(lat)),("longitude", JsNumber(long)) ) ++
      accuracy.map(a => Seq( ("accuracy", JsNumber(a)) )).getOrElse(Nil)
  )
}
case class SpaceTimeCoordinate(who : String, where : SpaceCoordinate, when : DateTime)

trait JsonMessagesFromTracker {
  def toJson : JsObject
}

case class MapView(leftTop: SpaceCoordinate, rightBottom : SpaceCoordinate, name : String,
                   course : List[SpaceCoordinate]) {
  def toJson = JsObject(Seq(
    ("leftTop", leftTop.toJson), ("rightBottom", rightBottom.toJson), ("name", JsString(name)),
    ("course", JsArray(course.map(_ toJson)))
  ))
  def dimension = SpaceDimension(rightBottom.lat-leftTop.lat, rightBottom.long-leftTop.long)
}

case class ChangeMapView(newMap : MapView) extends JsonMessagesFromTracker with MessageToTracker {
  override def toJson: JsObject = JsObject(Seq( ("map", newMap.toJson) ))
}

class Tracker extends Actor {
  case object Tick extends MessageToTracker
  case class HeatMapCalculated(heatMap : HeatMap) extends MessageToTracker

  val coordinates : mutable.HashMap[String, SpaceTimeCoordinate] = mutable.HashMap.empty

  val coordinatesExpireAfter = Period.seconds(90)

  var myTracker : Option[ActorRef] = None
  var ticker : Option[Cancellable] = None

  var mapView : Option[MapView] = None

  var heatMapBuilder=new HeatMapBuilder(SpaceCoordinate(1,2,None),SpaceDimension(10,20),100)
  var maxCellsInHeatMap=50

  var lastHeatMap : Option[HeatMap] = None

  val (broadcastEnumerator, broadcastChannel) = Concurrent.broadcast[JsValue]

  private def setTicker(interval : FiniteDuration) {
    ticker.map( _.cancel())
    myTracker.map{ tracker =>
      ticker = Some(Akka.system.scheduler.schedule(1 seconds, interval, tracker, Tick))
    }
  }

  private def currentMapConfiguration() : JsObject = {
    val changeMapView : Option[JsonMessagesFromTracker]=mapView map ChangeMapView
    val changeHeatMap : Option[JsonMessagesFromTracker]=lastHeatMap map ChangeHeatMap
    val emptyObject=JsObject(Seq())
    (List(changeMapView, changeHeatMap) flatMap (_ map {o => List(o.toJson)} getOrElse Nil )).
      foldLeft(emptyObject)(_ ++ _)
  }

  override  def receive = {
    case Join(clientId) => {
      sender ! ConnectedToTracker(Enumerator[JsValue](currentMapConfiguration()) andThen broadcastEnumerator)
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
      val expiredCoordinates = coordinates filter {
        case (_,SpaceTimeCoordinate(_,_,when)) => when.isBefore(expireTime)
      } map {case (clientId, _) => clientId}
      coordinates --= expiredCoordinates
      scala.concurrent.future{
        heatMapBuilder.calculateHeatMap(coordinates.values,maxCellsInHeatMap)
      } onSuccess{
        case heatMap => myTracker map (_ ! HeatMapCalculated(heatMap))
      }

    case HeatMapCalculated(newHeatMap) => {
      lastHeatMap=Some(newHeatMap)
      val changeHeatMap=ChangeHeatMap(newHeatMap).toJson
      broadcastChannel.push(changeHeatMap)
    }

    case ChangeMapView(newMapView) => {
      mapView=Some(newMapView)
      heatMapBuilder=new HeatMapBuilder(newMapView.leftTop,newMapView.dimension, 100)
      broadcastChannel.push(newMapView.toJson)
    }
  }
}

case class SpaceDimension(latLength : BigDecimal, longLength : BigDecimal) {
  def toJson = JsObject(Seq( ("latLength", JsNumber(latLength)), ("longLength", JsNumber(longLength)) ))
}

case class MapCell(latIdx : Int, longIdx : Int)

case class HeatMapCell(cell : MapCell, count : Int){
  def toJson = JsObject(Seq(
    ("latIdx",JsNumber(cell.latIdx)), ("longIdx", JsNumber(cell.longIdx)), ("count", JsNumber(count))
  ))
}

case class ChangeHeat(heat : HeatMap) extends JsonMessagesFromTracker {
  def toJson: JsObject = JsObject(Seq( ("heat", heat.toJson) ))
}

case class HeatMap(origin : SpaceCoordinate, cellDimension : SpaceDimension, cells : List[HeatMapCell])
{
  def toJson = JsObject(Seq( ("origin", origin.toJson), ("dimensions", origin.toJson),
    ("cells", JsArray(cells.map(_ toJson)))
  ))
}

case class ChangeHeatMap(newHeatMap : HeatMap) extends JsonMessagesFromTracker {
  override def toJson : JsObject = JsObject(Seq( ("heat", newHeatMap.toJson) ))
}

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