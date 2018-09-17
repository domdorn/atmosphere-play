package org.atmosphere.play.old

import java.io.IOException
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.{ActorRef, PoisonPill}
import akka.util.ByteString
import org.atmosphere.cpr._
import org.atmosphere.util.ByteArrayAsyncWriter
import org.slf4j.LoggerFactory
import play.api.mvc.RequestHeader
import play.mvc.Http

class PlayAtmosphereScalaChunkedAsyncIOWriter(req: RequestHeader, additionalAttributes: Map[String, Object], out: ActorRef) extends AtmosphereInterceptorWriter {

  //  val transport = req.queryString.get(HeaderConfig.X_ATMOSPHERE_TRANSPORT)

  var keepAlive = false
  var atmosphereRequest: AtmosphereRequest = _
  var atmosphereResponse: AtmosphereResponse = _
  val logger = LoggerFactory.getLogger(classOf[PlayAtmosphereChunkedAsyncIOWriter])
  val pendingWrite = new AtomicInteger
  val asyncClose = new AtomicBoolean(false)
  val _isClosed = new AtomicBoolean(false)
  val buffer = new ByteArrayAsyncWriter
  var _byteWritten = false
  var _lastWrite = 0L
  var resumeOnBroadcast = false


  import scala.collection.JavaConverters._

  try {
    atmosphereRequest = AtmosphereUtils.request(new Http.RequestImpl(req), additionalAttributes.asJava)
    atmosphereResponse = new AtmosphereResponseImpl.Builder()
      .asyncIOWriter(this)
      .writeHeader(false)
      .request(atmosphereRequest).build()
    keepAlive = AtmosphereCoordinator.instance().route(atmosphereRequest, atmosphereResponse)
  } catch {
    case e: Throwable =>
      e.printStackTrace()
      keepAlive = true
  } finally {
    if (!keepAlive) {
      out.tell(PoisonPill, ActorRef.noSender)
    }
  }




  def isClosed: Boolean = _isClosed.get

  def byteWritten: Boolean = _byteWritten

  def resumeOnBroadcast(resumeOnBroadcast: Boolean): Unit = {
    this.resumeOnBroadcast = resumeOnBroadcast
  }

  @throws[IOException]
  override def writeError(r: AtmosphereResponse, errorCode: Int, message: String): AsyncIOWriter = { //        if (!response.isOpen()) {
    //            return this;
    //        }
    // TODO: Set status
    logger.error("Error {}:{}", errorCode, message)
    println("in asyncIOWriter.writeError: " + message)
    out.tell(ByteString.fromString(message), ActorRef.noSender)
    this
  }

  @throws[IOException]
  override def write(r: AtmosphereResponse, data: String): AsyncIOWriter = {
    val b = data.getBytes("ISO-8859-1")
    write(r, b)
    this
  }

  @throws[IOException]
  override def write(r: AtmosphereResponse, data: Array[Byte]): AsyncIOWriter = {
    write(r, data, 0, data.length)
    this
  }

  @throws[IOException]
  protected def transform(response: AtmosphereResponse, b: Array[Byte], offset: Int, length: Int): Array[Byte] = {
    val a = response.getAsyncIOWriter
    try {
      response.asyncIOWriter(buffer)
      invokeInterceptor(response, b, offset, length)
      buffer.stream.toByteArray
    } finally {
      buffer.close(null)
      response.asyncIOWriter(a)
    }
  }

  @throws[IOException]
  override def write(r: AtmosphereResponse, data: Array[Byte], offset: Int, length: Int): AsyncIOWriter = {
//    logger.trace(s"Writing ${r.resource.uuid} with transport ${r.resource().transport()}")
    println("in asyncIOWriter:write " + r)
    val shouldTransform: Boolean = filters.size > 0 && r.getStatus < 400
    var newData: Array[Byte] = data
    var newOffset: Int = offset
    var newLength: Int = length
    if (shouldTransform) {
      newData = transform(r, data, offset, length)
      newOffset = 0
      newLength = data.length
    }
    pendingWrite.incrementAndGet
//    out.tell(ByteString.fromString(new String(newData, newOffset, newLength, r.getCharacterEncoding)), ActorRef.noSender)
    println("before telling")
    out.tell(new String(newData, newOffset, newLength, r.getCharacterEncoding), ActorRef.noSender)
    _byteWritten = true
    _lastWrite = System.currentTimeMillis
    if (resumeOnBroadcast) {
      out.tell(PoisonPill.getInstance, ActorRef.noSender)
      _close(r.request)
    }
    this
  }

  private def _close(request: AtmosphereRequest): Unit = {
    val r = classOf[AtmosphereResourceImpl].cast(request.resource)
    if (request != null && r != null) {
      classOf[AsynchronousProcessor].cast(r.getAtmosphereConfig.framework.getAsyncSupport).endRequest(r, true)
    }
  }

  def lastTick: Long = {
    if (_lastWrite == -1)
      System.currentTimeMillis
    else
      _lastWrite
  }


  def signalClose(): Unit = {
    _close(atmosphereRequest)
  }

  @throws[IOException]
  override def close(r: AtmosphereResponse): Unit = { // Make sure we don't have bufferred bytes
    if (!byteWritten && r != null && r.getOutputStream != null) {
      // TODO we don't have a servlet output stream
      r.getOutputStream.flush()
    }
    asyncClose.set(true)
    out.tell(PoisonPill.getInstance, ActorRef.noSender)
    _isClosed.set(true)
  }


}
