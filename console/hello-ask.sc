:load console/base.sc
import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.Success
import scala.util.Failure
import com.example.HelloAkkaTyped.asking._


import akka.actor.typed.scaladsl.AskPattern._
val greeter: ActorSystem[SayHello] = ActorSystem(behavior("Hello"), "hello-demo")

implicit val timeout = Timeout(3.seconds)
implicit val scheduler = greeter.scheduler
implicit val ec = greeter.executionContext
