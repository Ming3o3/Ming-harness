package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.KnowledgeDocument;
import org.mingharness.context.KnowledgeDocumentRepository;
import org.mingharness.education.api.EducationSourceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 管理教育知识源元数据，不复制知识正文。 */
@Service
public class EducationKnowledgeService {

    private final EducationKnowledgeSourceRepository sourceRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final SensitiveDataSanitizer sanitizer;
    private final EducationKnowledgeGraphService graphService;

    public EducationKnowledgeService(EducationKnowledgeSourceRepository sourceRepository,
                                     KnowledgeDocumentRepository documentRepository,
                                     SensitiveDataSanitizer sanitizer) {
        this(sourceRepository, documentRepository, sanitizer, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EducationKnowledgeService(EducationKnowledgeSourceRepository sourceRepository,
                                     KnowledgeDocumentRepository documentRepository,
                                     SensitiveDataSanitizer sanitizer,
                                     EducationKnowledgeGraphService graphService) {
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.sanitizer = sanitizer;
        this.graphService = graphService;
    }

    @Transactional
    public EducationKnowledgeSource upsertSource(String tenantId, String userId,
                                                  EducationSourceRequest request) {
        return upsertSource(tenantId, userId, request, false);
    }

    /**
     * 教师可以维护自己可见的组织共享资料元数据，但不能因此获得正文删除权限。
     * 旧的三参数入口保留给内部调用和已有测试，默认仍按资料所有者校验。
     */
    @Transactional
    public EducationKnowledgeSource upsertSource(String tenantId, String userId,
                                                  EducationSourceRequest request,
                                                  boolean canManageSharedDocument) {
        KnowledgeDocument document = documentRepository.findById(request.documentId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "DOCUMENT_NOT_FOUND", "知识文档不存在: " + request.documentId()));
        assertTenant(document.getTenantId(), tenantId);
        if (!document.getOwnerUserId().equals(userId) && !canManageSharedDocument) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DOCUMENT_ACCESS_DENIED",
                    "只有资料所有者或课程教师可以维护课程元数据");
        }
        if (!document.getOwnerUserId().equals(userId) && !document.isVisibleTo(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DOCUMENT_ACCESS_DENIED",
                    "课程教师只能维护自己可见的组织共享资料元数据");
        }
        if (document.getDeletedAt() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "DOCUMENT_DELETED",
                    "已删除的知识文档不能绑定教育元数据");
        }

        String subject = clean(request.subject());
        String gradeLevel = clean(request.gradeLevel());
        String curriculumVersion = clean(request.curriculumVersion());
        var existing = sourceRepository.findByTenantIdAndDocumentId(tenantId, document.getId());
        EducationKnowledgeSource source = existing.orElseGet(() -> new EducationKnowledgeSource(
                tenantId, document.getId(), subject, gradeLevel, curriculumVersion,
                clean(request.chapter()), clean(request.learningObjectives()), clean(request.conceptTags()),
                clean(request.prerequisiteConcepts()), request.effectiveDifficultyLevel(), clean(request.sourceType())));
        if (existing.isPresent()) {
            source.update(subject, gradeLevel, curriculumVersion, clean(request.chapter()),
                    clean(request.learningObjectives()), clean(request.conceptTags()),
                    clean(request.prerequisiteConcepts()), request.effectiveDifficultyLevel(),
                    clean(request.sourceType()));
        }
        EducationKnowledgeSource saved = sourceRepository.save(source);
        if (graphService != null) graphService.replaceDerivedEdges(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EducationKnowledgeSource> listSources(String tenantId, String userId) {
        return sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc(tenantId).stream()
                .filter(source -> visibleDocument(source, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public EducationDependencyGraph resolveDependencyGraph(String tenantId,
                                                           EducationRetrievalFilter filter) {
        return graphService == null || filter == null
                ? EducationDependencyGraph.empty(filter == null ? null : filter.conceptKeyOrNull())
                : graphService.resolve(tenantId, filter);
    }

    /** 管理员治理页只读查看同租户课程元数据；正文仍不从此接口暴露。 */
    @Transactional(readOnly = true)
    public List<EducationKnowledgeSource> listSourcesForGovernance(String tenantId) {
        return sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc(tenantId);
    }

    /**
     * 判断当前学习者是否真的能检索到满足本次课程约束的资料。
     *
     * <p>这里复用展示给学习者的可见性规则，而不是仅检查教育元数据是否存在：资料被删除、
     * 未授权给该学习者，或不满足知识点、难度过滤时，都不能支撑一次教育 Run。</p>
     */
    @Transactional(readOnly = true)
    public boolean hasVisibleMatchingSource(String tenantId, String userId,
                                            EducationRetrievalFilter filter) {
        if (filter == null || !filter.requiresEducationMetadata()) return false;
        return listSources(tenantId, userId).stream().anyMatch(filter::matches);
    }

    @Transactional
    public void deleteSource(String tenantId, String userId, String documentId) {
        EducationKnowledgeSource source = sourceRepository
                .findByTenantIdAndDocumentIdAndDeletedAtIsNull(tenantId, documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EDUCATION_SOURCE_NOT_FOUND", "教育知识源元数据不存在"));
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "DOCUMENT_NOT_FOUND", "知识文档不存在: " + documentId));
        assertTenant(document.getTenantId(), tenantId);
        if (!document.getOwnerUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DOCUMENT_ACCESS_DENIED",
                    "只有知识文档所有者可以删除课程元数据");
        }
        source.markDeleted();
        sourceRepository.save(source);
        if (graphService != null) graphService.removeDerivedEdges(tenantId, documentId);
    }

    private boolean visibleDocument(EducationKnowledgeSource source, String userId) {
        return documentRepository.findById(source.getDocumentId())
                .filter(document -> document.getDeletedAt() == null && document.isVisibleTo(userId))
                .isPresent();
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    private void assertTenant(String actual, String expected) {
        if (actual == null || !actual.equals(expected)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED",
                    "无权访问其他组织的教育知识源");
        }
    }
}
