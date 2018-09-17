package org.atmosphere.play.old

import akka.actor.{Actor, ActorRef, Props}
import org.atmosphere.cpr.{AtmosphereConfig, AtmosphereResponseImpl, WebSocketProcessorFactory}
import org.atmosphere.websocket.WebSocketProcessor
import play.api.mvc.RequestHeader
import play.mvc.Http


object PlayAtmosphereScalaWebsocketActor {

  def props(
           out: ActorRef,
           request: RequestHeader,
           additionalAttributes: Map[String, Object],
           config: AtmosphereConfig
           ): Props = {
    Props.create(classOf[PlayAtmosphereScalaWebsocketActor], out, request, additionalAttributes, config)
  }

}

class PlayAtmosphereScalaWebsocketActor(
                                         out: ActorRef,
                                         request: RequestHeader,
                                         additionalAttributes: Map[String, Object],
                                         config: AtmosphereConfig
                                       ) extends Actor {

  var atmosphereWebsocket: PlayAtmosphereWebsocket = _
  var processor: WebSocketProcessor = _

  override def preStart(): Unit = {
    self ! "start"
  }

  override def postStop(): Unit = {
    processor.close(atmosphereWebsocket, 1002)
  }


  override def receive: Receive = {
    case "start" => {
      atmosphereWebsocket = new PlayAtmosphereWebsocket(config, out, self)
      this.processor = WebSocketProcessorFactory.getDefault.getWebSocketProcessor(config.framework)
      import scala.collection.JavaConverters._
      val r = AtmosphereUtils.request(new Http.RequestImpl(request), additionalAttributes.asJava)
      this.processor.open(atmosphereWebsocket, r, AtmosphereResponseImpl.newInstance(config, r, atmosphereWebsocket))
    }
    case s => {
      println("received message of type " + s.getClass + " with content " + s)
      processor.invokeWebSocketProtocol(atmosphereWebsocket, s.toString)
    }
  }
}
