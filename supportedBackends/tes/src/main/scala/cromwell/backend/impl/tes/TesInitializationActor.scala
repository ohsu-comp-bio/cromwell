package cromwell.backend.impl.tes

import akka.actor.{ActorRef, Props}
import cromwell.backend.impl.tes.TesRuntimeAttributes._
import cromwell.backend.validation.RuntimeAttributesDefault
import cromwell.backend.validation.RuntimeAttributesKeys._
import cromwell.backend.{BackendConfigurationDescriptor, BackendInitializationData, BackendWorkflowDescriptor, BackendWorkflowInitializationActor}
import cromwell.core.WorkflowOptions
import wdl4s.TaskCall
import wdl4s.types.{WdlBooleanType, WdlIntegerType, WdlPrimitiveType, WdlStringType}
import wdl4s.values.WdlValue

import scala.concurrent.Future
import scala.util.Try

object TesInitializationActor {

  val SupportedKeys = Set(
    DockerKey,
    DockerWorkingDirKey,
    FailOnStderrKey,
    ContinueOnReturnCodeKey,
    CpuKey,
    MemoryKey,
    DiskKey
  )

  def props(workflowDescriptor: BackendWorkflowDescriptor,
            calls: Set[TaskCall],
            configurationDescriptor: BackendConfigurationDescriptor,
            serviceRegistryActor: ActorRef): Props =
    Props(new TesInitializationActor(workflowDescriptor, calls, configurationDescriptor, serviceRegistryActor))
}

class TesInitializationActor(override val workflowDescriptor: BackendWorkflowDescriptor,
                             override val calls: Set[TaskCall],
                             override val configurationDescriptor: BackendConfigurationDescriptor,
                             override val serviceRegistryActor: ActorRef)
  extends BackendWorkflowInitializationActor {
  import TesInitializationActor._

  private def optional(wdlType: WdlPrimitiveType) = {
    wdlTypePredicate(valueRequired = false, wdlType.isCoerceableFrom) _
  }

  override protected def runtimeAttributeValidators: Map[String, (Option[WdlValue]) => Boolean] = Map(
    DockerKey               -> optional(WdlStringType),
    DockerWorkingDirKey     -> optional(WdlStringType),
    FailOnStderrKey         -> optional(WdlBooleanType),
    ContinueOnReturnCodeKey -> continueOnReturnCodePredicate(valueRequired = false),
    CpuKey                  -> optional(WdlIntegerType),
    MemoryKey               -> optional(WdlStringType),
    DiskKey                 -> optional(WdlStringType)
  )

  /**
    * A call which happens before anything else runs
    */
  override def beforeAll(): Future[Option[BackendInitializationData]] = Future.successful(None)

  /**
    * Validate that this WorkflowBackendActor can run all of the calls that it's been assigned
    */
  override def validate(): Future[Unit] = Future {
    calls foreach { call =>
      val runtimeAttributes = call.task.runtimeAttributes.attrs
      val notSupportedAttributes = runtimeAttributes filterKeys { !SupportedKeys.contains(_) }

      if (notSupportedAttributes.nonEmpty) {
        val notSupportedAttrString = notSupportedAttributes.keys mkString ", "
        log.warning(s"Key/s [$notSupportedAttrString] is/are not supported by the TES backend. Unsupported attributes will not be part of jobs executions.")
      }
    }

  }

  override protected def coerceDefaultRuntimeAttributes(options: WorkflowOptions): Try[Map[String, WdlValue]] = {
    RuntimeAttributesDefault.workflowOptionsDefault(options, TesRuntimeAttributes.coercionMap)
  }
}
