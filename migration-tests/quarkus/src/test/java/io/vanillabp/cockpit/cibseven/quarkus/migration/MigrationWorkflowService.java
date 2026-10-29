package io.vanillabp.cockpit.cibseven.quarkus.migration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The one workflow of the test. The same class serves it on Camunda 7 and on CIB seven: a
 * workflow service knows nothing about the engine it runs on.
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = MigrationAggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = MigrationWorkflowService.BPMN_PROCESS_ID))
public class MigrationWorkflowService {

  /** The BPMN process of the test. */
  public static final String BPMN_PROCESS_ID = "MigrationProcess";

  /** The form key of its user task. */
  public static final String TASK_DEFINITION = "approve";

  /** The BPMN element id of its user task. */
  public static final String BPMN_TASK_ID = "Approve";

  /** The id of the waiting user task, per aggregate id. */
  public static final Map<String, String> WAITING_USER_TASKS = new ConcurrentHashMap<>();

  @Inject
  ProcessService<MigrationAggregate> processService;

  /**
   * @return The process service, so that the test can start a workflow and end a task
   */
  public ProcessService<MigrationAggregate> processes() {

    return processService;

  }

  @WorkflowTask(taskDefinition = TASK_DEFINITION)
  public void waitForApproval(
      final MigrationAggregate aggregate,
      @TaskId final String taskId) {

    WAITING_USER_TASKS.put(aggregate.getId(), taskId);

  }

}
