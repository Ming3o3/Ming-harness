package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, String> {

    List<LearnerProfile> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);

    Optional<LearnerProfile> findTop1ByTenantIdAndUserIdAndActiveTrueOrderByUpdatedAtDesc(
            String tenantId, String userId);

    Optional<LearnerProfile> findByTenantIdAndUserIdAndSubjectAndGradeLevelAndCurriculumVersion(
            String tenantId, String userId, String subject, String gradeLevel, String curriculumVersion);

    Optional<LearnerProfile> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);
}
