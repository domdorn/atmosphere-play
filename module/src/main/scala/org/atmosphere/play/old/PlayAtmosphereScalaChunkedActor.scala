package org.atmosphere.play.old

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.util.ByteString
import org.atmosphere.cpr.HeaderConfig
import play.api.mvc.RequestHeader


object PlayAtmosphereScalaChunkedActor {
  def props(request: RequestHeader, additionalProperties: Map[String, Object], out: ActorRef) = Props
    .create(classOf[PlayAtmosphereScalaChunkedActor], request, additionalProperties, out)
}

class PlayAtmosphereScalaChunkedActor(request: RequestHeader, additionalProperties: Map[String, Object], out: ActorRef) extends Actor with ActorLogging {

  val asyncIOWriter = new PlayAtmosphereScalaChunkedAsyncIOWriter(request, additionalProperties, self)

  context.watch(out)

  override def receive: Receive = {
    case Terminated => {
      println("PlayAtmosphereScalaChunkedActor: child stopped")
      if(request.queryString.get(HeaderConfig.X_ATMOSPHERE_TRANSPORT).exists(l => l.exists(_.equalsIgnoreCase(HeaderConfig.POLLING_TRANSPORT)))) {
        asyncIOWriter.signalClose()
      }
      self ! PoisonPill
    }
    case bs : ByteString => {
      println("received bytestring, transforming to string: ' " + bs.toString())
      out ! bs.toString()
    }
    case s => {
      println("ScalaChunkedActor received " + s.getClass + ": " + s + " by " + sender())
      //out ! s.toString.replaceAll(" ", "X")
//      out ! "balbla"
      out ! ByteString.fromString("blub")
    }

  }
}
