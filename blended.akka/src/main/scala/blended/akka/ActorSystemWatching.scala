package blended.akka

import akka.actor.{ActorRef, ActorSystem, Props}
import blended.akka.internal.ActorSystemCapsule
import blended.util.logging.Logger
import domino.DominoImplicits
import domino.capsule.{Capsule, CapsuleContext}
import org.osgi.framework.BundleContext

trait ActorSystemWatching extends DominoImplicits {

  private[this] val log = Logger[ActorSystemWatching]

  /** Dependency */
  protected def capsuleContext : CapsuleContext

  /** Dependency */
  protected def bundleContext : BundleContext

  def whenActorSystemAvailable(f : OSGIActorConfig => Unit) : Unit = {
    val m = new ActorSystemCapsule(capsuleContext, f, bundleContext)
    capsuleContext.addCapsule(m)
  }

  def setupBundleActor(cfg : OSGIActorConfig, props : Props) : ActorRef = setupBundleActor(cfg.system, props)

  def setupBundleActor(system : ActorSystem, props : Props) : ActorRef = {
    val actorName = bundleContext.getBundle().getSymbolicName()
    log.debug(s"About to create bundle actor for bundle: ${actorName}")
    val actorRef = system.actorOf(props, actorName)

    capsuleContext.addCapsule(new Capsule {
      override def start() {
      }
      override def stop() {
        log.debug(s"About to stop bundle actor for bundle: ${actorName}")
        system.stop(actorRef)
      }
    })

    actorRef
  }

}
