autoscale:true
theme: Lightbend
slide-dividers: #
slide-transition:  true

# Akka Persistence Typed

Renato Cavalcanti

Lightbend
[@renatocaval](http://twitter.com/renatocaval)

[.header:#ff931e, alignment(center), text-scale(2.3), Fira Sans]
[.text:#FFFFFF, alignment(center), text-scale(1.7)]
[.background-color: #132931]

# History

* Started as an Akka Extension called Eventsourced by Martin Krasser
* Brought into Akka as Akka Persistence (untyped) in 2.4.0
* Akka Persistence Query in 2.5.0
* Akka Persistence Typed (upcoming 2.6.0)

^ Eventsourced initial commit dates from Jul 2012

^ It's important to keep in mind that Akka Persistence is an Event Sourcing library. It's not about CQRS, but of course it gives you the means to build a CQRS application with it.


# Akka Typed - protocol

When defining an Actor, we start by defining it's protocol

<!-- #hello-protocol -->
```scala 
sealed trait Message
final case class SayHello(name: String) extends Message
final case class ChangeGreeting(greet: String) extends Message
```
<!-- #hello-protocol -->

^ The protocol is the set of messages that an Actor can receive  


# Akka Typed - behavior

In Akka Typed, an Actor is just a Behavior function

<!-- #hello-behaviour -->
```scala
def behavior(greeting: String): Behavior[Message] =
  Behaviors.receiveMessage {
    case SayHello(name) =>
      println(s"$greeting $name!")
      Behaviors.same
    case ChangeGreeting(greet) =>  
      behavior(greet)
  }
```
<!-- #hello-behaviour -->

^ In Typed, you don't extend an Actor class, but you define a Behaviour function instead.  
^ Differently than in untyped, that's NOT a partial function.
^ After each message, you define what is the next behavior.  
You can stay on the same behavior or you can change it.  


# Akka Typed - ActorContext

<!-- #hello-behaviour-ctx -->
```scala
def behaviorCtx(greeting: String): Behavior[Message] =
  Behaviors.setup { ctx: ActorContext[Message] =>
    Behaviors.receiveMessage {
      case SayHello(name) =>
        ctx.log.info(s"$greeting $name!")
        Behaviors.same
      case ChangeGreeting(greet) =>
        behavior(greet)
    }
  }
```
<!-- #hello-behaviour-ctx -->

^ Because its a function we don't have access to Actor Context. There is no logging, no sender(), etc.
^ In order to access some actor facilities, we need to bring an ActorContext into scope.

# Akka Typed - ask pattern

There is no 'sender'. If you need to respond to an 'ask', your incoming message must have an *ActorRef[R]* that you can use to respond.

<!-- #hello-behaviour-ask -->
```scala
final case class Hello(msg: String)
final case class SayHello(name: String, replyTo: ActorRef[Hello])

def behavior(greeting: String): Behavior[SayHello] =
  Behaviors.receiveMessage {
    case SayHello(name, replyTo) =>
      replyTo ! Hello(s"$greeting $name!")
      Behaviors.same
  }
```
<!-- #hello-behavior-ask -->

# Akka Typed

```scala
final case class Hello(msg: String)
final case class SayHello(name: String, replyTo: ActorRef[Hello])

def behavior(greeting: String): Behavior[SayHello] =
  Behaviors.receiveMessage {
    case SayHello(name, replyTo) =>
      replyTo ! Hello(s"$greeting $name!")
      Behaviors.same
  }

val greeter: ActorSystem[SayHello] = ActorSystem(behavior("Hello"), "HelloAkka")
val res: Future[Hello] =  
  greeter.ask(replyTo => SayHello("Akka Typed", replyTo))
```

# Akka Persistence Typed - Highlights

* Protocol is defined in terms of *Command*, *Event* and *State*
* *EventSourcedBehavior* instead of *Behavior*
* Tagging function  
* Better controlled snapshotting (number of events and/or predicate)
* Enforced Replies  

##  

* Old plugins are still compatible
* Akka Persistence Query untouched, already typed and based on Akka Streams

^ EventSourcedBehavior takes cares of all the bits necessary. User concentrates on modelling their domain.
^ Snapshotting is controlled by Akka itself. User defines when it should take place, Akka make sure its done in the right order.
^ Tagging of events is needed for querying by tag and generate views or publish to, for instance, a Kafka topic. In classical Akka this was done by adding an EventAdapter via config.  Now it's more explicit when defining your model. It's optional.
^ EnforcedReplies is a common pattern for CQRS, you send a command and you want confirmation that the command was accepted

[.build-lists: true]
[.header:#ff931e, alignment(left), text-scale(1.6), Fira Sans]

# Commands, Events and State

```scala
sealed trait AccountCommand
final case class Deposit(amount: Double) extends AccountCommand
final case class Withdraw(amount: Double) extends AccountCommand
case class GetBalance(replyTo: ActorRef[Balance]) extends AccountCommand

sealed trait AccountEvent
final case class Deposited(amount: Double) extends AccountEvent
final case class Withdrawn(amount: Double) extends AccountEvent

case class Account(balance: Double)
```

[.header:#ff931e, alignment(left), text-scale(1.8), Fira Sans]

# Command Handler  

```scala
// (State, Command) =>  Effect
case class Account(balance: Double) {
  def applyCommand(cmd: AccountCommand): Effect[AccountEvent, Account] =
    cmd match {
      case Deposit(amount) =>
        Effect.persist(Deposited(amount))
  
      // other cases intentionally omitted  
    }
}
```

# Event Handler  

```scala
// (State, Event) =>  State
case class Account(balance: Double) {
  def applyEvent(evt: AccountEvent): Account = {
      evt match {
        case Deposited(amount) => copy(balance = balance + amount)
        case Withdrawn(amount) => copy(balance = balance - amount)
      }
    }
}
```

# EventSourcedBehavior

```scala
def behavior(id: String): EventSourcedBehavior[AccountCommand, AccountEvent, Account] = {
  
  EventSourcedBehavior[AccountCommand, AccountEvent, Account](
    persistenceId = PersistenceId(id),
    emptyState = Account(balance = 0),
    // command handler: (State, Command) => Effect
    commandHandler = (account, cmd) => account.applyCommand(cmd),
    // event handler: (State, Event) => State
    eventHandler = (account, evt) => account.applyEvent(evt)
  )

}
```

# Tagging

```scala
def behavior(id: String): EventSourcedBehavior[AccountCommand, AccountEvent, Account] = {
  
  EventSourcedBehavior[AccountCommand, AccountEvent, Account](
    persistenceId = PersistenceId(id),
    emptyState = Account(balance = 0),
    commandHandler = (account, cmd) => account.applyCommand(cmd),
    eventHandler = (account, evt) => account.applyEvent(evt)
  )
  .withTagger {
    // tagging events are useful for querying by tag
    case evt: Deposited => Set("account", "deposited")
    case evt: Withdrawn => Set("account", "withdrawn")
  }
  
}
```

# Snapshots

```scala
def behavior(id: String): EventSourcedBehavior[AccountCommand, AccountEvent, Account] = {
  
  EventSourcedBehavior[AccountCommand, AccountEvent, Account](
    persistenceId = PersistenceId(id),
    emptyState = Account(balance = 0),
    commandHandler = (account, cmd) => account.applyCommand(cmd),
    eventHandler = (account, evt) => account.applyEvent(evt)
  )
  // save a snapshot on every 100 events and keep max 2
  .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
  
  // save a snapshot when a predicate holds
  .snapshotWhen {
    case (account, evt: Withdrawn, seqNr) => true
    case _                                => false
  }
  
}
```

# Some live coding

[.header:#FFFFFF, alignment(center), text-scale(1.8), Fira Sans]
[.background-color: #13719F]
[.footer: ]

^ Now is time to show some code. Do demo and navigate over the features

# Cluster Sharding and Persistence

* Manage state over different JVMs
* Knows where is your instance
* Entity Passivation  
* Honours single writer principle for Persistence
* Rolling updates without downtime
* Commands must be serializable

[.build-lists: true]

^ The most common usage of ClusterSharding is to manage state for Akka Persistence over more than one JVM.  

[.header:#ff931e, alignment(left), text-scale(1.8), Fira Sans]

# Cluster Sharding - EntityContext

```scala

object Account {

  val typeKey = EntityTypeKey[AccountCommand]("Account")
  
  def behavior(entityContext: EntityContext):  
    EventSourcedBehavior[AccountCommand, AccountEvent, Account] = {
  
      EventSourcedBehavior[AccountCommand, AccountEvent, Account](
        persistenceId = PersistenceId(entityContext.entityId),
        emptyState = Account(balance = 0),
        commandHandler = (account, cmd) => account.applyCommand(cmd),
        eventHandler = (account, evt) => account.applyEvent(evt)
      )
}  
```  

[.header:#ff931e, alignment(left), text-scale(1.8), Fira Sans]

# Cluster Sharding - Entity

```scala

clusterSharding.init(
    Entity(
      Account.typeKey,
      ctx: EntityContext => Account.behavior(ctx)
    )
  )
``` 

[.header:#ff931e, alignment(left), text-scale(1.8), Fira Sans]

# Cluster Sharding - EntityRef

```scala
val account: EntityRef[AccountCommand] =
  clusterSharding.entityRefFor(
    typeKey = Account.typeKey,  
    entityId = "BE50 7314 3515 2919"
  )
```

[.header:#ff931e, alignment(left), text-scale(1.8), Fira Sans]

# Takeaways

* Declarative API
* Developer can concentrate on modelling
* Types everywhere
* And functions9
* `Any => Unit` is part of the past

##  
* Event Sourcing opens the door for decoupling your services
* High throughput with append only journals  
* Scalability with Cluster Sharding
* Rolling updates when clustered  
* Distributed event consuming (included in Lagom, will be extracted)

[.build-lists: true]

# Thanks for listening

In GitHub:  
[renatocaval/akka-persistence-typed-talk](https://github.com/renatocaval/akka-persistence-typed-talk)

Twitter: [@renatocaval](http://twitter.com/renatocaval)

[.header:#ff931e, alignment(center), text-scale(2.3), Fira Sans]
[.text:#FFFFFF, alignment(center), text-scale(1.7)]
[.background-color: #132931]