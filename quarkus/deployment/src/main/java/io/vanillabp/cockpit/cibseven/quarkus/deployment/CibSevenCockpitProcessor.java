package io.vanillabp.cockpit.cibseven.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.vanillabp.cockpit.cibseven.quarkus.CibSevenCockpitProducer;

/**
 * What the CIB seven half of the Business Cockpit extension has to say at build time.
 * <p>
  * It produces no VanillaBP build item. Unlike a BPMS adapter, an extension announces itself by
  * the beans it produces. The engine customizer among those beans is already on the unremovable
  * list of the CIB seven adapter's own deployment module, so nothing has to be said about it here
  * either.
 */
class CibSevenCockpitProcessor {

  private static final String FEATURE = "vanillabp-business-cockpit-cibseven";

  /**
   * Quarkus builds the processor at build time to call its build steps. It keeps no state of
   * its own.
   */
  CibSevenCockpitProcessor() {

  }

  /**
   * Announces the extension and makes the runtime's producer a bean Quarkus keeps.
   *
   * @param featureProducer Where the feature is announced, so that a booting application lists
   *          the extension
   * @return The producer class, as a bean nothing may remove
   */
  @BuildStep
  AdditionalBeanBuildItem registerProducer(
      final BuildProducer<FeatureBuildItem> featureProducer) {

    featureProducer.produce(new FeatureBuildItem(FEATURE));
    return AdditionalBeanBuildItem
        .builder()
        .addBeanClass(CibSevenCockpitProducer.class)
        .setUnremovable()
        .build();

  }

}
