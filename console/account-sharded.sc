:load console/base.sc

import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.Success
import scala.util.Failure
import akka.actor.typed.scaladsl.AskPattern._
import com.example.AccountModelSharded._
import com.typesafe.config.ConfigFactory
import akka.cluster.typed.Cluster
import akka.cluster.typed.Join
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl._
import akka.cluster.sharding.typed.ClusterShardingSettings
import scala.concurrent.duration._


val config = ConfigFactory.parseString(
  s"""
  | akka {
  |   actor.provider = cluster
  |   cluster {
  |     seed-nodes = [
  |        "akka://account-application@127.0.0.1:25520"
  |        "akka://account-application@127.0.0.1:25530"
  |     ]
  |   }
  |   remote.artery {
  |     canonical.port = $port
  |     canonical.hostname = "127.0.0.1"
  |   }
  | }
  """.stripMargin
  )
val appConfig = config.withFallback(ConfigFactory.load())

val typedActorSystem = akka.actor.ActorSystem("account-application", appConfig).toTyped
val cluster = Cluster(typedActorSystem)
val clusterSharding = ClusterSharding(typedActorSystem)

clusterSharding.init(
    Entity(Account.typeKey) { ctx => Account.behavior(ctx) }
  )
  

implicit val timeout = Timeout(3.seconds)
implicit val scheduler = typedActorSystem.scheduler
implicit val ec = typedActorSystem.executionContext 

def entityRefFor(id: String): EntityRef[AccountCommand[_]] = 
  clusterSharding.entityRefFor(Account.typeKey, id)
