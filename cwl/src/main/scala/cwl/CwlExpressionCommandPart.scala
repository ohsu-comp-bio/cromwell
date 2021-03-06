package cwl

import cwl.CommandLineTool.CommandInputParameter
import wom.CommandPart
import wom.callable.RuntimeEnvironment
import wom.expression.IoFunctionSet
import wom.graph.LocalName
import wom.values._


case class CwlExpressionCommandPart(expr: Expression) extends CommandPart {
  override def instantiate(inputsMap: Map[LocalName, WomValue],
                           functions: IoFunctionSet,
                           valueMapper: (WomValue) => WomValue,
                           runtimeEnvironment: RuntimeEnvironment ): String = {

    val stringKeyMap = inputsMap.map{ case (LocalName(localName), value) => localName -> value }

    val pc =
      ParameterContext(
        runtime = runtimeEnvironment.cwlMap
      ).withInputs(stringKeyMap, functions)

    val womValue: WomValue = expr.fold(EvaluateExpression).apply(pc)

    womValue.valueString
  }
}

// TODO: Dan to revisit making this an Either (and perhaps adding some other cases)
case class CommandLineBindingCommandPart(argument: CommandLineBinding) extends CommandPart {
  override def instantiate(inputsMap: Map[LocalName, WomValue],
                           functions: IoFunctionSet,
                           valueMapper: (WomValue) => WomValue,
                           runtimeEnvironment: RuntimeEnvironment) = {

    val pc = ParameterContext(runtime = runtimeEnvironment.cwlMap).withInputs(inputsMap.map({
      case (LocalName(localName), WomSingleFile(path)) => localName -> WomString(path)
      case (LocalName(localName), value) => localName -> value
    }), functions)

    val womValue: WomValue = argument match {
      case CommandLineBinding(_, _, _, _, _, Some(StringOrExpression.Expression(expression)), Some(false)) =>
        expression.fold(EvaluateExpression).apply(pc)
      case CommandLineBinding(_, _, _, _, _, Some(StringOrExpression.String(string)), Some(false)) =>
        WomString(string)
      // There's a fair few other cases to add, but until then...
      case other => throw new NotImplementedError(s"As-yet-unsupported command line binding: $other")
    }

    womValue.valueString
  }
}

case class InputParameterCommandPart(commandInputParameter: CommandInputParameter) extends CommandPart {

  override def instantiate(inputsMap: Map[LocalName, WomValue],
                           functions: IoFunctionSet,
                           valueMapper: (WomValue) => WomValue,
                           runtimeEnvironment: RuntimeEnvironment) = {

    val womValue: WomValue = commandInputParameter match {
      case cip: CommandInputParameter =>
        val localizedId = LocalName(FullyQualifiedName(cip.id).id)
        inputsMap.get(localizedId) match {
          case Some(x) =>x
          case _ => throw new RuntimeException(s"could not find ${localizedId} in map $inputsMap")
        }

      // There's a fair few other cases to add, but until then...
      case other => throw new NotImplementedError(s"As-yet-unsupported commandPart from  command input parameters: $other")
    }

    womValue.valueString
  }
}
