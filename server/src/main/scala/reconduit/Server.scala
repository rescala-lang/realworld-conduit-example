package reconduit

import better.files._
import io.javalin.Javalin
import loci.communicator.ws.javalin.WS.Properties
import loci.registry.Registry
import scalatags.Text.all._

object Server {

  val head = """
  <head>
    <meta charset="utf-8">
    <title>Conduit</title>
    <!-- Import Ionicon icons & Google Fonts our Bootstrap theme relies on -->
    <link href="//code.ionicframework.com/ionicons/2.0.1/css/ionicons.min.css" rel="stylesheet" type="text/css">
    <link href="//fonts.googleapis.com/css?family=Titillium+Web:700|Source+Serif+Pro:400,700|Merriweather+Sans:400,700|Source+Sans+Pro:400,300,600,700,300italic,400italic,600italic,700italic" rel="stylesheet" type="text/css">
    <!-- Import the custom Bootstrap 4 theme from our hosted CDN -->
    <link rel="stylesheet" href="//demo.productionready.io/main.css">
  </head>"""


  def main(args: Array[String]): Unit = {

    val app = Javalin.create().start(7000);

    val websocket = loci.communicator.ws.javalin.WS(app, "ws", Properties())
    val registry  = new Registry

    val resources = List("localforage.min.js.gz", "app-fastopt.js.map.gz", "app-fastopt.js.gz")

    def noZip(s: String) = if (s.endsWith(".gz")) s.substring(0, s.length - 3) else s

    val main = "<!DOCTYPE html>" + html(raw(head), body("executing JS …")(resources.filter(_.endsWith(".js.gz")).map(noZip).map(p => script(src := p)))).render

    for {
      path <- resources
    } {
      app.get(s"/${noZip(path)}", ctx => {
        if (path.endsWith(".js.gz")) ctx.contentType("application/javascript")
        if (path.endsWith(".gz")) ctx.header("Content-Encoding", "gzip")
        ctx.result(File(s"target/resources/static/$path").newInputStream.buffered)
      })
    }


    app.get("/", ctx => {
      ctx.contentType("text/html; charset=UTF-8")
      ctx.status(200)
      ctx.result(main)
    })

    registry.listen(websocket)

  }

}
