package com.example

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.journal.Tagged
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import akka.cluster.typed.Cluster
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.Join
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.ClusterShardingSettings
import scala.concurrent.duration._

object AccountModelSharded {

  /**
   * The current state held by the persistent entity.
   */
  case class Account(balance: Double) {

    def applyCommand(command: AccountCommand): ReplyEffect[AccountEvent, Account] =
      command match {
        case Deposit(amount, replyTo) =>
          Effect
            .persist(Deposited(amount))
            .thenReply(replyTo) { _ =>
              Accepted
            }

        case Withdraw(amount, replyTo) if balance - amount < 0 =>
          Effect.reply(replyTo)(Rejected("Insuficient balance!"))

        case Withdraw(amount, replyTo) =>
          Effect
            .persist(Withdrawn(amount))
            .thenReply(replyTo) { _ =>
              Accepted
            }

        case GetBalance(replyTo) =>
          Effect.reply(replyTo)(Balance(balance))
      }

    def applyEvent(evt: AccountEvent): Account = {
      evt match {
        case Deposited(amount) => copy(balance = balance + amount)
        case Withdrawn(amount) => copy(balance = balance - amount)
      }
    }

  }

  object Account {

    def empty: Account = Account(balance = 0)

    val typeKey = EntityTypeKey[AccountCommand]("Account")

    def behavior(entityContext: EntityContext[AccountCommand]): Behavior[AccountCommand] = {
      Behaviors.setup { ctx =>
        ctx.log.info(s"Instantiating account: ${entityContext.entityId}")
        EventSourcedBehavior[AccountCommand, AccountEvent, Account](
          persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
          emptyState = Account.empty,
          commandHandler = (account, cmd) => {
            ctx.log.info(s"Received command $cmd for account ${entityContext.entityId}")
            account.applyCommand(cmd)
          },
          eventHandler = (account, evt) => account.applyEvent(evt)
        )
      }
    }
  }

  sealed trait AccountReply
  case class Balance(amount: Double) extends AccountReply

  sealed trait Confirmation extends AccountReply
  sealed trait Accepted extends Confirmation
  case object Accepted extends Accepted
  case class Rejected(reason: String) extends Confirmation

  sealed trait AccountCommand
  final case class Deposit(amount: Double, replyTo: ActorRef[Accepted]) extends AccountCommand
  final case class Withdraw(amount: Double, replyTo: ActorRef[Confirmation]) extends AccountCommand
  case class GetBalance(replyTo: ActorRef[Balance]) extends AccountCommand

  sealed trait AccountEvent
  final case class Deposited(amount: Double) extends AccountEvent
  final case class Withdrawn(amount: Double) extends AccountEvent

}

object ShardedApp {

  import com.example.AccountModelSharded._
  import akka.actor.typed.scaladsl.adapter._

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.parseString("akka.actor.provider = cluster")
    val appConfig = ConfigFactory.load().withFallback(ConfigFactory.load())

    val typedActorSystem = akka.actor.ActorSystem("clustered", appConfig).toTyped
    val clusterSharding = ClusterSharding(typedActorSystem)

    println(typedActorSystem.settings.config.getString("akka.actor.provider"))
    val cluster = Cluster(typedActorSystem)
    cluster.manager ! Join.create(cluster.selfMember.address)

    clusterSharding.init(
      Entity(Account.typeKey) { ctx => Account.behavior(ctx) }
      .withSettings(ClusterShardingSettings(typedActorSystem).withPassivateIdleEntityAfter(5.seconds))
    )
    val account: EntityRef[AccountCommand] = clusterSharding.entityRefFor(Account.typeKey, "abc")
  }
}
