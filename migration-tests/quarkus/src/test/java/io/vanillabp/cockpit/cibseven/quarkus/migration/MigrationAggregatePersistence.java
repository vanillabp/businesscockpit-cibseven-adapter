package io.vanillabp.cockpit.cibseven.quarkus.migration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vanillabp.integration.spi.AggregatePersistenceAware;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Keeps the workflow aggregates of the test in memory. The test is about the two extensions,
 * not about persistence.
 */
@ApplicationScoped
public class MigrationAggregatePersistence implements AggregatePersistenceAware<MigrationAggregate> {

  private final Map<String, MigrationAggregate> aggregates = new ConcurrentHashMap<>();

  @Override
  public Class<MigrationAggregate> getAggregateClass() {

    return MigrationAggregate.class;

  }

  @Override
  public MigrationAggregate save(
      final MigrationAggregate aggregate) {

    aggregates.put(aggregate.getId(), aggregate);
    return aggregate;

  }

  @Override
  public MigrationAggregate loadById(
      final Object aggregateId) {

    return aggregates.get(String.valueOf(aggregateId));

  }

  @Override
  public Object getAggregateId(
      final MigrationAggregate aggregate) {

    return aggregate.getId();

  }

}
