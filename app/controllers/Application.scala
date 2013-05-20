package controllers

import play.api._
import libs.json.JsValue
import play.api.mvc._
import models.Tracker
import java.util.UUID

object Application extends Controller {
  
  def index = Action { implicit request =>
    Ok(views.html.index("Your new application is ready!!!"))
  }

  def tracker = Action { implicit request =>
    Ok(views.html.tracker())
  }
  def trackerSocket = WebSocket.async[JsValue] { request =>
    Tracker.join(UUID.randomUUID().toString())
  }

  def trackerPost = Action { implicit request =>
    Ok(views.html.tracker()).as("text/javascript")
  }
  
}