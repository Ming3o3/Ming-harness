package org.mingharness.runtime.repository;

import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunRepository extends JpaRepository<Run, String> {
    List<Run> findTop50ByOrderByCreatedAtDesc();
    long countByStatus(RunStatus status);
}
