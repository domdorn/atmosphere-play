package org.atmosphere.play.old

import java.util.Date

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.ByteString
import javax.inject.Inject
import org.atmosphere.cpr.{AtmosphereConfig, AtmosphereFramework, HeaderConfig}
import play.api.http.{HttpChunk, HttpEntity}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._

class AtmosphereScalaController @Inject() (
                                            cc: ControllerComponents,
                                            implicit val actorSystem: ActorSystem,
                                            implicit val materializer: Materializer,
                                          ) extends AbstractController(cc) {

  val framework: AtmosphereFramework = AtmosphereCoordinator.instance().framework()
  val config: AtmosphereConfig = framework.getAtmosphereConfig
  val playSessionConverter = Option(config.getInitParameter(AtmosphereCoordinator.PLAY_SESSION_CONVERTER))

  def webSocket2 = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef(ref => PlayAtmosphereScalaWebsocketActor.props(ref, request, request.session.data, config))
  }(MessageFlowTransformer.stringMessageFlowTransformer)

  def webSocket = cc.actionBuilder.apply {
    req =>
      NotFound
  }

  import scala.collection.JavaConverters._
  def convertedSession(session: Session): java.util.Map[String, Object] = {
    session.data.toMap[String, Object].asJava
  }


  def http = cc.actionBuilder.apply {
    request =>

      val isSSE: Boolean = request.queryString.get(HeaderConfig.X_ATMOSPHERE_TRANSPORT).exists(l => l.exists(s => s.equalsIgnoreCase(HeaderConfig.SSE_TRANSPORT)))

      if(isSSE) {
        // response.setContentType("text/event-stream")
        Ok("blabla").as("text/event-stream") // TODO
      } else {

        val (outActor, publisher) = Source.actorRef[ByteString](16, OverflowStrategy.dropNew)
          .toMat(Sink.asPublisher(false))(Keep.both).run()

        val chunkedActorRef: ActorRef = actorSystem.actorOf(PlayAtmosphereScalaChunkedActor.props(request, request.session.data, outActor))
        val source1 = Source.fromPublisher(publisher)

//        = ActorFlow.actorRef(ref => PlayAtmosphereScalaWebsocketActor.props(ref, request, request.session.data, config))
/*

        val chunkedActorRef = actorSystem.actorOf(PlayAtmosphereChunkedActor.props(
          new Http.RequestImpl(request), convertedSession(request.session)))

        val source: Source[ByteString, _] = Source.actorRef[ByteString](256, OverflowStrategy.dropNew)
          .mapMaterializedValue(sourceActor => {
            val destinationActor = sourceActor

            println("telling the chunkedActorRef " + chunkedActorRef + " the address of the sourceActor " + sourceActor)
            chunkedActorRef.tell(destinationActor, ActorRef.noSender)
            //                    sourceActor.tell(ByteString.fromString("blabla1"), ActorRef.noSender());
            //                    sourceActor.tell(ByteString.fromString("blabla2"), ActorRef.noSender());
            //                    return sourceActor;
            NotUsed.getInstance

          })*/

        import scala.concurrent.duration._
        val source: Source[ByteString, _] = Source.tick(10 millis, 500 millis, new Date())
          .map(s => ByteString.fromString(s.toString))

        Result(
          header = ResponseHeader(200, Map.empty),
          body = HttpEntity.Chunked(source.map(s => HttpChunk.Chunk(s)), None)
//          body = HttpEntity.Streamed(source, None, None)
        )
      }

  }

}
