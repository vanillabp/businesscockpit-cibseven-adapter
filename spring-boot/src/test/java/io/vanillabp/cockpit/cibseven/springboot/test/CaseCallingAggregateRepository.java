package io.vanillabp.cockpit.cibseven.springboot.test;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores the workflow aggregates of the calling cases.
 */
public interface CaseCallingAggregateRepository extends JpaRepository<CaseCallingAggregate, Long> {
}
