package org.mingharness.runtime.repository;

import org.mingharness.runtime.domain.Step;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StepRepository extends JpaRepository<Step, String> {
    List<Step> findByRunIdOrderBySequenceAsc(String runId);
}
