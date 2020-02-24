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
import scalatags.jsdom.Frag

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import org.scalajs.dom.experimental.Fetch
import rescala.reactives.RExceptions.EmptySignalControlThrowable
import upickle.default.ReadWriter

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue


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


  implicit val AuthorRW     : ReadWriter[Author]      = upickle.default.macroRW
  implicit val ArticleRW    : ReadWriter[Article]     = upickle.default.macroRW
  implicit val ArticleListRW: ReadWriter[ArticleList] = upickle.default.macroRW
  implicit val CommentRW    : ReadWriter[Comment]     = upickle.default.macroRW
  implicit val CommentListRW: ReadWriter[CommentList] = upickle.default.macroRW

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

}


object ConduitFrontend {


  val replicaID: Id = IdUtil.genId()

  def main(args: Array[String]): Unit = {

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
                                 case Login | Register => Templates.login
                                 case Compose          => Templates.createEdit
                                 case Author(username) => Templates.profile
                                 case Reader(slug)     => currentArticle.value
                               }
                             }.asModifier,
                             Templates.footerTag).render

    println("fetching arrticles")

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
