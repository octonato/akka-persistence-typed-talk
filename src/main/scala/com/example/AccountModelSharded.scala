package com.example

import akka.persistence.typed.ExpectingReply
import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.journal.Tagged
import akka.persistence.typed.ExpectingReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import akka.persistence.typed.ExpectingReply
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

    def applyCommand(command: AccountCommand[_]): ReplyEffect[AccountEvent, Account] =
      command match {
        case cmd @ Deposit(amount, _) =>
          Effect
            .persist(Deposited(amount))
            .thenReply(cmd) { _ =>
              Accepted
            }

        case cmd @ Withdraw(amount, _) if balance - amount < 0 =>
          Effect.reply(cmd)(Rejected("Insuficient balance!"))

        case cmd @ Withdraw(amount, _) =>
          Effect
            .persist(Withdrawn(amount))
            .thenReply(cmd) { _ =>
              Accepted
            }

        case cmd @ GetBalance(replyTo) =>
          Effect.reply(cmd)(Balance(balance))
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

    val typeKey = EntityTypeKey[AccountCommand[_]]("Account")

    def behavior(entityContext: EntityContext): Behavior[AccountCommand[_]] = {
      Behaviors.setup { ctx =>
        ctx.log.info(s"Instantiating account: ${entityContext.entityId}")
        EventSourcedBehavior[AccountCommand[_], AccountEvent, Account](
          persistenceId = typeKey.persistenceIdFrom(entityContext.entityId),
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

  sealed trait AccountCommand[R <: AccountReply] extends ExpectingReply[R]
  final case class Deposit(amount: Double, replyTo: ActorRef[Accepted]) extends AccountCommand[Accepted]
  final case class Withdraw(amount: Double, replyTo: ActorRef[Confirmation]) extends AccountCommand[Confirmation]
  case class GetBalance(replyTo: ActorRef[Balance]) extends AccountCommand[Balance]

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
      Entity(
        Account.typeKey,
        ctx => Account.behavior(ctx)
      ).withSettings(ClusterShardingSettings(typedActorSystem).withPassivateIdleEntitiesAfter(5.seconds))
    )
    val account: EntityRef[AccountCommand[_]] = clusterSharding.entityRefFor(Account.typeKey, "abc")
  }
}
