package io.vanillabp.cockpit.cibseven.quarkus.migration;

/**
 * The workflow aggregate of the test. The test sets the id, so it knows the business key of
 * each workflow before it starts.
 */
public class MigrationAggregate {

  private String id;

  public String getId() {

    return id;

  }

  public void setId(
      final String id) {

    this.id = id;

  }

}
