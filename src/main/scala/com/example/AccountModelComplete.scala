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

object AccountWithReplies {

  /**
   * The current state held by the persistent entity.
   */
  case class Account(balance: Double) {

    def applyCommand(cmd: AccountCommand): ReplyEffect[AccountEvent, Account] =
      cmd match {
        case  Deposit(amount, replyTo) =>
          Effect
            .persist(Deposited(amount))
            .thenReply(replyTo) { _ =>
              Accepted
            }

        case Withdraw(amount, replyTo) if balance - amount < 0 =>
          Effect.reply(replyTo)(Rejected("Insufficient balance!"))

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

    def behavior(id: String): EventSourcedBehavior[AccountCommand, AccountEvent, Account] = {
      EventSourcedBehavior
        .withEnforcedReplies[AccountCommand, AccountEvent, Account](
          persistenceId = PersistenceId("Account", id),
          emptyState = Account.empty,
          commandHandler = (account, cmd) => account.applyCommand(cmd),
          eventHandler = (account, evt) => account.applyEvent(evt)
        )
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 5, keepNSnapshots = 2))
        .snapshotWhen {
          case (_, evt: Withdrawn, _) => true
          case _                      => false
        }
        .withTagger {
          case evt: Deposited => Set("account", "deposited")
          case evt: Withdrawn => Set("account", "withdrawn")
        }
    }
  }

  sealed trait AccountEvent
  final case class Deposited(amount: Double) extends AccountEvent
  final case class Withdrawn(amount: Double) extends AccountEvent

  sealed trait AccountCommand

  sealed trait AccountReply
  case class Balance(amount: Double) extends AccountReply

  sealed trait Confirmation extends AccountReply
  sealed trait Accepted extends Confirmation
  case object Accepted extends Accepted
  case class Rejected(reason: String) extends Confirmation

  final case class Deposit(amount: Double, replyTo: ActorRef[Accepted]) extends AccountCommand
  final case class Withdraw(amount: Double, replyTo: ActorRef[Confirmation]) extends AccountCommand
  case class GetBalance(replyTo: ActorRef[Balance]) extends AccountCommand

}
