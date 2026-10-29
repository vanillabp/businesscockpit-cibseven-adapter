package io.vanillabp.cockpit.cibseven.springboot;

import org.cibseven.bpm.model.bpmn.BpmnModelInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.vanillabp.cibseven.CibSevenProcessingContext;
import io.vanillabp.cibseven.api.CibSevenEngineFacts;
import io.vanillabp.cibseven.engine.CibSevenEngineCustomizer;
import io.vanillabp.cockpit.cibseven.CibSevenCockpitCustomizer;
import io.vanillabp.cockpit.cibseven.CibSevenCockpitWiring;
import io.vanillabp.cockpit.cibseven.CibSevenWorkflowProcesses;
import io.vanillabp.cockpit.extension.spi.BusinessCockpitEventPublisher;
import io.vanillabp.integration.adapter.migration.config.MigrationAdapterProperties;
import io.vanillabp.integration.adapter.spi.NameClashAvoidanceSupport;
import io.vanillabp.integration.adapter.spi.workflowtask.WorkflowTaskWiring;
import io.vanillabp.integration.extension.spi.ExtensionWiringService;

/**
 * Registers the CIB seven half of the Business Cockpit extension on Spring Boot.
 * <p>
  * Nothing here decides anything. What the extension does with an engine is decided in the
  * platform-neutral module of this repository. This class does what only Spring can do: find the
  * beans and put the extension's own where VanillaBP and the CIB seven adapter collect them.
 * <p>
 * It runs after VanillaBP's own auto-configuration, named rather than referenced, because an
 * extension does not compile against a platform integration.
 */
@AutoConfiguration(afterName = "io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration")
@ConditionalOnBean(MigrationAdapterProperties.class)
@Import(CibSevenCockpitBeanRegistrar.class)
public class CibSevenCockpitAutoConfiguration {

  /**
   * Spring builds the configuration to call the bean methods below. It keeps no
   * state of its own.
   */
  public CibSevenCockpitAutoConfiguration() {

  }

  /**
   * Provides the one store of deployed processes of this application.
   *
   * @return Which BPMN processes of which workflow modules this application deployed, shared
   *         by the wiring service filling it and by everything translating an engine's
   *         identifiers back
   */
  @Bean
  public CibSevenWorkflowProcesses businessCockpitCibSevenWorkflowProcesses() {

    return new CibSevenWorkflowProcesses();

  }

  /**
   * Provides this extension's wiring service.
   *
   * @param processes The claimed processes
   * @param workflowTaskWiring VanillaBP's registry, which answers whether a
   *          <code>&#64;WorkflowService</code> claims a process
   * @param publisher The extension, which is told the user tasks of every wired model. It is
   *          resolved when the first model is wired
   * @return This extension's place in VanillaBP's deployment pipeline, taken for a workflow
   *         module which runs on CIB seven and for no other
   */
  @Bean
  public ExtensionWiringService<BpmnModelInstance, CibSevenProcessingContext> businessCockpitCibSevenWiringService(
      final CibSevenWorkflowProcesses processes,
      final WorkflowTaskWiring workflowTaskWiring,
      final ObjectProvider<BusinessCockpitEventPublisher> publisher) {

    return new CibSevenCockpitWiring(processes, workflowTaskWiring, publisher::getObject);

  }

  /**
   * The listeners and the history handler of every CIB seven engine this application builds.
   * <p>
    * The type is the concrete one rather than {@link CibSevenEngineCustomizer}. The bridge of
    * each adapter id takes its scope from here, and the adapter collects customizers by their
    * interface either way.
   *
   * @param processes The deployed processes
   * @param scoping VanillaBP's name-clash avoidance
   * @param engines What the CIB seven adapter knows about each of its engines, one bean per
   *          configured adapter id. They are collected when they are first needed rather than
   *          now: each of them is built from an engine, and an engine is built from this bean
   * @param publisher Where an observed event is reported. It is resolved on the first event
   *          rather than now: this bean is asked for while an engine is being built, and the
   *          extension it would return is built from those engines
   * @return The customizer
   */
  @Bean
  public CibSevenCockpitCustomizer businessCockpitCibSevenEngineCustomizer(
      final CibSevenWorkflowProcesses processes,
      final NameClashAvoidanceSupport scoping,
      final ObjectProvider<CibSevenEngineFacts> engines,
      final ObjectProvider<BusinessCockpitEventPublisher> publisher) {

    return new CibSevenCockpitCustomizer(
        processes, scoping, () -> engines.stream().toList(), publisher::getObject);

  }

}
