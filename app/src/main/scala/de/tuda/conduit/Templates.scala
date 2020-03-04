package de.tuda.conduit

import de.tuda.conduit.API.{Article, Author, Comment, ErrorMessages, User, UserReg}
import de.tuda.conduit.Navigation.AppState
import org.scalajs.dom.Element
import scalatags.JsDom.all._
import scalatags.JsDom.tags2.{SeqFrag => _, _}
import rescala.default._
import rescala.extra.Tags._
import rescala.reactives.RExceptions.EmptySignalControlThrowable
import scalatags.JsDom.TypedTag

object Templates {

  def navTag(currentPlace: Signal[AppState], currentUser: Signal[Option[User]]) = {

    val navlist =
      currentUser.map[List[(AppState, Modifier)]] {
        case None       => List(
          Navigation.Index -> stringFrag("""Home"""),
          Navigation.Login -> stringFrag("""Log in"""),
          Navigation.Register -> stringFrag("""Sign up""")
          )
        case Some(user) => List(
          Navigation.Index -> stringFrag("""Home"""),
          Navigation.Compose -> i(`class` := "ion-compose", raw("&nbsp;New article")),
          Navigation.Settings -> i(`class` := "ion-gear-a", raw("&nbsp;Settings")),
          Navigation.Profile(user.username) -> stringFrag(user.username)
          )
      }

    val listitems = navlist.map { items =>
      items.map { case (place, description) =>
        li(
          `class` := currentPlace.map { as =>
            (if (as == place) "nav-item active" else "nav-item")
          },
          a(`class` := "nav-link", href := place.url, description))
      }
    }

    nav(`class` := "navbar navbar-light",
        div(`class` := "container",
            a(`class` := "navbar-brand", href := Navigation.Index.url, "conduit"),
            ul(`class` := "nav navbar-nav pull-xs-right")(listitems.asModifierL))
        )
  }

  val footerTag = footer(raw("""
      <div class="container">
        <a href="/" class="logo-font">conduit</a>
        <span class="attribution">
          An interactive learning project from <a href="https://thinkster.io">Thinkster</a>. Code &amp; design licensed under MIT.
        </span>
      </div>"""))


  def tagsTag = raw("""
        <div class="col-md-3">
          <div class="sidebar">
            <p>Popular Tags</p>

            <div class="tag-list">
              <a href="" class="tag-pill tag-default">programming</a>
              <a href="" class="tag-pill tag-default">javascript</a>
              <a href="" class="tag-pill tag-default">emberjs</a>
              <a href="" class="tag-pill tag-default">angularjs</a>
              <a href="" class="tag-pill tag-default">react</a>
              <a href="" class="tag-pill tag-default">mean</a>
              <a href="" class="tag-pill tag-default">node</a>
              <a href="" class="tag-pill tag-default">rails</a>
            </div>
          </div>
        </div>""")

  def feedToggle = raw("""
          <div class="feed-toggle">
            <ul class="nav nav-pills outline-active">
              <li class="nav-item">
                <a class="nav-link disabled" href="">Your Feed</a>
              </li>
              <li class="nav-item">
                <a class="nav-link active" href="">Global Feed</a>
              </li>
            </ul>
          </div>""")

  val headerBannerTag = raw("""
  <div class="banner">
    <div class="container">
      <h1 class="logo-font">conduit</h1>
      <p>A place to share your knowledge.</p>
    </div>
  </div>""")

  def articlePreviewTag(article: Article) =
    div(`class` := "article-preview",
        authorMeta(article)(favButton(article)),
        a(href := article.url, `class` := "preview-link",
          h1(article.title), p(article.description), span("Read more ...")))


  def articleList(articles: Signal[List[Article]]) = {
    div(`class` := "home-page",
        headerBannerTag,
        div(`class` := "container page",
            div(`class` := "row",
                div(`class` := "col-md-9", feedToggle)(
                  articles.map(_.map(articlePreviewTag)).asModifierL),
                tagsTag)))
  }

  def favButton(article: Article): Tag = {
    button(`class` := "btn btn-outline-primary btn-sm pull-xs-right",
           i(`class` := "ion-heart"), article.favoritesCount)
  }

  def followButton(author: Author): Tag = {
    button(`class` := "btn btn-sm action-btn btn-outline-secondary",
           i(`class` := "ion-plus-round"),
           raw("&nbspFollow"),
           author.username)
  }

  def favoriteButton(article: Article): Tag = {
    button(`class` := "btn btn-sm btn-outline-primary",
           i(`class` := "ion-heart"),
           raw("&nbsp;Favorite Post "),
           span(`class` := "counter", article.favoritesCount)
           )
  }


  def authorMeta(article: Article): Tag = {
    div(`class` := "article-meta",
        a(href := article.author.url,
          img(src := article.author.image)),
        div(`class` := "info",
            a(href := article.author.url, `class` := "author", article.author.username),
            span(`class` := "date", article.createdAt))
        )
  }

  object authentication {


    val nameInput = input(`class` := "form-control form-control-lg",
                          `type` := "text",
                          placeholder := "Your Name").render

    val mailInput     = input(`class` := "form-control form-control-lg",
                              `type` := "text",
                              placeholder := "Email").render
    val passwordInput = input(`class` := "form-control form-control-lg",
                              `type` := "password",
                              placeholder := "Password").render

    private val registerEvt = Evt[Any]
    val registerUser = registerEvt.map { _ =>
      UserReg(username = nameInput.value, password = passwordInput.value, email = mailInput.value)
    }

    private val loginEvt = Evt[Any]
    val loginUser = loginEvt.map { _ =>
      UserReg(password = passwordInput.value, email = mailInput.value)
    }


    def wrapping(inner: Tag*) = {
      div(`class` := "auth-page",
          div(`class` := "container page",
              div(`class` := "row",
                  div(`class` := "col-md-6 offset-md-3 col-xs-12", SeqFrag(inner)))))
    }


    def errors(messages: Signal[Option[ErrorMessages]]) = {
      val listitems = messages.map(m => m.map(_.errors).getOrElse(Map.empty).toList.map {
        case (name, message) => li(name, " ", message.mkString(" and "))
      })
      ul(`class` := "error-messages", listitems.asModifierL)
    }


    def register(messages: Signal[Option[ErrorMessages]]) = wrapping(
      h1(`class` := "text-xs-center", "Sign up"),
      p(`class` := "text-xs-center", a(href := Navigation.Login.url, "Have an account?")),
      errors(messages),
      form(
        onsubmit := registerEvt,
        SeqFrag(List(nameInput, mailInput, passwordInput).map(ip => fieldset(`class` := "form-group", ip))),
        button(`class` := "btn btn-lg btn-primary pull-xs-right", "Sign up")
        ))

    def login(messages: Signal[Option[ErrorMessages]]) = wrapping(
      h1(`class` := "text-xs-center", "Log in"),
      p(`class` := "text-xs-center", a(href := Navigation.Register.url, "Create account?")),
      errors(messages),
      form(
        onsubmit := loginEvt,
        SeqFrag(List(mailInput, passwordInput).map(ip => fieldset(`class` := "form-group", ip))),
        button(`class` := "btn btn-lg btn-primary pull-xs-right", "Log in")
        ))
  }

  def profile(author: Signal[Author]) =
    div(`class` := "profile-page",
        div(`class` := "user-info",
            div(`class` := "container",
                author.map { auth =>
                  div(`class` := "row",
                      div(`class` := "col-xs-12 col-md-10 offset-md-1",
                          img(src := auth.image, `class` := "user-img"),
                          h4(auth.username),
                          p(stringFrag(auth.biography.getOrElse("This coud be your description"))),
                          followButton(auth)))
                }.asModifier)),
        div(`class` := "container",
            div(`class` := "row",

                div(`class` := "col-xs-12 col-md-10 offset-md-1",
                    div(`class` := "articles-toggle",
                        ul(`class` := "nav nav-pills outline-active",
                           li(`class` := "nav-item",
                              a(`class` := "nav-link active", href := "", "My Articles")),
                           li(`class` := "nav-item",
                              a(`class` := "nav-link", href := "", "Favorited Articles")))),
                    List.empty[Article].map(articlePreviewTag)
                    ))))


  val settings = div(`class` := "settings-page", raw("""
        <div class="container page">
          <div class="row">

            <div class="col-md-6 offset-md-3 col-xs-12">
              <h1 class="text-xs-center">Your Settings</h1>

              <form>
                <fieldset>
                  <fieldset class="form-group">
                    <input class="form-control" type="text" placeholder="URL of profile picture">
                    </fieldset>
                    <fieldset class="form-group">
                      <input class="form-control form-control-lg" type="text" placeholder="Your Name">
                      </fieldset>
                      <fieldset class="form-group">
                        <textarea class="form-control form-control-lg" rows="8" placeholder="Short bio about you"></textarea>
                      </fieldset>
                      <fieldset class="form-group">
                        <input class="form-control form-control-lg" type="text" placeholder="Email">
                        </fieldset>
                        <fieldset class="form-group">
                          <input class="form-control form-control-lg" type="password" placeholder="Password">
                          </fieldset>
                          <button class="btn btn-lg btn-primary pull-xs-right">
                            Update Settings
                          </button>
                        </fieldset>
                      </form>
                    </div>

                  </div>
                </div>
                """))

  val createEdit = div(`class` := "editor-page", raw("""
                <div class="container page">
                  <div class="row">

                    <div class="col-md-10 offset-md-1 col-xs-12">
                      <form>
                        <fieldset>
                          <fieldset class="form-group">
                            <input type="text" class="form-control form-control-lg" placeholder="Article Title">
                            </fieldset>
                            <fieldset class="form-group">
                              <input type="text" class="form-control" placeholder="What's this article about?">
                              </fieldset>
                              <fieldset class="form-group">
                                <textarea class="form-control" rows="8" placeholder="Write your article (in markdown)"></textarea>
                              </fieldset>
                              <fieldset class="form-group">
                                <input type="text" class="form-control" placeholder="Enter tags">
                                  <div class="tag-list"></div>
                                </fieldset>
                                <button class="btn btn-lg pull-xs-right btn-primary" type="button">
                                  Publish Article
                                </button>
                              </fieldset>
                            </form>
                          </div>

                        </div>
                      </div>
                      """))

  def articleFromSlug(slug: Signal[String], articles: Signal[List[Article]]): Signal[TypedTag[Element]] = {
    Signal[TypedTag[Element]] {
      articles.value.find(_.slug == slug.value).map(articleTag).getOrElse(throw EmptySignalControlThrowable)
    }
  }

  def articleTag(article: Article): TypedTag[Element] = {

    val authorTags = authorMeta(article)(followButton(article.author), raw("&nbsp;&nbsp;"), favoriteButton(article))
    val banner     = div(`class` := "banner",
                         div(`class` := "container",
                             h1(article.title),
                             authorTags))

    val content = div(`class` := "row article-content",
                      div(`class` := "col-md-12",
                          p(article.body)))

    val writeCommentTag = form(`class` := "card comment-form",
                               div(`class` := "card-block",
                                   textarea(`class` := "form-control", placeholder := "Write a comment...", rows := "3")),
                               div(`class` := "card-footer",
                                   img(src := "http://i.imgur.com/Qr71crq.jpg", `class` := "comment-author-img"),
                                   button(`class` := "btn btn-sm btn-primary", "Post Comment")))

    def comment(comment: Comment) = div(`class` := "card",
                                        div(`class` := "card-block",
                                            p(`class` := "card-text" > "With supporting text below as a natural lead-in to additional content.")),
                                        div(`class` := "card-footer",
                                            a(href := "", `class` := "comment-author",
                                              img(src := "http://i.imgur.com/Qr71crq.jpg", `class` := "comment-author-img")),
                                            raw("&nbsp;"),
                                            a(href := "", `class` := "comment-author", comment.author.username),
                                            span(`class` := "date-posted", comment.createdAt),
                                            span(`class` := "mod-options",
                                                 i(`class` := "ion-edit"),
                                                 i(`class` := "ion-trash-a"))))

    div(`class` := "article-page", banner,
        div(`class` := "container page", content, hr, authorTags,
            div(`class` := "row",
                div(`class` := "col-xs-12 col-md-8 offset-md-2",
                    writeCommentTag)(List.empty[Comment].map(comment): _*))))
  }
}
