import cats.implicits.catsSyntaxPartialOrder
import domain.{Measurement, MeasurementRow, StatisticOperation, StatisticResult}
import zio.Console.{printLine, readLine}
import zio.stream.{ZPipeline, ZStream}
import zio.{Chunk, ZIO}
import io.circe.parser._

import java.nio.file.{Files, Paths}

object HumidityStatistics {
  import Measurement._

  def minSome(stream: ZStream[Any, Throwable, Option[Long]]): ZIO[Any, Throwable, Option[Long]] =
    stream.runFold(Option.empty[Long]) {
      case (None, some)       => some
      case (Some(x), Some(y)) => Some(List(x, y).min)
      case (some, None)       => some
    }

  def maxSome(stream: ZStream[Any, Throwable, Option[Long]]): ZIO[Any, Throwable, Option[Long]] =
    stream.runFold(Option.empty[Long]) {
      case (None, some)       => some
      case (Some(x), Some(y)) => Some(List(x, y).max)
      case (some, None)       => some
    }

  def averageSome(stream: ZStream[Any, Throwable, Option[Long]]): ZIO[Any, Throwable, Option[Long]] =
    stream
      .runFold((Option.empty[Long], 0)) {
        case ((None, count), Some(y))    => (Some(y), count + 1)
        case ((None, count), None)       => (None, count)
        case ((Some(x), count), Some(y)) => (Some(x + y), count + 1)
        case (some, None)                => some
      }
      .map { case (res, count) => res.map(_ / count) }

  /** This function is used to execute the operation one by one */
  def calculateOperation(
    stream: ZStream[Any, Throwable, Measurement],
    opName: String,
    op: ZStream[Any, Throwable, Option[Long]] => ZIO[Any, Throwable, Option[Long]]
  ): ZStream[Any, Throwable, (String, StatisticOperation)]                                          =
    stream.groupBy(in => ZIO.succeed(in.sensorId, in.humidity)) { case (sensorId, stream) =>
      ZStream.fromZIO(op(stream)).map(r => (sensorId, StatisticOperation(opName, r)))
    }

  def joinResults(
    max: Chunk[(String, StatisticOperation)],
    avg: Chunk[(String, StatisticOperation)],
    min: Chunk[(String, StatisticOperation)]
  ): List[(String, StatisticResult)] =
    (max ++ avg ++ min)
      .groupBy(_._1)
      .view
      .mapValues {
        _.foldLeft(StatisticResult(None, None, None)) {
          case (st, (_, StatisticOperation("min", min))) =>
            st.copy(min = min)
          case (st, (_, StatisticOperation("max", max))) =>
            st.copy(max = max)
          case (st, (_, StatisticOperation("avg", avg))) =>
            st.copy(avg = avg)
        }
      }
      .toList
      .sortWith(_._2.avg > _._2.avg)

  /**
   * TODO: I tried to execute the operation using concurrency but it doesn't works.
   *  I'm using calculateOperation instead.
   */
  def calculateStatisticsOperations(
    stream: ZStream[Any, Throwable, Measurement],
    minOp: ZStream[Any, Throwable, Option[Long]] => ZIO[Any, Throwable, Option[Long]],
    maxOp: ZStream[Any, Throwable, Option[Long]] => ZIO[Any, Throwable, Option[Long]],
    avgOp: ZStream[Any, Throwable, Option[Long]] => ZIO[Any, Throwable, Option[Long]]
  ): ZStream[Any, Throwable, (String, (Option[Long], Option[Long], Option[Long]))] =
    stream.groupBy(in => ZIO.succeed(in.sensorId, in.humidity)) { case (s, stream) =>
      ZStream.fromZIO {
        for {
          minFiber <- minOp(stream).fork
          maxFiber <- maxOp(stream).fork
          avgFiber <- avgOp(stream).fork
          min      <- minFiber.join
          max      <- maxFiber.join
          avg      <- avgFiber.join
        } yield (min, avg, max)
      }.map(r => s -> r)
    }

  val program: ZIO[Any, Throwable, Chunk[Measurement]] =
    for {
      _                   <- printLine("Which is the directory of csv files?")
      path                <- readLine
      dir                 <- ZIO.attempt(Paths.get(path)).mapError(e => new IllegalArgumentException(s"$path is not a directory", e))
      _                   <- if (!Files.isDirectory(dir)) ZIO.fail(new IllegalArgumentException(s"$path is not a directory"))
                             else ZIO.succeed(())
      zFiles               = ZStream.fromJavaIterator(Files.newDirectoryStream(dir, "*.csv").iterator())
      nFiles              <- zFiles.runCount
      zMeasurements        =
        zFiles
          .via(
            ZPipeline.mapStream(in =>
              ZStream
                .fromFile(in.toFile)
                .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
                .via(ZPipeline.drop(1))
                .via(ZPipeline.mapZIO(in => ZIO.fromEither(decode[MeasurementRow](convertToJson(in)))))
                .via(
                  ZPipeline
                    .map(in =>
                      Measurement(
                        in.sensorId,
                        if (in.humidity == "NaN") None //NaN is represented by None in our implementation.
                        else Some(in.humidity.toLong)
                      )
                    )
                )
            )
          )
      nMeasurements       <- zMeasurements.runCount
      nFailedMeasurements <- zMeasurements.via(ZPipeline.filter(_.humidity.isEmpty)).runCount
      minMeasurements     <- calculateOperation(zMeasurements, "min", minSome).runCollect
      maxMeasurements     <- calculateOperation(zMeasurements, "max", maxSome).runCollect
      avgMeasurements     <- calculateOperation(zMeasurements, "avg", averageSome).runCollect
      measurements        <- zMeasurements.runCollect
      _                   <- printLine(s"Num of processed files: $nFiles")
      _                   <- printLine(s"Num of processed measurements: $nMeasurements")
      _                   <- printLine(s"Num of failed measurements: $nFailedMeasurements")
      _                   <- printLine("\n")
      _                   <- printLine("Sensors with highest avg humidity:")
      _                    = joinResults(minMeasurements, maxMeasurements, avgMeasurements).foreach(println)
    } yield measurements

}
