package de.tuda.conduit

import de.tuda.conduit.API.{User, UserReg}
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
import org.scalajs.dom.experimental.{AbortSignal, BodyInit, Fetch, HeadersInit, HttpMethod, ReferrerPolicy, RequestCache, RequestCredentials, RequestInit, RequestMode, RequestRedirect, URL}
import rescala.core.{Pulse, Scheduler, Struct}
import rescala.{default, reactives}
import scalatags.jsdom.Frag

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import rescala.reactives.RExceptions.EmptySignalControlThrowable
import upickle.default.ReadWriter

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.{UndefOr, |}
import scala.util.{Failure, Random}


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

object API {
  val baseurl = "https://conduit.productionready.io/api"


  case class Author(username: String,
                    bio: String,
                    image: String,
                    following: Boolean) {
    def url: String = Navigation.Author(username).url
  }

  case class Article(slug: String,
                     title: String,
                     description: String,
                     body: String,
                     tagList: List[String],
                     createdAt: String,
                     updatedAt: String,
                     favorited: Boolean,
                     favoritesCount: Int,
                     author: Author) {

    def url: String = Navigation.Reader(slug).url
  }

  case class ArticleList(articles: List[Article], articlesCount: Int)

  case class Comment(id: Int, createdAt: String, updatedAt: String, body: String, author: Author)
  case class CommentList(comments: List[Comment])

  case class UserReg(username: String, password: String, email: String, bio: Option[String] = None, image: Option[String] = None)
  case class User(id: Int, token: String, username: String, password: String, email: String, bio: Option[String] = None, image: Option[String] = None)
  case class UserRegWrapper(user: UserReg)
  case class UserWrapper(user: User)


  implicit val AuthorRW        : ReadWriter[Author]         = upickle.default.macroRW
  implicit val ArticleRW       : ReadWriter[Article]        = upickle.default.macroRW
  implicit val ArticleListRW   : ReadWriter[ArticleList]    = upickle.default.macroRW
  implicit val CommentRW       : ReadWriter[Comment]        = upickle.default.macroRW
  implicit val CommentListRW   : ReadWriter[CommentList]    = upickle.default.macroRW
  implicit val UserRW          : ReadWriter[User]           = upickle.default.macroRW
  implicit val UserRegRW       : ReadWriter[UserReg]        = upickle.default.macroRW
  implicit val UserRegWrapperRW: ReadWriter[UserRegWrapper] = upickle.default.macroRW
  implicit val UserWrapperRW   : ReadWriter[UserWrapper]    = upickle.default.macroRW

  def articles(): Future[List[Article]] =
    Fetch.fetch(baseurl + "/articles").toFuture.flatMap(_.text().toFuture)
         .map { response =>
           upickle.default.read[ArticleList](response).articles
         }

  def comments(slug: String): Future[List[Comment]] = {
    Fetch.fetch(baseurl + s"/articles/$slug/comments").toFuture.flatMap(_.text().toFuture)
         .map { response =>
           upickle.default.read[CommentList](response).comments
         }
  }

  def registerFun(user: UserReg): Future[User] = {
    val serializedUser = upickle.default.write(UserRegWrapper(user))
    println(serializedUser)
    Fetch.fetch(baseurl + "/users",
                js.Dynamic.literal(
                  method = HttpMethod.POST,
                  body = serializedUser,
                  headers = js.Dictionary("Content-Type" -> "application/json;charset=utf-8")
                  ).asInstanceOf[RequestInit]).toFuture.flatMap(_.text().toFuture)
         .map { response =>
           upickle.default.read[UserWrapper](response).user
         }

  }

  def loginFun(user: UserReg): Future[User] = {
    val serializedUser = upickle.default.write(UserRegWrapper(user))
    Fetch.fetch(baseurl + "/users/login",
                js.Dynamic.literal(
                  method = HttpMethod.POST,
                  body = serializedUser,
                  headers = js.Dictionary("Content-Type" -> "application/json;charset=utf-8")
                  ).asInstanceOf[RequestInit]).toFuture.flatMap(_.text().toFuture)
         .map { response =>
           upickle.default.read[UserWrapper](response).user
         }

  }

  val login: PullEvent[UserReg, User, default.Structure] = PullEvent.from(loginFun)
  val register: PullEvent[UserReg, User, default.Structure] = PullEvent.from(registerFun)
  val currentUser: Signal[Option[User]] = (login.event || register.event).latestOption()

}

trait PullEvent[A, T, S <: Struct] {
  def apply(value: A)(implicit scheduler: Scheduler[S], executor: ExecutionContext): Future[T]
  def event: rescala.reactives.Event[T, S]
}

object PullEvent {
  def from[A, T, S <: Struct](callback: A => Future[T])(implicit creationTicket: rescala.core.CreationTicket[S]): PullEvent[A, T, S] = {
    val evt = rescala.reactives.Evt[T, S]()(creationTicket)
    new PullEvent[A, T, S] {
      override def apply(value: A)(implicit scheduler: Scheduler[S], executor: ExecutionContext): Future[T] = {
        callback(value).andThen { case res =>
          scheduler.forceNewTransaction(evt) { at =>
            evt.admitPulse(Pulse.fromTry(res))(at)
          }
        }
      }
      override def event: reactives.Event[T, S] = evt
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

    val mainArticles = Signals.fromFuture(API.articles())

    val slug           = Navigation.currentAppState.map {
      case Reader(slug) => slug
      case _            => throw EmptySignalControlThrowable
    }
    val currentArticle = Templates.articleFromSlug(slug, mainArticles)


    dom.document.body = body(Templates.navTag(Navigation.currentAppState),
                             Signal {
                               Navigation.currentAppState.value match {
                                 case Index            => Templates.articleList(mainArticles)
                                 case Settings         => Templates.settings
                                 case Login | Register => Templates.signup
                                 case Compose          => Templates.createEdit
                                 case Author(username) => Templates.profile
                                 case Reader(slug)     => currentArticle.value
                               }
                             }.asModifier,
                             Templates.footerTag).render

    println("fetching articles")

    //val bodyParent = dom.document.body.parentElement
    //bodyParent.removeChild(dom.document.body)
    //bodySignal.asModifier.applyTo(bodyParent)
  }
}

object Navigation {

  sealed abstract class AppState(val urlhash: String) {
    def url = s"#$urlhash"
  }
  case object Index extends AppState("")
  case object Settings extends AppState("settings")
  case object Login extends AppState("login")
  case object Register extends AppState("register")
  case object Compose extends AppState("compose")
  case class Author(username: String) extends AppState(s"profile/$username")
  case class Reader(slug: String) extends AppState(s"reader/$slug")


  def pathToState(path: String): AppState = {
    val paths = List(path.substring(1).split("/"): _*)
    scribe.debug(s"get state for $paths")
    paths match {
      case Nil | "" :: Nil              => Index
      case "settings" :: Nil            => Settings
      case "login" :: Nil               => Login
      case "register" :: Nil            => Register
      case "compose" :: Nil             => Compose
      case "reader" :: slug :: Nil      => Reader(slug)
      case "profile" :: username :: Nil => Author(username)
      case _                            => Index
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
