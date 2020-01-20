import java.io.ByteArrayInputStream

import better.files._
import io.javalin.Javalin
import loci.communicator.ws.javalin.WS.Properties
import loci.registry.Registry
import scalatags.Text.all._

object Server {

  val js = File("/home/ragnar/Sync/Projekte/REalworld/app/target/scala-2.12/app-fastopt.js").byteArray


  def main(args: Array[String]): Unit = {

    val app = Javalin.create().start(7000);

    val websocket = loci.communicator.ws.javalin.WS(app, "ws", Properties())
    val registry  = new Registry


    val main = " <!DOCTYPE html>" + html(head(script(src := "js")), body("hallo welt")).render

    app.get("/js", ctx => {
      ctx.result(new ByteArrayInputStream(js))
    })

    app.get("/", ctx => {
      ctx.contentType("html")
      ctx.status(200)
      ctx.result(main)
    })

    registry.listen(websocket)

  }

}
