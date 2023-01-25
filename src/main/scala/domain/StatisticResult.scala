package domain

case class StatisticOperation(
  operation: String,
  result: Option[Long]
)

case class StatisticResult(
  min: Option[Long],
  avg: Option[Long],
  max: Option[Long]
)
