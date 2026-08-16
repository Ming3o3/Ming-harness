package org.mingharness.runtime.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.UniqueConstraint;
import org.mingharness.education.EducationRunConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "harness_runs", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_run_tenant_idempotency",
        columnNames = {"tenant_id", "idempotency_key"}
))
public class Run {

    @Id
    private String id;
    private String tenantId;
    private String userId;
    private String title;
    private String modelName;
    private String promptVersion;
    private String policyVersion;
    /** 可选的聊天会话归属；独立 Run 保持为空以兼容旧接口。 */
    @Column(name = "conversation_id", length = 128)
    private String conversationId;
    /** Run 创建时冻结工作区，防止用户后来切换项目而让旧 Worker 写入新目录。 */
    @Column(name = "workspace_id", length = 128)
    private String workspaceId;
    /** 教育 Agent 在创建时冻结的课程和学习者状态配置。 */
    @Column(name = "education_mode")
    private Boolean educationMode;
    @Column(name = "education_learner_profile_id", length = 128)
    private String educationLearnerProfileId;
    /** 直接学习会话选定的课程实例快照，避免后续改名或换班后无法追溯课程边界。 */
    @Column(name = "education_course_id", length = 128)
    private String educationCourseId;
    @Column(name = "education_course_code", length = 128)
    private String educationCourseCode;
    @Column(name = "education_course_title", length = 255)
    private String educationCourseTitle;
    @Column(name = "education_learning_goal_id", length = 128)
    private String educationLearningGoalId;
    @Column(name = "education_learning_assignment_id", length = 128)
    private String educationLearningAssignmentId;
    @Column(name = "education_learning_assignment_title", length = 255)
    private String educationLearningAssignmentTitle;
    @Column(name = "education_learning_assignment_instructions", columnDefinition = "text")
    private String educationLearningAssignmentInstructions;
    @Column(name = "education_learning_assignment_teacher_review_note", columnDefinition = "text")
    private String educationLearningAssignmentTeacherReviewNote;
    @Column(name = "education_review_plan_id", length = 255)
    private String educationReviewPlanId;
    @Column(name = "education_learning_goal_title", length = 255)
    private String educationLearningGoalTitle;
    @Column(name = "education_learning_goal_baseline")
    private Double educationLearningGoalBaseline;
    @Column(name = "education_learning_goal_target")
    private Double educationLearningGoalTarget;
    @Column(name = "education_subject", length = 128)
    private String educationSubject;
    @Column(name = "education_grade_level", length = 128)
    private String educationGradeLevel;
    @Column(name = "education_curriculum_version", length = 128)
    private String educationCurriculumVersion;
    @Column(name = "education_concept_key", length = 255)
    private String educationConceptKey;
    @Column(name = "education_min_difficulty")
    private Integer educationMinDifficulty;
    @Column(name = "education_max_difficulty")
    private Integer educationMaxDifficulty;
    @Column(name = "education_pedagogical_mode", length = 64)
    private String educationPedagogicalMode;
    @Column(name = "education_learner_state", columnDefinition = "text")
    private String educationLearnerState;
    @Column(name = "education_retrieval_strategy", length = 32)
    private String educationRetrievalStrategy;
    @Column(name = "education_dependency_graph", columnDefinition = "text")
    private String educationDependencyGraph;
    /** Run 创建时冻结的掌握度、观测次数和答对次数快照。 */
    @Column(name = "education_learner_state_snapshot", columnDefinition = "text")
    private String educationLearnerStateSnapshot;
    /** CALIBRATED 或 ADAPTIVE 策略在创建 Run 时冻结的检索策略快照。 */
    @Column(name = "education_retrieval_weights", columnDefinition = "text")
    private String educationRetrievalWeights;
    /** 创建 Run 时固化的用户模型供应商快照；为空表示沿用环境默认模型或旧数据兼容路径。 */
    @Column(name = "model_config_snapshot_id", length = 128)
    private String modelConfigSnapshotId;
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;
    @Column(name = "permissions_snapshot", columnDefinition = "text")
    private String permissionsSnapshot;
    private String traceId;
    @Column(name = "input_data", columnDefinition = "text")
    private String input;
    @Column(name = "output_data", columnDefinition = "text")
    private String output;
    @Column(columnDefinition = "text")
    private String error;
    @Enumerated(EnumType.STRING)
    private RunStatus status;
    private BigDecimal budget;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String workerId;
    private Instant leaseUntil;
    private Instant heartbeatAt;
    /** Agent 模式会根据模型 Tool Call 动态追加模型和工具步骤。 */
    // local H2 可能已经存在旧 Run，允许 Hibernate update 先新增可空列，再由 getter 提供兼容默认值。
    @Column(name = "agent_mode")
    private Boolean agentMode;
    @Column(name = "max_turns")
    private Integer maxTurns;
    @Column(name = "audit_event_count", nullable = false)
    private long auditEventCount;
    @Column(name = "audit_head_hash", length = 64)
    private String auditHeadHash;
    @Column(name = "audit_head_signature", length = 64)
    private String auditHeadSignature;
    @Version
    private long version;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sequence ASC")
    private List<Step> steps = new ArrayList<>();

    protected Run() {
    }

    public Run(String tenantId, String userId, String title, String input, BigDecimal budget,
               String modelName, String promptVersion, String policyVersion) {
        this(tenantId, userId, title, input, budget, modelName, promptVersion, policyVersion, null);
    }

    public Run(String tenantId, String userId, String title, String input, BigDecimal budget,
               String modelName, String promptVersion, String policyVersion, String idempotencyKey) {
        this(tenantId, userId, title, input, budget, modelName, promptVersion, policyVersion,
                idempotencyKey, null);
    }

    public Run(String tenantId, String userId, String title, String input, BigDecimal budget,
               String modelName, String promptVersion, String policyVersion, String idempotencyKey,
               String permissionsSnapshot) {
        this(tenantId, userId, title, input, budget, modelName, promptVersion, policyVersion,
                idempotencyKey, permissionsSnapshot, false, 1);
    }

    public Run(String tenantId, String userId, String title, String input, BigDecimal budget,
               String modelName, String promptVersion, String policyVersion, String idempotencyKey,
               String permissionsSnapshot, boolean agentMode, int maxTurns) {
        this(tenantId, userId, title, input, budget, modelName, promptVersion, policyVersion,
                idempotencyKey, permissionsSnapshot, agentMode, maxTurns, null);
    }

    public Run(String tenantId, String userId, String title, String input, BigDecimal budget,
               String modelName, String promptVersion, String policyVersion, String idempotencyKey,
               String permissionsSnapshot, boolean agentMode, int maxTurns, String conversationId) {
        this(tenantId, userId, title, input, budget, modelName, promptVersion, policyVersion,
                idempotencyKey, permissionsSnapshot, agentMode, maxTurns, conversationId, null);
    }

    public Run(String tenantId, String userId, String title, String input, BigDecimal budget,
               String modelName, String promptVersion, String policyVersion, String idempotencyKey,
               String permissionsSnapshot, boolean agentMode, int maxTurns, String conversationId,
               String workspaceId) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.title = title;
        this.input = input;
        this.budget = budget;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.policyVersion = policyVersion;
        this.idempotencyKey = idempotencyKey;
        this.permissionsSnapshot = permissionsSnapshot;
        this.conversationId = conversationId;
        this.workspaceId = workspaceId;
        this.agentMode = agentMode;
        this.maxTurns = Math.max(1, maxTurns);
        this.traceId = UUID.randomUUID().toString();
        this.status = RunStatus.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void start() {
        requireStatus(RunStatus.QUEUED);
        this.status = RunStatus.RUNNING;
        this.startedAt = Instant.now();
        this.finishedAt = null;
        touch();
    }

    public void attachModelConfigSnapshot(String snapshotId) {
        this.modelConfigSnapshotId = snapshotId;
    }

    /** 在 Run 持久化前冻结教育上下文，保证重试和异步 Worker 使用同一课程快照。 */
    public void attachEducationConfiguration(EducationRunConfiguration configuration) {
        EducationRunConfiguration value = configuration == null
                ? EducationRunConfiguration.disabled() : configuration;
        this.educationMode = value.enabled();
        this.educationLearnerProfileId = value.learnerProfileId();
        this.educationCourseId = value.courseId();
        this.educationCourseCode = value.courseCode();
        this.educationCourseTitle = value.courseTitle();
        this.educationLearningGoalId = value.learningGoalId();
        this.educationLearningAssignmentId = value.learningAssignmentId();
        this.educationLearningAssignmentTitle = value.learningAssignmentTitle();
        this.educationLearningAssignmentInstructions = value.learningAssignmentInstructions();
        this.educationLearningAssignmentTeacherReviewNote = value.learningAssignmentTeacherReviewNote();
        this.educationReviewPlanId = value.reviewPlanId();
        this.educationLearningGoalTitle = value.learningGoalTitle();
        this.educationLearningGoalBaseline = value.learningGoalBaselineMastery();
        this.educationLearningGoalTarget = value.learningGoalTargetMastery();
        this.educationSubject = value.subject();
        this.educationGradeLevel = value.gradeLevel();
        this.educationCurriculumVersion = value.curriculumVersion();
        this.educationConceptKey = value.conceptKey();
        this.educationMinDifficulty = value.minDifficulty();
        this.educationMaxDifficulty = value.maxDifficulty();
        this.educationPedagogicalMode = value.pedagogicalMode();
        this.educationLearnerState = value.learnerStateSummary();
        this.educationRetrievalStrategy = value.retrievalStrategyValue().name();
        this.educationDependencyGraph = value.dependencyGraphSnapshot();
        this.educationLearnerStateSnapshot = value.learnerStateSnapshot();
    }

    public void attachEducationRetrievalWeights(String snapshot) {
        this.educationRetrievalWeights = snapshot == null || snapshot.isBlank() ? null : snapshot;
    }

    /** ADAPTIVE 策略复用同一持久化列，但通过独立方法名保留业务语义。 */
    public void attachEducationRetrievalPolicy(String snapshot) {
        attachEducationRetrievalWeights(snapshot);
    }

    public EducationRunConfiguration educationConfiguration() {
        return new EducationRunConfiguration(isEducationMode(), educationLearnerProfileId,
                educationLearningGoalId, educationLearningAssignmentId,
                educationLearningAssignmentTitle, educationLearningAssignmentInstructions,
                educationLearningAssignmentTeacherReviewNote,
                educationReviewPlanId,
                educationLearningGoalTitle,
                educationLearningGoalBaseline == null ? 0.0 : educationLearningGoalBaseline,
                educationLearningGoalTarget == null ? 0.0 : educationLearningGoalTarget,
                educationSubject, educationGradeLevel, educationCurriculumVersion, educationConceptKey,
                educationMinDifficulty, educationMaxDifficulty,
                educationPedagogicalMode == null ? "AUTO" : educationPedagogicalMode,
                educationLearnerState == null ? "" : educationLearnerState,
                educationCourseId, educationCourseCode, educationCourseTitle,
                educationRetrievalStrategy == null ? "FULL" : educationRetrievalStrategy,
                educationDependencyGraph, educationLearnerStateSnapshot);
    }

    /** Worker 成功获取执行锁后建立租约。 */
    public void claim(String workerId, Instant leaseUntil) {
        if (status != RunStatus.RUNNING) {
            throw new IllegalStateException("只有执行中的 Run 可以建立 Worker 租约: " + status);
        }
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
        this.heartbeatAt = Instant.now();
        touch();
    }

    /** Worker 在步骤边界刷新租约，避免长任务被恢复器误判。 */
    public void heartbeat(String workerId, Instant leaseUntil) {
        if (status != RunStatus.RUNNING || !java.util.Objects.equals(this.workerId, workerId)) {
            return;
        }
        this.leaseUntil = leaseUntil;
        this.heartbeatAt = Instant.now();
        touch();
    }

    public void clearLease() {
        clearLeaseFields();
        touch();
    }

    private void clearLeaseFields() {
        this.workerId = null;
        this.leaseUntil = null;
        this.heartbeatAt = null;
    }

    public void succeed(String output) {
        requireStatus(RunStatus.RUNNING);
        this.output = output;
        this.status = RunStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
        clearLeaseFields();
        touch();
    }

    public void fail(String error) {
        if (status == RunStatus.SUCCEEDED || status == RunStatus.CANCELLED) {
            return;
        }
        this.error = error;
        this.status = RunStatus.FAILED;
        this.finishedAt = Instant.now();
        clearLeaseFields();
        touch();
    }

    public void timeout(String error) {
        if (status == RunStatus.SUCCEEDED || status == RunStatus.CANCELLED) {
            return;
        }
        this.error = error;
        this.status = RunStatus.TIMED_OUT;
        this.finishedAt = Instant.now();
        clearLeaseFields();
        touch();
    }

    public void cancel() {
        if (status == RunStatus.SUCCEEDED || status == RunStatus.FAILED || status == RunStatus.CANCELLED) {
            return;
        }
        this.status = RunStatus.CANCELLED;
        this.finishedAt = Instant.now();
        clearLeaseFields();
        touch();
    }

    public void waitApproval() {
        requireStatus(RunStatus.RUNNING);
        this.status = RunStatus.WAITING_APPROVAL;
        touch();
    }

    public void resumeAfterApproval() {
        requireStatus(RunStatus.WAITING_APPROVAL);
        this.status = RunStatus.RUNNING;
        touch();
    }

    public void retry() {
        if (status != RunStatus.FAILED && status != RunStatus.TIMED_OUT) {
            throw new IllegalStateException("只有失败或超时任务可以重试: " + status);
        }
        this.status = RunStatus.QUEUED;
        this.error = null;
        this.output = null;
        touch();
    }

    public void addStep(Step step) {
        step.attachTo(this);
        this.steps.add(step);
        touch();
    }

    /** 更新 Run 的已签名审计链头，用于检测事件删除、乱序和替换。 */
    public void updateAuditHead(long eventCount, String eventHash, String headSignature) {
        if (eventCount != auditEventCount + 1) {
            throw new IllegalStateException("审计事件序号不连续");
        }
        this.auditEventCount = eventCount;
        this.auditHeadHash = eventHash;
        this.auditHeadSignature = headSignature;
        touch();
    }

    private void requireStatus(RunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Run 状态不允许执行当前操作: " + status);
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public String getPolicyVersion() { return policyVersion; }
    public String getConversationId() { return conversationId; }
    public String getWorkspaceId() { return workspaceId; }
    public boolean isEducationMode() { return Boolean.TRUE.equals(educationMode); }
    public String getEducationLearnerProfileId() { return educationLearnerProfileId; }
    public String getEducationCourseId() { return educationCourseId; }
    public String getEducationCourseCode() { return educationCourseCode; }
    public String getEducationCourseTitle() { return educationCourseTitle; }
    public String getEducationLearningGoalId() { return educationLearningGoalId; }
    public String getEducationLearningAssignmentId() { return educationLearningAssignmentId; }
    public String getEducationLearningAssignmentTitle() { return educationLearningAssignmentTitle; }
    public String getEducationLearningAssignmentInstructions() { return educationLearningAssignmentInstructions; }
    public String getEducationReviewPlanId() { return educationReviewPlanId; }
    public String getEducationLearningGoalTitle() { return educationLearningGoalTitle; }
    public Double getEducationLearningGoalBaseline() { return educationLearningGoalBaseline; }
    public Double getEducationLearningGoalTarget() { return educationLearningGoalTarget; }
    public String getEducationSubject() { return educationSubject; }
    public String getEducationGradeLevel() { return educationGradeLevel; }
    public String getEducationCurriculumVersion() { return educationCurriculumVersion; }
    public String getEducationConceptKey() { return educationConceptKey; }
    public Integer getEducationMinDifficulty() { return educationMinDifficulty; }
    public Integer getEducationMaxDifficulty() { return educationMaxDifficulty; }
    public String getEducationPedagogicalMode() { return educationPedagogicalMode; }
    public String getEducationLearnerState() { return educationLearnerState; }
    public String getEducationRetrievalStrategy() { return educationRetrievalStrategy == null ? "FULL" : educationRetrievalStrategy; }
    public String getEducationDependencyGraph() { return educationDependencyGraph; }
    public String getEducationLearnerStateSnapshot() { return educationLearnerStateSnapshot; }
    public String getEducationRetrievalWeights() { return educationRetrievalWeights; }
    public String getModelConfigSnapshotId() { return modelConfigSnapshotId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPermissionsSnapshot() { return permissionsSnapshot; }
    public String getTraceId() { return traceId; }
    public String getInput() { return input; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public RunStatus getStatus() { return status; }
    public BigDecimal getBudget() { return budget; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getWorkerId() { return workerId; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public boolean isAgentMode() { return Boolean.TRUE.equals(agentMode); }
    public int getMaxTurns() { return maxTurns == null ? 1_000 : Math.max(1, maxTurns); }
    public long getAuditEventCount() { return auditEventCount; }
    public String getAuditHeadHash() { return auditHeadHash; }
    public String getAuditHeadSignature() { return auditHeadSignature; }
    public long getDurationMs() {
        if (startedAt == null) {
            return 0;
        }
        Instant end = finishedAt == null ? Instant.now() : finishedAt;
        return java.time.Duration.between(startedAt, end).toMillis();
    }
    public long getVersion() { return version; }
    public List<Step> getSteps() { return steps; }
}
