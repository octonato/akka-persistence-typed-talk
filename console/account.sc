:load console/base.sc

import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.Success
import scala.util.Failure
import akka.actor.typed.scaladsl.AskPattern._
import com.example.AccountModel._

val account: ActorSystem[AccountCommand] =
  ActorSystem(
    Account.behavior("abc"),
    "persistence-demo"
  )

implicit val timeout = Timeout(3.seconds)
implicit val scheduler = account.scheduler
implicit val ec = account.executionContext
