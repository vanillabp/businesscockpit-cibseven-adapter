package io.vanillabp.cockpit.cibseven.quarkus.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vanillabp.camunda7.quarkus.runtime.Camunda7QuarkusEngineRegistry;
import io.vanillabp.cibseven.quarkus.runtime.CibSevenQuarkusEngineRegistry;
import io.vanillabp.cockpit.extension.spi.BusinessCockpitBpmsBridge;
import io.vanillabp.cockpit.extension.spi.UserTaskReference;
import io.vanillabp.cockpit.extension.test.support.CockpitServer;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

/**
 * The Business Cockpit on Quarkus while an application moves from Camunda 7 to CIB seven: the
 * Camunda 7 adapter and the CIB seven adapter in one application, each with its engine and its
 * datasource, and both Business Cockpit extensions next to them. See decisions 4 and 5 in the
 * repository's DECISIONS.md.
 * <p>
 * CIB seven is listed first, so a new workflow starts there. A workflow which ran on Camunda 7
 * before the migration is started in that engine directly, the way it was started before. The
 * user task of each workflow reaches the cockpit once, through the extension of the engine the
 * task runs on, and the cockpit asks that extension about it.
 * <p>
 * Both datasources of <code>application.yaml</code> are XA datasources. VanillaBP's outbox starts
 * the new workflow on CIB seven in a transaction which also writes the outbox table of the
 * application's datasource. Without XA that transaction refuses the second datasource.
 */
@ExtendWith(SuppressOutputExtension.class)
@SuppressOutputExtension.SuppressBackgroundOutput
public class CibSevenNextToCamunda7Test {

  private static final String MODULE_ID = "migration";

  private static final long TIMEOUT_MS = 30_000;

  @RegisterExtension
  static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
      .withApplicationRoot(jar -> jar
          .addClass(MigrationAggregate.class)
          .addClass(MigrationAggregatePersistence.class)
          .addClass(MigrationWorkflowService.class)
          // the test class is initialized twice: once while the application is built, and
          // again inside the class loader of the running application. Its assertions use the
          // server class, so the copy inside that application needs it
          .addClass(CockpitServer.class)
          .addAsResource("application.yaml")
          .addAsResource("migration/processes/migration-process.bpmn")
          .addAsResource("workflow-module-descriptor/workflow-module", "META-INF/workflow-module"))
      .overrideRuntimeConfigKey("vanillabp.cockpit.rest.base-url", CockpitServer.baseUrl());

  @Inject
  Camunda7QuarkusEngineRegistry camunda7Engines;

  @Inject
  CibSevenQuarkusEngineRegistry cibSevenEngines;

  @Inject
  MigrationWorkflowService workflowService;

  @Inject
  MigrationAggregatePersistence aggregates;

  /** Each Business Cockpit extension produces the list of its own bridges, one per adapter id. */
  @Inject
  Instance<List<BusinessCockpitBpmsBridge>> bridgesPerExtension;

  @Inject
  UserTransaction userTransaction;

  @Test
  @DisplayName("A user task on Camunda 7 and one on CIB seven both reach the cockpit once, through their own BPMS half")
  public void eachEngineReportsThroughItsOwnHalf() throws Exception {

    final var camunda7 = camunda7Engines.engineFor("c7").getProcessEngine();
    final var cibSeven = cibSevenEngines.engineFor("cs").getProcessEngine();
    assertEquals("camunda7", bridgeOf("c7").adapterType());
    assertEquals("cibseven", bridgeOf("cs").adapterType());

    CockpitServer.forgetRequests();

    // a workflow which ran on Camunda 7 before the migration
    final var before = new MigrationAggregate();
    before.setId("running-before");
    inTransaction(() -> aggregates.save(before));
    inTransaction(() -> camunda7
        .getRuntimeService()
        .createProcessInstanceByKey(MigrationWorkflowService.BPMN_PROCESS_ID)
        .processDefinitionTenantId(MODULE_ID)
        .businessKey(before.getId())
        .execute());

    // a new workflow, started the way the application starts it: on CIB seven, listed first
    final var after = new MigrationAggregate();
    after.setId("started-after");
    inTransaction(() -> workflowService.processes().startWorkflow(after));

    awaitValue(() -> MigrationWorkflowService.WAITING_USER_TASKS.get(before.getId()), "the task on Camunda 7");
    awaitValue(() -> MigrationWorkflowService.WAITING_USER_TASKS.get(after.getId()), "the task on CIB seven");
    CockpitServer.awaitRequestOf("/usertask/created", businessIdOf(before.getId()));
    CockpitServer.awaitRequestOf("/usertask/created", businessIdOf(after.getId()));

    final var camunda7Task = camunda7
        .getTaskService()
        .createTaskQuery()
        .processInstanceBusinessKey(before.getId())
        .singleResult();
    final var onCamunda7 = new UserTaskReference(
        "c7", MODULE_ID, MigrationWorkflowService.BPMN_PROCESS_ID, String.valueOf(camunda7
            .getRepositoryService()
            .getProcessDefinition(camunda7Task.getProcessDefinitionId())
            .getVersion()), before.getId(), camunda7Task.getProcessInstanceId(), camunda7Task
                .getId(), MigrationWorkflowService.TASK_DEFINITION, MigrationWorkflowService.BPMN_TASK_ID);
    final var cibSevenTask = cibSeven
        .getTaskService()
        .createTaskQuery()
        .processInstanceBusinessKey(after.getId())
        .singleResult();
    final var onCibSeven = new UserTaskReference(
        "cs", MODULE_ID, MigrationWorkflowService.BPMN_PROCESS_ID, String.valueOf(cibSeven
            .getRepositoryService()
            .getProcessDefinition(cibSevenTask.getProcessDefinitionId())
            .getVersion()), after.getId(), cibSevenTask.getProcessInstanceId(), cibSevenTask
                .getId(), MigrationWorkflowService.TASK_DEFINITION, MigrationWorkflowService.BPMN_TASK_ID);
    assertEquals(0, cibSeven
        .getRuntimeService()
        .createProcessInstanceQuery()
        .processInstanceBusinessKey(before.getId())
        .count(), "the workflow of Camunda 7 must not run on CIB seven");
    assertEquals(0, camunda7
        .getRuntimeService()
        .createProcessInstanceQuery()
        .processInstanceBusinessKey(after.getId())
        .count(), "the new workflow belongs to the adapter listed first");

    // each half answers about the task of its own engine, and knows nothing of the other one
    assertTrue(bridgeOf("c7").prefilledUserTaskDetails(onCamunda7).isPresent());
    assertTrue(bridgeOf("cs").prefilledUserTaskDetails(onCibSeven).isPresent());
    assertTrue(bridgeOf("cs").prefilledUserTaskDetails(asCibSevens(onCamunda7)).isEmpty());

    // the completion of each task is reported through the half of its engine
    inTransaction(() -> workflowService.processes().completeUserTask(before, onCamunda7.userTaskId()));
    inTransaction(() -> workflowService.processes().completeUserTask(after, onCibSeven.userTaskId()));
    CockpitServer.awaitAnyRequest("/usertask/%s/completed".formatted(onCamunda7.userTaskId()));
    CockpitServer.awaitAnyRequest("/usertask/%s/completed".formatted(onCibSeven.userTaskId()));
    CockpitServer.awaitAnyRequest("/workflow/%s/completed".formatted(onCamunda7.workflowId()));
    CockpitServer.awaitAnyRequest("/workflow/%s/completed".formatted(onCibSeven.workflowId()));

    // and nothing was reported twice: both extensions listen, but each only to its own engine
    CockpitServer.awaitQuiet();
    assertEquals(1, reportsAbout("/usertask/created", before.getId()));
    assertEquals(1, reportsAbout("/usertask/created", after.getId()));
    assertEquals(1, CockpitServer
        .matching("/usertask/%s/completed".formatted(onCamunda7.userTaskId()))
        .size());
    assertEquals(1, CockpitServer
        .matching("/usertask/%s/completed".formatted(onCibSeven.userTaskId()))
        .size());

  }

  private BusinessCockpitBpmsBridge bridgeOf(
      final String adapterId) {

    final var bridges = bridgesPerExtension
        .stream()
        .flatMap(List::stream)
        .toList();
    return bridges
        .stream()
        .filter(bridge -> bridge.adapterId().equals(adapterId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no BPMS half for adapter id '%s' in %s"
            .formatted(adapterId, bridges)));

  }

  private static String businessIdOf(
      final String businessKey) {

    return "\"businessId\":\"%s\"".formatted(businessKey);

  }

  /**
   * @param pathSuffix What the paths of the reports end with
   * @param businessKey The business key of the workflow the reports are about
   * @return How many reports of that kind arrived about the workflow
   */
  private static long reportsAbout(
      final String pathSuffix,
      final String businessKey) {

    return CockpitServer
        .matching(pathSuffix)
        .stream()
        .filter(request -> request.body().contains(businessIdOf(businessKey)))
        .count();

  }

  private static UserTaskReference asCibSevens(
      final UserTaskReference reference) {

    return new UserTaskReference(
        "cs", reference.workflowModuleId(), reference.bpmnProcessId(), reference.processVersion(), reference
            .workflowAggregateId(), reference
                .workflowId(), reference.userTaskId(), reference.taskDefinition(), reference.bpmnTaskId());

  }

  private <T> T inTransaction(
      final Callable<T> work) throws Exception {

    userTransaction.begin();
    try {
      final var result = work.call();
      userTransaction.commit();
      return result;
    } catch (final Exception e) {
      userTransaction.rollback();
      throw e;
    }

  }

  private void inTransaction(
      final Runnable work) throws Exception {

    inTransaction(() -> {
      work.run();
      return null;
    });

  }

  private static <T> T awaitValue(
      final Supplier<T> value,
      final String description) {

    final var deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (true) {
      final var current = value.get();
      if (current != null) {
        return current;
      }
      if (System.currentTimeMillis() >= deadline) {
        fail("not within %d ms: %s".formatted(TIMEOUT_MS, description));
      }
      try {
        Thread.sleep(50);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        fail("interrupted while waiting for %s".formatted(description));
      }
    }

  }

}
