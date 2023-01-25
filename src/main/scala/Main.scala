import zio.ZIOAppDefault

object Main extends ZIOAppDefault {
  import HumidityStatistics._
  override def run = program
}
