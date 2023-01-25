import domain.{StatisticOperation, StatisticResult}
import zio.{Chunk, Scope}
import zio.stream._
import zio.test._
import zio.test.ZIOSpecDefault
import zio.test.Assertion._

object HumidityStatisticsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("HumidityStatistics")(
    test("minSome with different values") {
      assertZIO(
        HumidityStatistics.minSome(ZStream.fromIterable(List(Some(10), None, Some(20), None, Some(1))))
      )(equalTo(Option.apply(1L)))

    },
    test("minSome with only nones") {
      assertZIO(
        HumidityStatistics.minSome(ZStream.fromIterable(List(None, None, None, None, None)))
      )(equalTo(Option.empty[Long]))
    },
    test("maxSome with different values") {
      assertZIO(
        HumidityStatistics.maxSome(ZStream.fromIterable(List(Some(10), None, Some(20), None, Some(1))))
      )(equalTo(Option.apply(20L)))
    },
    test("maxSome with only nones") {
      assertZIO(
        HumidityStatistics.maxSome(ZStream.fromIterable(List(None, None, None, None, None)))
      )(equalTo(Option.empty[Long]))
    },
    test("averageSome with different values") {
      assertZIO(
        HumidityStatistics.averageSome(ZStream.fromIterable(List(Some(30), None, Some(20), None, Some(40))))
      )(equalTo(Option.apply(30L)))
    },
    test("averageSome with only nones") {
      assertZIO(
        HumidityStatistics.averageSome(ZStream.fromIterable(List(None, None, None, None, None)))
      )(equalTo(Option.empty[Long]))
    },
    test("joinResults with three operations like min, avg, max") {
      val maxL = Chunk.fromIterable(
        List(
          ("s1", StatisticOperation("max", Some(100L))),
          ("s2", StatisticOperation("max", Some(80L))),
          ("s3", StatisticOperation("max", Some(50L)))
        )
      )
      val minL = Chunk.fromIterable(
        List(
          ("s1", StatisticOperation("min", Some(10L))),
          ("s2", StatisticOperation("min", Some(2L))),
          ("s3", StatisticOperation("min", Some(5L)))
        )
      )
      val avgL =
        Chunk.fromIterable(
          List(
            ("s1", StatisticOperation("avg", Some(30L))),
            ("s2", StatisticOperation("avg", Some(20L))),
            ("s3", StatisticOperation("avg", Some(15L)))
          )
        )

      val expectedResult: List[(String, StatisticResult)] =
        List(
          ("s1", StatisticResult(Some(10L), Some(30L), Some(100L))),
          ("s2", StatisticResult(Some(2L), Some(20L), Some(80L))),
          ("s3", StatisticResult(Some(5L), Some(15L), Some(50L)))
        )

      assert(HumidityStatistics.joinResults(maxL, avgL, minL))(equalTo(expectedResult))
    }
  )

}
