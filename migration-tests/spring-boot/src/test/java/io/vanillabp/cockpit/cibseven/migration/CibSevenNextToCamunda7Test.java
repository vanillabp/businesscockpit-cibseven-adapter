package io.vanillabp.cockpit.cibseven.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import io.vanillabp.camunda7.springboot.engine.Camunda7EngineHolder;
import io.vanillabp.cibseven.springboot.engine.CibSevenEngineHolder;
import io.vanillabp.cockpit.extension.BusinessCockpitExtension;
import io.vanillabp.cockpit.extension.spi.UserTaskReference;
import io.vanillabp.cockpit.extension.test.support.CockpitServer;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * The Business Cockpit while an application moves from Camunda 7 to CIB seven: the Camunda 7
 * adapter and the CIB seven adapter in one application, each with its engine, and both Business
 * Cockpit extensions next to them. See decision 4 in the repository's DECISIONS.md.
 * <p>
 * The application runs twice on the same databases. First with Camunda 7 in front, the way it
 * ran before the migration, and it starts a workflow there. Then with CIB seven in front, and
 * the new workflow lands on CIB seven while the old one still waits on Camunda 7. The user task
 * of each workflow reaches the cockpit once, and the cockpit asks the extension of the engine
 * the task runs on about it.
 */
@ExtendWith(SuppressOutputExtension.class)
@SuppressOutputExtension.SuppressBackgroundOutput
public class CibSevenNextToCamunda7Test {

  private static final long TIMEOUT_MS = 30_000;

  @BeforeAll
  public static void giveCibSevenADatabaseOfItsOwn() {

    System.setProperty(MigrationTestApplication.SECOND_DATABASE, "cockpit-migration-cibseven");

  }

  @AfterAll
  public static void forgetTheSecondDatabase() {

    System.clearProperty(MigrationTestApplication.SECOND_DATABASE);

  }

  @Test
  @DisplayName("A user task on Camunda 7 and one on CIB seven both reach the cockpit through their own BPMS half")
  public void eachEngineReportsThroughItsOwnHalf() {

    CockpitServer.forgetRequests();

    // before the migration: Camunda 7 in front
    try (var before = boot("c7", "cs")) {
      start(before, "started-before");
      CockpitServer.awaitRequestOf("/usertask/created", "\"businessId\":\"started-before\"");
      assertEquals(1, runningOn(camunda7(before), "started-before"));
      assertEquals(0, runningOn(cibSeven(before), "started-before"));
    }

    // after the migration: CIB seven in front, Camunda 7 still holds the older workflow
    try (var after = boot("cs", "c7")) {
      start(after, "started-after");
      CockpitServer.awaitRequestOf("/usertask/created", "\"businessId\":\"started-after\"");
      assertEquals(1, runningOn(cibSeven(after), "started-after"));
      assertEquals(0, runningOn(camunda7(after), "started-after"));

      final var extension = after.getBean(BusinessCockpitExtension.class);
      assertEquals("camunda7", extension.bridgeOf("c7").adapterType());
      assertEquals("cibseven", extension.bridgeOf("cs").adapterType());

      final var onCamunda7 = taskOn(camunda7(after), "c7", "started-before");
      final var onCibSeven = taskOn(cibSeven(after), "cs", "started-after");

      // each half answers about the task of its own engine, and knows nothing of the other one
      assertTrue(extension.bridgeOf("c7").prefilledUserTaskDetails(onCamunda7).isPresent());
      assertTrue(extension.bridgeOf("cs").prefilledUserTaskDetails(onCibSeven).isPresent());
      assertTrue(extension.bridgeOf("cs").prefilledUserTaskDetails(asCibSevens(onCamunda7)).isEmpty());

      // the completion of each task is reported through the half of its engine
      approve(after, "started-before");
      approve(after, "started-after");
      CockpitServer.awaitAnyRequest("/usertask/%s/completed".formatted(onCamunda7.userTaskId()));
      CockpitServer.awaitAnyRequest("/usertask/%s/completed".formatted(onCibSeven.userTaskId()));
      CockpitServer.awaitAnyRequest("/workflow/%s/completed".formatted(onCamunda7.workflowId()));
      CockpitServer.awaitAnyRequest("/workflow/%s/completed".formatted(onCibSeven.workflowId()));

      // and nothing was reported twice: both extensions listen, but each only to its own engine
      CockpitServer.awaitQuiet();
      assertEquals(1, reportsAbout("/usertask/created", "started-before"));
      assertEquals(1, reportsAbout("/usertask/created", "started-after"));
      assertEquals(1, CockpitServer
          .matching("/usertask/%s/completed".formatted(onCamunda7.userTaskId()))
          .size());
      assertEquals(1, CockpitServer
          .matching("/usertask/%s/completed".formatted(onCibSeven.userTaskId()))
          .size());
    }

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
        .filter(request -> request.body().contains("\"businessId\":\"%s\"".formatted(businessKey)))
        .count();

  }

  private static ConfigurableApplicationContext boot(
      final String first,
      final String second) {

    final var properties = new LinkedHashMap<String, Object>();
    properties.put("spring.datasource.url", "jdbc:h2:mem:cockpit-migration;DB_CLOSE_DELAY=-1");
    // the aggregates have to survive the end of one application and the start of the next
    properties.put("spring.jpa.hibernate.ddl-auto", "update");
    properties.put("spring.main.banner-mode", "off");
    properties.put("vanillabp.outbox.poll-interval", "PT0.2S");
    properties.put("vanillabp.outbox.attempt-frequency", "PT0.2S");
    properties.put("vanillabp.adapters.c7.type", "camunda7");
    properties.put("vanillabp.adapters.cs.type", "cibseven");
    properties.put("vanillabp.adapters.cs.data-source-name", "cibSevenDataSource");
    properties.put("vanillabp.prioritized-adapters[0]", first);
    properties.put("vanillabp.prioritized-adapters[1]", second);
    final var module = "vanillabp.workflow-modules.%s.".formatted(MigrationTestApplication.MODULE_ID);
    properties.put(module
        + "adapters.c7.resources-location", "classpath*:migration/processes");
    properties.put(module
        + "adapters.cs.resources-location", "classpath*:migration/processes");
    properties.put("vanillabp.cockpit.rest.base-url", CockpitServer.baseUrl());
    properties.put(module
        + "cockpit.workflow-module-uri", "http://localhost:8081/migration");
    properties.put(module
        + "cockpit.ui-uri-type", "WEBPACK_MF_REACT");
    properties.put(module
        + "cockpit.ui-uri-path", "/remoteEntry.js");
    properties.put(module
        + "cockpit.i18n-languages", "en");
    properties.put(module
        + "cockpit.bpmn-description-language", "en");
    properties.put(module
        + "cockpit.initiator-source", "system");
    return new SpringApplicationBuilder(MigrationTestApplication.class)
        .properties(properties)
        .run();

  }

  private static void start(
      final ConfigurableApplicationContext context,
      final String id) {

    final var aggregate = new MigrationAggregate();
    aggregate.setId(id);
    context.getBean(MigrationWorkflowService.class).start(aggregate);
    awaitValue(() -> MigrationWorkflowService.WAITING_USER_TASKS.get(id), "the user task of workflow "
        + id);

  }

  private static void approve(
      final ConfigurableApplicationContext context,
      final String id) {

    final var aggregate = context.getBean(MigrationRepository.class).findById(id).orElseThrow();
    context
        .getBean(MigrationWorkflowService.class)
        .approve(aggregate, MigrationWorkflowService.WAITING_USER_TASKS.get(id));

  }

  private static EngineReader camunda7(
      final ConfigurableApplicationContext context) {

    final var engine = context
        .getBean("Camunda7_Engine_c7", Camunda7EngineHolder.class)
        .getProcessEngine();
    return businessKey -> {
      final var task = engine
          .getTaskService()
          .createTaskQuery()
          .processInstanceBusinessKey(businessKey)
          .singleResult();
      final var running = engine
          .getRuntimeService()
          .createProcessInstanceQuery()
          .processInstanceBusinessKey(businessKey)
          .count();
      if (task == null) {
        return new EngineTask(running, null, null, null);
      }
      final var definition = engine
          .getRepositoryService()
          .getProcessDefinition(task.getProcessDefinitionId());
      return new EngineTask(running, task.getId(), task.getProcessInstanceId(), String
          .valueOf(definition.getVersion()));
    };

  }

  private static EngineReader cibSeven(
      final ConfigurableApplicationContext context) {

    final var engine = context
        .getBean("CibSeven_Engine_cs", CibSevenEngineHolder.class)
        .getProcessEngine();
    return businessKey -> {
      final var task = engine
          .getTaskService()
          .createTaskQuery()
          .processInstanceBusinessKey(businessKey)
          .singleResult();
      final var running = engine
          .getRuntimeService()
          .createProcessInstanceQuery()
          .processInstanceBusinessKey(businessKey)
          .count();
      if (task == null) {
        return new EngineTask(running, null, null, null);
      }
      final var definition = engine
          .getRepositoryService()
          .getProcessDefinition(task.getProcessDefinitionId());
      return new EngineTask(running, task.getId(), task.getProcessInstanceId(), String
          .valueOf(definition.getVersion()));
    };

  }

  private static long runningOn(
      final EngineReader engine,
      final String businessKey) {

    return engine.read(businessKey).running();

  }

  private static UserTaskReference taskOn(
      final EngineReader engine,
      final String adapterId,
      final String businessKey) {

    final var task = engine.read(businessKey);
    return new UserTaskReference(
        adapterId, MigrationTestApplication.MODULE_ID, MigrationWorkflowService.BPMN_PROCESS_ID, task
            .processVersion(), businessKey, task.processInstanceId(), task
                .taskId(), MigrationWorkflowService.TASK_DEFINITION, MigrationWorkflowService.BPMN_TASK_ID);

  }

  private static UserTaskReference asCibSevens(
      final UserTaskReference reference) {

    return new UserTaskReference(
        "cs", reference.workflowModuleId(), reference.bpmnProcessId(), reference.processVersion(), reference
            .workflowAggregateId(), reference
                .workflowId(), reference.userTaskId(), reference.taskDefinition(), reference.bpmnTaskId());

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

  /**
   * What the test reads of one engine. The two engines have the same API in two packages, so
   * each gets a reader of its own.
   */
  @FunctionalInterface
  private interface EngineReader {

    EngineTask read(
        String businessKey);

  }

  /**
   * One workflow as an engine holds it.
   *
   * @param running The number of running instances with the business key
   * @param taskId The id of the waiting user task, or <code>null</code>
   * @param processInstanceId The instance the task belongs to
   * @param processVersion The version of the deployed process, as the engine counts it
   */
  private record EngineTask(
                            long running,
                            String taskId,
                            String processInstanceId,
                            String processVersion) {
  }

}
