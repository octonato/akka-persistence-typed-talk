package com.example

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

object HelloAkkaTyped {

  object logging {
    // #hello-protocol
    sealed trait Message
    final case class SayHello(name: String) extends Message
    final case class ChangeGreeting(greet: String) extends Message
    // #hello-protocol

    // #hello-behaviour
    def behavior(greeting: String): Behavior[Message] =
      Behaviors.receiveMessage {
        case SayHello(name) =>
          println(s"$greeting $name!")
          Behaviors.same
        case ChangeGreeting(greet) => behavior(greet)
      }
    // #hello-behavior

    // #hello-behaviour-ctx
    def behaviorCtx(greeting: String): Behavior[Message] =
      Behaviors.setup { ctx: ActorContext[Message] =>
        Behaviors.receiveMessage {
          case SayHello(name) =>
            ctx.log.info("{} {}!", greeting, name)
            Behaviors.same
          case ChangeGreeting(greet) =>
            behavior(greet)
        }
      }
    // #hello-behaviour-ctx
  }

  object asking {

    // #hello-behaviour-ask
    final case class Hello(msg: String)
    final case class SayHello(name: String)(val replyTo: ActorRef[Hello])

    def behavior(greeting: String): Behavior[SayHello] =
      Behaviors.receiveMessage {
        case m @ SayHello(name) =>
          m.replyTo ! Hello(s"$greeting $name!")
          Behaviors.same
      }
    // #hello-behavior-ask

    import akka.util.Timeout
    import scala.concurrent.duration._
    import scala.util.Success
    import scala.util.Failure
    import com.example.HelloAkkaTyped.asking._

    import akka.actor.typed.scaladsl.adapter._
    import akka.actor.typed.scaladsl.AskPattern._

    val system: ActorSystem = ActorSystem("hello-demo")
    val typedSystem = system.toTyped

    implicit val timeout = Timeout(3.seconds)
    implicit val scheduler = typedSystem.scheduler
    implicit val ec = typedSystem.executionContext

    val greeter: ActorRef[SayHello] = system.spawn(behavior("Hello"), "hello-akka")
    val res: Future[Hello] = greeter.ask(SayHello("Akka Typed"))
  }

}
