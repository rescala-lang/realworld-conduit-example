package de.tuda.conduit

import de.tuda.conduit.API.Author
import de.tuda.conduit.Navigation._
import org.scalajs.dom
import org.scalajs.dom.URL
import org.scalajs.dom.HashChangeEvent
import rescala.default._
import rescala.extra.Tags._
import rescala.extra.lattices.IdUtil
import rescala.extra.lattices.IdUtil.Id
import rescala.operator.Pulse
import rescala.operator.RExceptions.EmptySignalControlThrowable
import scalatags.JsDom.tags.body
import upickle.default.ReadWriter

import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.util.Try


//@JSImport("localforage", JSImport.Namespace)
@JSGlobal
@js.native
@nowarn
object localforage extends js.Object with LocalForageInstance {
  def createInstance(config: js.Any): LocalForageInstance = js.native
}

@js.native
@nowarn
trait LocalForageInstance extends js.Object {
  def setItem(key: String, value: js.Any): js.Promise[Unit] = js.native
  def getItem[T](key: String): js.Promise[T] = js.native
}


object StorageManager {
  val storage = dom.window.localStorage
  def get[T: upickle.default.Reader](key: String): Option[T] =
    Option(storage.getItem(key)).flatMap(str => Try {upickle.default.read(str)}.toOption)

  def stored[T: ReadWriter](key: String)(create: Option[T] => Signal[T]): Signal[T] = {
    val res = create(get[T](key))
    res.observe(value => storage.setItem(key, upickle.default.write(value)))
    res
  }
}

trait PullEvent[A, T] {
  def pull(value: A)(implicit scheduler: Scheduler, executor: ExecutionContext): Future[T]
  def event: Event[T]
}

object PullEvent {
  def from[A, T](callback: A => Future[T])(implicit creationTicket: CreationTicket): PullEvent[A, T] = {
    val evt = Evt[T]()(creationTicket)
    new PullEvent[A, T] {
      override def pull(value: A)(implicit scheduler: Scheduler, executor: ExecutionContext): Future[T] = {
        callback(value).andThen { case res =>
          scheduler.forceNewTransaction(evt) { at =>
            evt.admitPulse(Pulse.fromTry(res))(at)
          }
        }
      }
      override def event: Event[T] = evt
    }
  }
}


object ConduitFrontend {


  val replicaID: Id = IdUtil.genId()

  def main(args: Array[String]): Unit = {

    //val randid = Random.nextInt().toString
    //
    //val user = UserReg(username = "res" + randid, email = s"restest$randid@example.com", password = "1234567890")
    //
    //API.register(user).onComplete(println)

    Templates.authentication.loginUser.observe{x => API.login.pull(x); ()}
    Templates.authentication.registerUser.observe{x => API.register.pull(x); ()}
    Templates.writeArticle.draftEvent
             .collect { Function.unlift(draft => API.currentUser.value.map(draft -> _)) }
             .observe { case (draft, user) =>
               API.postArticle(draft, user).onComplete {
                 res => println(res)
               }
             }

    val mainArticles = Signals.fromFuture(API.articles())

    val slug           = Navigation.currentAppState.map {
      case Reader(slug) => slug
      case _            => throw EmptySignalControlThrowable
    }
    val currentArticle = Templates.articleFromSlug(slug, mainArticles)

    val profileEvent = PullEvent.from(API.userprofile)

    val profileTag = Templates.profile(profileEvent.event.latest[Author]())

    val currentCategory = currentAppState.map {
      case Category(name) => Some(name)
      case _              => None
    }

    val tagArticles = currentCategory.map {
      case Some(name) => mainArticles.value.filter(_.tagList.contains(name))
      case None       => Nil
    }

    val categoriesTag = scalatags.JsDom.all.bindNode(Templates.categoriesList(API.categories).render)

    API.categoriesEvent.pull(())


    dom.document.body = body(Templates.navTag(Navigation.currentAppState, API.currentUser),
                             Signal {
                               Navigation.currentAppState.value match {
                                 case Index             => Templates.articleList(mainArticles, categoriesTag)
                                 case Settings          => Templates.settings
                                 case Register          => Templates.authentication.register(API.loginRegisterErrors)
                                 case Login             => Templates.authentication.login(API.loginRegisterErrors)
                                 case Compose           => Templates.writeArticle.editorTag
                                 case Profile(username) =>
                                   profileEvent.pull(username)
                                   profileTag
                                 case Reader(slug)      => currentArticle.value
                                 case Category(tagname) => Templates.articleList(tagArticles, categoriesTag)
                               }
                             }.asModifier,
                             Templates.footerTag).render

    //val bodyParent = dom.document.body.parentElement
    //bodyParent.removeChild(dom.document.body)
    //bodySignal.asModifier.applyTo(bodyParent)
  }
}

object Navigation {

  sealed abstract class AppState(val urlhash: String) {
    def url = s"#$urlhash"
  }
  object AppState {
    def parse(path: String): AppState = {
      val paths = path.substring(1).split("/").toList
      scribe.debug(s"get state for $paths")
      paths match {
        case "settings" :: Nil            => Settings
        case "login" :: Nil               => Login
        case "register" :: Nil            => Register
        case "compose" :: Nil             => Compose
        case "reader" :: slug :: Nil      => Reader(slug)
        case "profile" :: username :: Nil => Profile(username)
        case "tag" :: tagname :: Nil      => Category(tagname)
        case _                            => Index
      }
    }
  }
  case object Index extends AppState("")
  case object Settings extends AppState("settings")
  case object Login extends AppState("login")
  case object Register extends AppState("register")
  case object Compose extends AppState("compose")
  case class Profile(username: String) extends AppState(s"profile/$username")
  case class Reader(slug: String) extends AppState(s"reader/$slug")
  case class Category(name: String) extends AppState(s"tag/$name")


  def getHash: String = {
    dom.window.location.hash
  }

  val hashChange: Event[HashChangeEvent] =
    Events.fromCallback[HashChangeEvent](dom.window.onhashchange = _).event
  hashChange.observe(hc => scribe.debug(s"hash change event: ${hc.oldURL} -> ${hc.newURL}"))

  val hashBasedStates = hashChange.map(hc => AppState.parse(new URL(hc.newURL).hash))


  val targetStates: Event[AppState] = hashBasedStates

  val initialState = AppState.parse(getHash)

  scribe.debug(s"initial state: $initialState")


  val currentAppState: Signal[AppState] = targetStates.latest(initialState)
}
