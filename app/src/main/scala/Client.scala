import loci.registry.Registry

object CliRent {

  def main(args: Array[String]): Unit = {

    val websocket = loci.communicator.ws.javalin.WS("ws://localhost:7000/ws")
    val registry = new Registry

    registry.connect(websocket)

  }

}
