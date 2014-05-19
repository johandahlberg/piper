package molmed.apps.setupcreator

object ConsoleInputParser {
  implicit def packOptionalValue[T](value: T): Option[T] = Some(value)

  private def checkInput[T](function: Option[String] => T)(key: Option[String], value: T, checkInputQuestion: Option[String]): T = {
    val valid = readLine(checkInputQuestion.get + "\n")
    valid match {
      case "y" => value
      case "n" => function(key)
      case _ => {
        println("Did not recognize input: " + valid)
        checkInput(function)(key, value, checkInputQuestion)
      }
    }
  }

  def getMultipleInputs(key: Option[String]): List[String] = {

    def continue(accumulator: List[String]): List[String] = {

      val value = readLine("Set " + key.get + ":" + "\n")
      checkInput[List[String]](getMultipleInputs)(key, List(value), "Value of key: " + key.get + ", was set to: " + value + ". Do you want to keep it? [y/n]")

      val cont = readLine("Do you want to add another " + key.get + "? [y/n]" + "\n")
      cont match {
        case "n" => value :: accumulator
        case "y" => continue(value :: accumulator)
        case _ => {
          println("Did not recognize input: " + cont)
          continue(accumulator)
        }
      }
    }

    continue(List())
  }

  def withDefaultValue[T](key: Option[String], defaultValue: T)(function: Option[String] => T): T = {
    if (defaultValue.isDefined)
      checkInput(function)(key, defaultValue.get, "The default value of " + key.get + " is " + defaultValue.get + ". Do you want to keep it? [y/n]")
    else
      function(key)
  }

  def getSingleInput(key: Option[String]): String = {
    val value = readLine("Set " + key.get + ":" + "\n")
    checkInput[String](getSingleInput)(key, value, "Value of key: " + key.get + ", was set to: " + value + ". Do you want to keep it? [y/n]")
  }
}