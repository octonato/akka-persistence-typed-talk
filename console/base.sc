import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior

import scala.concurrent.Await
import scala.concurrent.duration._

def stop(implicit system: ActorSystem[_]): Unit = {
  system.terminate()
  Await.ready(system.whenTerminated, 5.seconds)
  print("system terminated")
}
