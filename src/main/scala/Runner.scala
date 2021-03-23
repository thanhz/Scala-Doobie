import doobie._
import doobie.implicits._
import cats._
import cats.effect._
import cats.implicits._

object Runner extends App {

  import doobie.util.ExecutionContexts

  // We need a ContextShift[IO] before we can construct a Transactor[IO]. The passed ExecutionContext
  // is where nonblocking operations will be executed. For testing here we're using a synchronous EC.
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContexts.synchronous)

  // A transactor that gets connections from java.sql.DriverManager and executes blocking operations
  // on an our synchronous EC. See the chapter on connection handling for more info.
  val xa = Transactor.fromDriverManager[IO](
    "com.mysql.cj.jdbc.Driver", // driver classname
    "jdbc:mysql://localhost/test", // connect URL (driver-specific)
    "root", // user
    "password", // password
    Blocker.liftExecutionContext(ExecutionContexts.synchronous) // just for testing
  )

  case class person(firstname: String, lastname: String, Mobile: String)

  val program1 = 42.pure[ConnectionIO]
  val program2 = sql"select 42".query[Int].unique
  val program3: ConnectionIO[(person, Double)] =
    for {
      a <- sql"select firstname,lastname,mobile from table1 where firstname='bob'".query[person].unique
      b <- sql"select rand()".query[Double].unique
    } yield (a, b)

  val program4 =
    sql"select * from table1"
      .query[person] // Query0[String]
      .to[List] // ConnectionIO[List[String]]
      .transact(xa) // IO[List[String]]
      .unsafeRunSync() // List[String]
      .take(5) // List[String]
      .foreach(println) // Unit

  val program5: Unit =
    sql"select * from table1"
      .query[String] // Query0[String]
      .stream // Stream[ConnectionIO, String]
      .take(5) // Stream[ConnectionIO, String]
      .compile.toList // ConnectionIO[List[String]]
      .transact(xa) // IO[List[String]]
      .unsafeRunSync() // List[String]
      .foreach(println) // Unit

  val io = program3.transact(xa)
  println(io.unsafeRunSync)

}
