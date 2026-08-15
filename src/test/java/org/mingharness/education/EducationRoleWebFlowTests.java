package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.repository.RunRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三角色真实 HTTP 验收：教师配置课程并布置作业，学生在课程约束下学习、提交，教师完成复核。
 *
 * <p>该测试使用静态 API Key 模拟正式环境，而不是直接调用 Service，确保认证、路径权限、
 * 租户隔离、课程所有权和教育 Run 都经过真实的 MVC 请求链。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "harness.auth.mode=api-key",
        "harness.auth.api-keys=admin-flow-key|tenant-flow|admin-flow|context.read,context.write,education.read,education.write,education.assign,ops.read;teacher-flow-key|tenant-flow|teacher-flow|context.read,context.write,education.read,education.write,education.assign,education.evaluate,run.read,run.create,run.execute;student-flow-key|tenant-flow|student-flow|education.read,education.write,run.read,run.create,run.execute",
        "harness.execution.mode=sync",
        "harness.local-execution.async=false",
        "harness.redis.enabled=false",
        "harness.messaging.enabled=false",
        "harness.model.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EducationRoleWebFlowTests {

    private static final String TENANT = "tenant-flow";
    private static final String TEACHER = "teacher-flow";
    private static final String STUDENT = "student-flow";

    @LocalServerPort
    private int port;

    @Autowired
    private RunRepository runRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldLetTeacherConfigureVisibleSharedDocumentButKeepStudentReadOnly() throws Exception {
        HttpResponse<String> document = request("admin-flow-key", "POST", "/api/context/documents",
                "{\"title\":\"组织共享函数教材\",\"content\":\"函数定义域课程规则\","
                        + "\"sensitivity\":\"INTERNAL\",\"allowedUsers\":\"teacher-flow\"}");
        assertEquals(201, document.statusCode(), document.body());
        String documentId = json(document).path("id").asText();

        HttpResponse<String> teacherSource = request("teacher-flow-key", "POST", "/api/education/sources",
                sourceRequest(documentId));
        assertEquals(201, teacherSource.statusCode(), teacherSource.body());
        assertEquals("函数定义域", json(teacherSource).path("conceptTags").asText());

        HttpResponse<String> studentSource = request("student-flow-key", "POST", "/api/education/sources",
                sourceRequest(documentId));
        assertError(studentSource, 403, "PERMISSION_DENIED");
    }

    @Test
    void shouldCompleteTeacherStudentCourseBusinessLoopOverHttp() throws Exception {
        // 学生只消费课程，不能创建课程或维护课程资料。
        HttpResponse<String> studentCourseDenied = request("student-flow-key", "POST",
                "/api/education/courses", "{\"code\":\"student-course\",\"title\":\"越权课程\","
                        + "\"subject\":\"数学\",\"gradeLevel\":\"高中一年级\",\"curriculumVersion\":\"人教A版\"}");
        assertError(studentCourseDenied, 403, "PERMISSION_DENIED");

        HttpResponse<String> studentSourceDenied = request("student-flow-key", "POST",
                "/api/education/sources", sourceRequest("missing-document"));
        assertError(studentSourceDenied, 403, "PERMISSION_DENIED");

        // 教师上传正文，再补齐教育检索所需元数据；课程资料明确授权给学生。
        HttpResponse<String> document = request("teacher-flow-key", "POST", "/api/context/documents",
                "{\"title\":\"函数定义域教材\",\"content\":\"函数定义域是使函数表达式有意义的自变量取值范围。"
                        + "判定函数定义域时，要结合分母不为零和偶次根式被开方数不小于零等约束。\","
                        + "\"sensitivity\":\"INTERNAL\",\"allowedUsers\":\"" + STUDENT + "\"}");
        assertEquals(201, document.statusCode(), document.body());
        String documentId = json(document).path("id").asText();
        assertFalse(documentId.isBlank(), document.body());

        HttpResponse<String> source = request("teacher-flow-key", "POST", "/api/education/sources",
                sourceRequest(documentId));
        assertEquals(201, source.statusCode(), source.body());
        assertEquals("函数定义域", json(source).path("conceptTags").asText());

        HttpResponse<String> course = request("teacher-flow-key", "POST", "/api/education/courses",
                "{\"code\":\"math-g1-flow\",\"title\":\"高一数学函数基础\",\"subject\":\"数学\","
                        + "\"gradeLevel\":\"高中一年级\",\"curriculumVersion\":\"人教A版\"}");
        assertEquals(201, course.statusCode(), course.body());
        String courseId = json(course).path("id").asText();
        assertFalse(courseId.isBlank(), course.body());

        HttpResponse<String> enrollment = request("teacher-flow-key", "POST",
                "/api/education/courses/" + courseId + "/enrollments",
                "{\"learnerUserId\":\"" + STUDENT + "\"}");
        assertEquals(201, enrollment.statusCode(), enrollment.body());

        String dueAt = Instant.now().plusSeconds(86_400).toString();
        HttpResponse<String> batch = request("teacher-flow-key", "POST",
                "/api/education/courses/" + courseId + "/assignments",
                "{\"title\":\"函数定义域练习\",\"instructions\":\"完成一道题并写出判定依据\","
                        + "\"conceptKey\":\"函数定义域\",\"targetMastery\":0.3,\"dueAt\":\"" + dueAt + "\"}",
                "flow-assignment-batch");
        assertEquals(201, batch.statusCode(), batch.body());
        assertEquals(1, json(batch).path("assignmentCount").asInt());
        String assignmentId = json(batch).path("assignments").get(0).path("id").asText();
        assertFalse(assignmentId.isBlank(), batch.body());

        // 学生可以建立画像并接受教师布置的课程作业。
        HttpResponse<String> profile = request("student-flow-key", "POST", "/api/education/profiles",
                "{\"subject\":\"数学\",\"gradeLevel\":\"高中一年级\","
                        + "\"curriculumVersion\":\"人教A版\",\"learningGoal\":\"掌握函数定义域\",\"language\":\"zh-CN\"}");
        assertEquals(201, profile.statusCode(), profile.body());

        HttpResponse<String> accepted = request("student-flow-key", "POST",
                "/api/education/assignments/" + assignmentId + "/accept", "{}");
        assertEquals(200, accepted.statusCode(), accepted.body());
        String goalId = json(accepted).path("learningGoalId").asText();
        assertFalse(goalId.isBlank(), accepted.body());

        // 作业主入口会自动创建会话并启动绑定课程、画像和目标的教育 Run。
        HttpResponse<String> started = request("student-flow-key", "POST",
                "/api/education/assignments/" + assignmentId + "/start",
                "{\"maxTurns\":2,\"courseId\":\"" + courseId + "\"}");
        assertEquals(200, started.statusCode(), started.body());
        String conversationId = json(started).path("conversation").path("conversation").path("id").asText();
        assertFalse(conversationId.isBlank(), started.body());
        String runId = json(started).path("conversation").path("messages").get(1).path("runId").asText();
        assertFalse(runId.isBlank(), started.body());

        HttpResponse<String> run = request("student-flow-key", "GET", "/api/runs/" + runId, null);
        assertEquals(200, run.statusCode(), run.body());
        JsonNode runJson = json(run);
        assertEquals("SUCCEEDED", runJson.path("run").path("status").asText(), run.body());
        assertEquals(courseId, runJson.path("run").path("educationCourseId").asText(), run.body());
        assertEquals(assignmentId, runJson.path("run").path("educationLearningAssignmentId").asText(), run.body());
        String stepId = runJson.path("steps").get(0).path("id").asText();
        assertFalse(stepId.isBlank(), run.body());

        // 教育 Agent 的状态更新必须绑定已成功 Run 和真实步骤；一次观察即可达到本测试目标阈值。
        HttpResponse<String> assessment = request("student-flow-key", "POST",
                "/api/education/goals/" + goalId + "/assessments",
                "{\"runId\":\"" + runId + "\",\"stepId\":\"" + stepId
                        + "\",\"conceptKey\":\"函数定义域\",\"correct\":true,\"observedMastery\":1.0,"
                        + "\"evidenceText\":\"学生写出分母不为零的判定依据\",\"feedback\":\"依据完整\"}");
        assertEquals(201, assessment.statusCode(), assessment.body());
        assertTrue(json(assessment).path("masteryAfter").asDouble() >= 0.3, assessment.body());

        HttpResponse<String> completedAssignment = request("student-flow-key", "GET",
                "/api/education/assignments/" + assignmentId, null);
        assertEquals(200, completedAssignment.statusCode(), completedAssignment.body());
        assertEquals("COMPLETED", json(completedAssignment).path("status").asText(), completedAssignment.body());
        assertEquals("PENDING", json(completedAssignment).path("reviewStatus").asText(), completedAssignment.body());

        HttpResponse<String> submission = request("student-flow-key", "POST",
                "/api/education/assignments/" + assignmentId + "/submissions",
                "{\"runId\":\"" + runId + "\",\"content\":\"函数定义域为使表达式有意义的自变量范围；本题分母必须不为零。\"}");
        assertEquals(201, submission.statusCode(), submission.body());
        assertEquals(runId, json(submission).path("runId").asText(), submission.body());

        // 学生不能调用教师复核接口；教师只能复核自己的作业，并且必须填写三维量规。
        HttpResponse<String> studentReviewDenied = request("student-flow-key", "POST",
                "/api/education/assignments/" + assignmentId + "/review",
                "{\"decision\":\"VERIFY\",\"note\":\"学生越权\","
                        + "\"contentCorrectnessScore\":5,\"evidenceQualityScore\":5,\"transferReadinessScore\":5}");
        assertError(studentReviewDenied, 403, "PERMISSION_DENIED");

        HttpResponse<String> teacherReview = request("teacher-flow-key", "POST",
                "/api/education/assignments/" + assignmentId + "/review",
                "{\"decision\":\"VERIFY\",\"note\":\"已核对提交物和 Run 证据\","
                        + "\"contentCorrectnessScore\":5,\"evidenceQualityScore\":4,\"transferReadinessScore\":4}");
        assertEquals(200, teacherReview.statusCode(), teacherReview.body());
        assertEquals("VERIFIED", json(teacherReview).path("reviewStatus").asText(), teacherReview.body());

        HttpResponse<String> progress = request("teacher-flow-key", "GET",
                "/api/education/courses/" + courseId + "/progress", null);
        assertEquals(200, progress.statusCode(), progress.body());
        assertEquals(1, json(progress).path("assignmentTotal").asInt(), progress.body());
        assertEquals(1, json(progress).path("reviewVerified").asInt(), progress.body());
    }

    @Test
    void shouldLetStudentJoinTeacherCourseWithSharedCode() throws Exception {
        HttpResponse<String> course = request("teacher-flow-key", "POST", "/api/education/courses",
                "{\"title\":\"邀请码课程\","
                        + "\"subject\":\"数学\",\"gradeLevel\":\"高中一年级\",\"curriculumVersion\":\"人教A版\"}");
        assertEquals(201, course.statusCode(), course.body());
        String courseCode = json(course).path("code").asText();
        assertTrue(courseCode.matches("COURSE-[A-Z0-9]{8}"), course.body());
        String joinCode = json(course).path("joinCode").asText();
        assertTrue(joinCode.matches("[A-Z0-9]{8}"), course.body());

        HttpResponse<String> joined = request("student-flow-key", "POST", "/api/education/courses/join",
                "{\"joinCode\":\" " + joinCode.toLowerCase() + " \"}");
        assertEquals(200, joined.statusCode(), joined.body());
        assertEquals(courseCode, json(joined).path("code").asText(), joined.body());
        assertEquals(1, json(joined).path("activeEnrollmentCount").asInt(), joined.body());

        HttpResponse<String> teacherJoinDenied = request("teacher-flow-key", "POST", "/api/education/courses/join",
                "{\"joinCode\":\"" + joinCode + "\"}");
        assertError(teacherJoinDenied, 403, "EDUCATION_STUDENT_ONLY");

        HttpResponse<String> repeated = request("student-flow-key", "POST", "/api/education/courses/join",
                "{\"joinCode\":\"" + joinCode + "\"}");
        assertEquals(200, repeated.statusCode(), repeated.body());
        assertEquals(1, json(repeated).path("activeEnrollmentCount").asInt(), repeated.body());
    }

    @Test
    void shouldKeepAdminEducationWorkspaceReadOnly() throws Exception {
        HttpResponse<String> course = request("teacher-flow-key", "POST", "/api/education/courses",
                "{\"code\":\"admin-read-only-flow\",\"title\":\"管理员只读验收课程\",\"subject\":\"数学\","
                        + "\"gradeLevel\":\"高中一年级\",\"curriculumVersion\":\"人教A版\"}");
        assertEquals(201, course.statusCode(), course.body());
        String courseId = json(course).path("id").asText();

        HttpResponse<String> adminCourses = request("admin-flow-key", "GET", "/api/education/courses", null);
        assertEquals(200, adminCourses.statusCode(), adminCourses.body());
        assertTrue(adminCourses.body().contains(courseId), adminCourses.body());

        HttpResponse<String> adminCourse = request("admin-flow-key", "GET",
                "/api/education/courses/" + courseId, null);
        assertEquals(200, adminCourse.statusCode(), adminCourse.body());

        HttpResponse<String> adminMetrics = request("admin-flow-key", "GET",
                "/api/education/metrics", null);
        assertEquals(200, adminMetrics.statusCode(), adminMetrics.body());
        assertTrue(json(adminMetrics).path("assignmentTotal").asInt() >= 1, adminMetrics.body());

        HttpResponse<String> adminSources = request("admin-flow-key", "GET",
                "/api/education/sources", null);
        assertEquals(200, adminSources.statusCode(), adminSources.body());

        // 管理员治理页可以识别课程资料，但教育接口仍只返回资料元数据，不返回正文。
        HttpResponse<String> sourceDocument = request("teacher-flow-key", "POST", "/api/context/documents",
                "{\"title\":\"管理员可识别的课程资料\",\"content\":\"不得通过教育来源接口返回的正文\","
                        + "\"sensitivity\":\"INTERNAL\"}");
        assertEquals(201, sourceDocument.statusCode(), sourceDocument.body());
        String sourceDocumentId = json(sourceDocument).path("id").asText();
        HttpResponse<String> configuredSource = request("teacher-flow-key", "POST", "/api/education/sources",
                sourceRequest(sourceDocumentId));
        assertEquals(201, configuredSource.statusCode(), configuredSource.body());
        HttpResponse<String> refreshedAdminSources = request("admin-flow-key", "GET",
                "/api/education/sources", null);
        assertEquals(200, refreshedAdminSources.statusCode(), refreshedAdminSources.body());
        JsonNode sourceMetadata = json(refreshedAdminSources).findValue("documentTitle");
        assertNotNull(sourceMetadata, refreshedAdminSources.body());
        assertEquals("管理员可识别的课程资料", sourceMetadata.asText(), refreshedAdminSources.body());
        assertFalse(refreshedAdminSources.body().contains("不得通过教育来源接口返回的正文"), refreshedAdminSources.body());

        HttpResponse<String> adminCreate = request("admin-flow-key", "POST", "/api/education/courses",
                "{\"code\":\"admin-must-not-create\",\"title\":\"越权课程\",\"subject\":\"数学\","
                        + "\"gradeLevel\":\"高中一年级\",\"curriculumVersion\":\"人教A版\"}");
        assertError(adminCreate, 403, "EDUCATION_ADMIN_READ_ONLY");

        HttpResponse<String> adminEnroll = request("admin-flow-key", "POST",
                "/api/education/courses/" + courseId + "/enrollments", "{\"learnerUserId\":\"student-flow\"}");
        assertError(adminEnroll, 403, "EDUCATION_ADMIN_READ_ONLY");
    }

    @Test
    void shouldAllowTeacherToJudgeFrozenEvidenceButRejectStudentSubmission() throws Exception {
        Run run = seededSucceededEducationRun();
        runRepository.saveAndFlush(run);
        String stepId = run.getSteps().get(0).getId();
        String citation = "document:doc-judgment#window:0";
        String judgment = "{\"stepId\":\"" + stepId + "\",\"evidenceCitation\":\""
                + citation + "\",\"targetGroundingScore\":5,"
                + "\"prerequisiteUtilityScore\":4,\"difficultyFitScore\":4,"
                + "\"overallUtilityScore\":5}";

        HttpResponse<String> studentSubmit = request("student-flow-key", "POST",
                "/api/education/runs/" + run.getId() + "/retrieval-judgments", judgment);
        assertError(studentSubmit, 403, "PERMISSION_DENIED");

        HttpResponse<String> teacherSubmit = request("teacher-flow-key", "POST",
                "/api/education/runs/" + run.getId() + "/retrieval-judgments", judgment);
        assertEquals(201, teacherSubmit.statusCode(), teacherSubmit.body());
        assertEquals(citation, json(teacherSubmit).path("evidenceCitation").asText(), teacherSubmit.body());

        HttpResponse<String> studentRead = request("student-flow-key", "GET",
                "/api/education/runs/" + run.getId() + "/retrieval-judgments", null);
        assertEquals(200, studentRead.statusCode(), studentRead.body());
        assertEquals(1, json(studentRead).size(), studentRead.body());

        HttpResponse<String> calibration = request("teacher-flow-key", "GET",
                "/api/education/retrieval-calibration", null);
        assertEquals(200, calibration.statusCode(), calibration.body());
        assertEquals(1, json(calibration).path("sampleCount").asInt(), calibration.body());
        assertEquals("CALIBRATED", json(calibration).path("sampleStatus").asText(), calibration.body());

        HttpResponse<String> evidenceImpact = request("teacher-flow-key", "GET",
                "/api/education/evidence-impact", null);
        assertEquals(200, evidenceImpact.statusCode(), evidenceImpact.body());
        assertEquals("NO_DATA", json(evidenceImpact).path("sampleStatus").asText(), evidenceImpact.body());
    }

    private Run seededSucceededEducationRun() {
        Run run = new Run(TENANT, STUDENT, "证据标注验收 Run", "函数定义域", BigDecimal.ONE,
                "model", "prompt", "policy");
        run.attachEducationConfiguration(new EducationRunConfiguration(
                true, "profile-judgment", "goal-judgment", "函数目标", 0.2, 0.8,
                "数学", "高中一年级", "人教A版", "函数定义域", null, null,
                "EXPLAIN", "函数=0.20"));
        run.start();
        Step step = new Step(1, StepType.MODEL, "model.complete", "函数定义域");
        step.start();
        step.setContextEvidenceJson(ContextEvidenceSnapshotCodec.encode(List.of(
                new ContextEvidence("doc-judgment", "函数教材", "document:doc-judgment#window:0",
                        "函数定义域内容", 0.9, "目标证据锚点", List.of()))));
        step.succeed("回答");
        run.addStep(step);
        run.succeed("完成");
        return run;
    }

    private String sourceRequest(String documentId) {
        return "{\"documentId\":\"" + documentId + "\",\"subject\":\"数学\","
                + "\"gradeLevel\":\"高中一年级\",\"curriculumVersion\":\"人教A版\","
                + "\"chapter\":\"函数\",\"learningObjectives\":\"掌握定义域判定\","
                + "\"conceptTags\":\"函数定义域\",\"prerequisiteConcepts\":\"函数概念\","
                + "\"difficultyLevel\":3,\"sourceType\":\"TEXTBOOK\"}";
    }

    private HttpResponse<String> request(String key, String method, String path, String body) throws Exception {
        return request(key, method, path, body, null);
    }

    private HttpResponse<String> request(String key, String method, String path, String body,
                                         String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + key);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if ("POST".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
        } else {
            throw new IllegalArgumentException("unsupported method: " + method);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        JsonNode node = objectMapper.reader().readTree(response.body());
        assertNotNull(node, response.body());
        return node;
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.body().contains(code), response.body());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
