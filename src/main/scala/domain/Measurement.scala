package domain

import io.circe.Decoder
import io.circe.generic.semiauto._

case class MeasurementRow(sensorId: String, humidity: String)
case class Measurement(sensorId: String, humidity: Option[Long])

object Measurement {
  implicit val decoder: Decoder[MeasurementRow] = deriveDecoder

  def convertToJson(inputString: String): String = {
    val values = inputString.split(",")
    s"""{"sensorId": "${values(0)}", "humidity": "${values(1)}"}"""
  }
}
