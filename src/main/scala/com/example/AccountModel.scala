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

object AccountModel {

  /**
   * The current state held by the persistent entity.
   */
  case class Account(balance: Double) {

    def applyCommand(command: AccountCommand[_]): Effect[AccountEvent, Account] =
      command match {
        case c @ Deposit(amount, replyTo) =>
          Effect
            .persist(Deposited(amount))
            .thenReply(c)(_ => Accepted)
        // .thenRun(_ => replyTo ! Accepted)

        case c @ Withdraw(amount, replyTo) if balance - amount < 0 =>
          Effect.reply(c)(Rejected("Insufficient balance!"))

        case Withdraw(amount, replyTo) =>
          Effect
            .persist(Withdrawn(amount))
            .thenRun(_ => replyTo ! Accepted)

        case GetBalance(replyTo) =>
          Effect.none
            .thenRun { acc =>
              replyTo ! Balance(balance)
            }
      }

    def applyEvent(event: AccountEvent): Account = {
      event match {
        case Deposited(amount) => copy(balance = balance + amount)
        case Withdrawn(amount) => copy(balance = balance - amount)
      }
    }

  }

  object Account {

    def empty: Account = Account(balance = 0)

    def behavior(id: String): EventSourcedBehavior[AccountCommand[_], AccountEvent, Account] = {
      EventSourcedBehavior[AccountCommand[_], AccountEvent, Account](
        persistenceId = PersistenceId(id),
        emptyState = Account.empty,
        commandHandler = (account, cmd) => account.applyCommand(cmd),
        eventHandler = (account, evt) => account.applyEvent(evt)
      )
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
