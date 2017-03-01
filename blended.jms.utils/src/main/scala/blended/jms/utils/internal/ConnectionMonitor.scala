package blended.jms.utils.internal
import java.text.SimpleDateFormat

class ConnectionMonitor(provider: String) extends ConnectionMonitorMBean {

  private[this] val df = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS")
  private[this] var state : ConnectionState = ConnectionState()

  private[this] var cmd : ConnectionCommand = ConnectionCommand(provider = provider)

  override def getProvider(): String = provider

  def getCommand() : ConnectionCommand = cmd
  def resetCommand() : Unit = { cmd = ConnectionCommand(provider = provider) }

  def setState(newState: ConnectionState) : Unit = { state = newState }
  def getState() : ConnectionState = state

  override def getStatus(): String = state.status

  override def getLastConnect(): String = state.lastConnect match {
    case None => "n/a"
    case Some(d) => df.format(d)
  }

  override def getLastDisconnect(): String = state.lastDisconnect match {
    case None => "n/a"
    case Some(d) => df.format(d)
  }

  override def getFailedPings(): Int = state.failedPings

  override def getMaxEvents(): Int = state.maxEvents

  override def setMaxEvents(n: Int): Unit = { cmd = cmd.copy(maxEvents = n) }

  override def getEvents(): Array[String] = state.events.toArray

  override def disconnect(reason: String): Unit = { cmd = cmd.copy(disconnectPending = true) }

  override def connect(now: Boolean): Unit = { cmd = cmd.copy(connectPending = true, reconnectNow = now) }
}
