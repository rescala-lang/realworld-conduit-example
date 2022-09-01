package de.tuda.conduit

import org.scalajs.dom.{Fetch, HttpMethod, RequestInit}
import rescala.default._
import upickle.default._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object API {
  val baseurl = "https://conduit.productionready.io/api/"


  case class Author(username: String,
                    bio: String,
                    image: String = "",
                    following: Boolean = false) {
    def url: String = Navigation.Profile(username).url
    def biography: Option[String] = Option(bio)
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
  case class ArticleWrapper(article: Article, articlesCount: Int)

  case class Comment(id: Int, createdAt: String, updatedAt: String, body: String, author: Author)
  case class CommentList(comments: List[Comment])

  case class UserReg(username: String = "", password: String, email: String, bio: String = "", image: String = "")
  case class User(id: Int, token: String, username: String, email: String, bio: Option[String] = None, image: Option[String] = None)
  case class UserRegWrapper(user: UserReg)
  case class UserWrapper(user: User)
  case class ProfileWrapper(profile: Author)

  case class ErrorMessages(errors: Map[String, List[String]])
  case class CategoriesList(tags: List[String])

  case class ArticleDraft(title: String, description: String, body: String, tagList: List[String])
  case class ArticleDraftWrapper(article: ArticleDraft)


  implicit val AuthorRW             : ReadWriter[Author]              = upickle.default.macroRW
  implicit val ArticleRW            : ReadWriter[Article]             = upickle.default.macroRW
  implicit val ArticleListRW        : ReadWriter[ArticleList]         = upickle.default.macroRW
  implicit val CommentRW            : ReadWriter[Comment]             = upickle.default.macroRW
  implicit val CommentListRW        : ReadWriter[CommentList]         = upickle.default.macroRW
  implicit val UserRW               : ReadWriter[User]                = upickle.default.macroRW
  implicit val UserRegRW            : ReadWriter[UserReg]             = upickle.default.macroRW
  implicit val UserRegWrapperRW     : ReadWriter[UserRegWrapper]      = upickle.default.macroRW
  implicit val UserWrapperRW        : ReadWriter[UserWrapper]         = upickle.default.macroRW
  implicit val ErrorMessagesRW      : ReadWriter[ErrorMessages]       = upickle.default.macroRW
  implicit val ProfileWrapperRW     : ReadWriter[ProfileWrapper]      = upickle.default.macroRW
  implicit val CategoriesListRW     : ReadWriter[CategoriesList]      = upickle.default.macroRW
  implicit val ArticleDraftRW       : ReadWriter[ArticleDraft]        = upickle.default.macroRW
  implicit val ArticleDraftWrapperRW: ReadWriter[ArticleDraftWrapper] = upickle.default.macroRW
  implicit val ArticleWrapperRW     : ReadWriter[ArticleWrapper]      = upickle.default.macroRW

  def fetchtext(endpoint: String,
                method: HttpMethod = HttpMethod.GET,
                body: Option[String] = None,
                authentication: Option[User] = None): Future[String] = {

    val ri = js.Dynamic.literal(method = method).asInstanceOf[RequestInit]

    body.foreach { content =>
      ri.body = content
      ri.headers = js.Dictionary("Content-Type" -> "application/json;charset=utf-8")
    }

    authentication.foreach{ user =>
      if (js.isUndefined(ri.headers)) ri.headers = js.Dictionary.empty[String]
      ri.headers.asInstanceOf[js.Dictionary[String]]("Authorization") = s"Token ${user.token}"
    }

    Fetch.fetch(baseurl + endpoint, ri).toFuture.flatMap(_.text().toFuture)
         .andThen { case resp => println(resp) }
  }

  def articles(): Future[List[Article]] =
    fetchtext("articles")
    .map { response =>
      upickle.default.read[ArticleList](response).articles
    }

  def comments(slug: String): Future[List[Comment]] = {
    fetchtext(s"articles/$slug/comments")
    .map { response =>
      upickle.default.read[CommentList](response).comments
    }
  }

  def userprofile(username: String): Future[Author] = {
    fetchtext(s"profiles/$username")
    .map { response =>
      try upickle.default.read[ProfileWrapper](response).profile
      catch {
        case error: Throwable =>
          println(response)
          throw error
      }
    }
  }

  def loginRegisterFun(user: UserReg, endpoint: String): Future[Either[ErrorMessages, User]] = {
    val serializedUser = upickle.default.write(UserRegWrapper(user))
    println(serializedUser)
    fetchtext(endpoint, HttpMethod.POST, Some(serializedUser))
    .map { response =>
      try {Right(upickle.default.read[UserWrapper](response).user)}
      catch {
        case error: Throwable =>
          println(error)
          Left(upickle.default.read[ErrorMessages](response))
      }
    }

  }

  def registerFun(user: UserReg): Future[Either[ErrorMessages, User]] = {
    loginRegisterFun(user, endpoint = "users")

  }

  def loginFun(user: UserReg): Future[Either[ErrorMessages, User]] = {
    loginRegisterFun(user, endpoint = "users/login")
  }

  val login   : PullEvent[UserReg, Either[ErrorMessages, User]] = PullEvent.from(loginFun)
  val register: PullEvent[UserReg, Either[ErrorMessages, User]] = PullEvent.from(registerFun)


  val loginresult                       = login.event || register.event
  val currentUser: Signal[Option[User]] =
    StorageManager.stored[Option[User]]("currentUser") { restored =>
      loginresult.map[Option[User]](_.toOption).latest(restored.flatten)
    }

  val loginRegisterErrors: Signal[Option[ErrorMessages]] = loginresult.map(_.swap.toOption).latest(None)


  def categoriesFun(): Future[List[String]] = {
    fetchtext("tags")
    .map { response =>
      try upickle.default.read[CategoriesList](response).tags
      catch {
        case error: Throwable =>
          println(response)
          throw error
      }
    }
  }

  val categoriesEvent: PullEvent[Unit, List[String]] = PullEvent.from(_ => categoriesFun())

  val categories = categoriesEvent.event.latest()

  def postArticle(article: ArticleDraft, user: User) = {
    val body = write(ArticleDraftWrapper(article))
    fetchtext("articles", HttpMethod.POST, Some(body), Some(user))
    .map { response =>
      try read[ArticleList](response)
      catch {
        case error: Throwable =>
          println(response)
          throw error
      }
    }
  }
}

