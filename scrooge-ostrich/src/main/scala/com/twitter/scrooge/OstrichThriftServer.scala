package com.twitter.scrooge

import com.twitter.app.GlobalFlag
import com.twitter.conversions.time._
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.stats.{StatsReceiver, OstrichStatsReceiver}
import com.twitter.finagle.ThriftMuxServer
import com.twitter.finagle.thrift.{Protocols, ThriftServerFramedCodec}
import com.twitter.finagle.tracing.{NullTracer, Tracer}
import com.twitter.finagle.Service
import com.twitter.logging.Logger
import com.twitter.ostrich
import com.twitter.util.Duration
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import org.apache.thrift.protocol.TProtocolFactory

/**
 * A [[com.twitter.app.GlobalFlag]] for enabling use of ThriftMux.
 */
object enableThriftMuxServer
  extends GlobalFlag(false, "If set to true, Thrift server will be served over ThriftMux")

/**
 * This trait is intended as a near-drop-in replacement for the ThriftServer class generated by
 * scrooge, which will soon no longer generated. Most use-cases are covered by simply changing
 * a class declaration that looks like "X extends Y.ThriftServer" to
 * "X extends Y.FutureIface with OstrichThriftServer".
 *
 * Ostrich in general is now deprecated, and long-term, you should switch to twitter-server.
 */
trait OstrichThriftServer extends ostrich.admin.Service {
  val log = Logger.get(getClass)

  def thriftCodec = ThriftServerFramedCodec()
  def statsReceiver: StatsReceiver = new OstrichStatsReceiver
  def tracer: Tracer = NullTracer
  @deprecated("use tracer instead", "3.3.3")
  def tracerFactory: Tracer.Factory = NullTracer.factory
  val thriftProtocolFactory: TProtocolFactory = Protocols.binaryFactory()
  val thriftPort: Int
  val serverName: String

  protected def finagleService: Service[Array[Byte], Array[Byte]] =
    findFutureIface(getClass) match {
      case None => throw new IllegalStateException("Can't infer Service class, you must implement finagleService method")
      case Some(iface) =>
        val serviceName = iface.getName.dropRight(12) + "$FinagledService"
        val serviceClass = Class.forName(serviceName)
        val constructor = serviceClass.getConstructor(iface, classOf[TProtocolFactory])
        constructor.newInstance(this, thriftProtocolFactory)
          .asInstanceOf[Service[Array[Byte], Array[Byte]]]
    }

  private def findFutureIface(cls: Class[_]): scala.Option[Class[_]] =
    if (cls.getName.endsWith("$FutureIface"))
      Some(cls)
    else
      (scala.Option(cls.getSuperclass) ++ cls.getInterfaces).view.flatMap(findFutureIface).headOption

  // Must be thread-safe as different threads can start and shutdown the service.
  private[this] val _server = new AtomicReference[Server]
  def server = _server.get
  def server_=(value: Server) = _server.set(value)

  def start() {
    server_=(serverBuilder.build(finagleService))
  }

  /**
   * You can override this to provide additional configuration
   * to the ServerBuilder.
   */
  def serverBuilder = {
    val baseBuilder =
      if (enableThriftMuxServer()) {
        log.info("Configuring server to use ThriftMux")
        ServerBuilder().stack(ThriftMuxServer)
      } else {
        ServerBuilder().codec(thriftCodec)
      }

    baseBuilder.name(serverName)
      .reportTo(statsReceiver)
      .bindTo(new InetSocketAddress(thriftPort))
      .tracer(tracer)
  }

  /**
   * Close the underlying server gracefully with the given grace
   * period. close() will drain the current channels, waiting up to
   * ``timeout``, after which channels are forcibly closed.
   */
  def shutdown(timeout: Duration = 0.seconds) {
    synchronized {
      val s = server
      if (s != null) {
        s.close(timeout)
      }
    }
  }

  def shutdown() = shutdown(0.seconds)
}
