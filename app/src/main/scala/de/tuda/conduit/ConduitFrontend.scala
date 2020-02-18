package de.tuda.conduit

import de.tuda.conduit.Navigation._
import org.scalajs.dom
import rescala.default._
import rescala.extra.Tags._
import rescala.extra.lattices.IdUtil
import rescala.extra.lattices.IdUtil.Id
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.body

import scala.scalajs.js.URIUtils.decodeURIComponent
import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.experimental.URL

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal


//@JSImport("localforage", JSImport.Namespace)
@JSGlobal
@js.native
object localforage extends js.Object with LocalForageInstance {
  def createInstance(config: js.Any): LocalForageInstance = js.native
}

@js.native
trait LocalForageInstance extends js.Object {
  def setItem(key: String, value: js.Any): js.Promise[Unit] = js.native
  def getItem[T](key: String): js.Promise[T] = js.native
}


object ConduitFrontend {

  val replicaID: Id = IdUtil.genId()

  def main(args: Array[String]): Unit = {
    dom.document.body = body(Templates.nav,
                                 Navigation.currentAppState.map {
                                   case Index            => Templates.home
                                   case Settings         => Templates.settings
                                   case Login | Register => Templates.login
                                 }.asModifier,
                                 Templates.footer)
    //val bodyParent = dom.document.body.parentElement
    //bodyParent.removeChild(dom.document.body)
    //bodySignal.asModifier.applyTo(bodyParent)
  }
}

object Navigation {

  sealed trait AppState
  case object Index extends AppState
  case object Settings extends AppState
  case object Login extends AppState
  case object Register extends AppState

  def pathToState(path: String): AppState = {
    val paths = List(path.substring(1).split("/"): _*)
    scribe.debug(s"get state for $paths")
    paths match {
      case Nil | "" :: Nil   => Index
      case "settings" :: Nil => Settings
      case "login" :: Nil    => Login
      case "register" :: Nil => Register
      case _                 => Index
    }
  }

  def getHash: String = {
    dom.window.location.hash
  }

  val hashChange: Event[HashChangeEvent] =
    Events.fromCallback[HashChangeEvent](dom.window.onhashchange = _).event
  hashChange.observe(hc => scribe.debug(s"hash change event: ${hc.oldURL} -> ${hc.newURL}"))

  val hashBasedStates = hashChange.map(hc => pathToState(new URL(hc.newURL).hash))


  val targetStates: Event[AppState] = hashBasedStates

  val initialState = pathToState(getHash)

  scribe.debug(s"initial state: $initialState")


  val currentAppState: Signal[AppState] = targetStates.latest(initialState)
}
