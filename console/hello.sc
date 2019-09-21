:load console/base.sc

import com.example.HelloAkkaTyped.logging._

implicit val greeter: ActorSystem[Message] = ActorSystem(behavior("Hello"), "hello-demo")
