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

    public EducationKnowledgeService(EducationKnowledgeSourceRepository sourceRepository,
                                     KnowledgeDocumentRepository documentRepository,
                                     SensitiveDataSanitizer sanitizer) {
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public EducationKnowledgeSource upsertSource(String tenantId, String userId,
                                                  EducationSourceRequest request) {
        KnowledgeDocument document = documentRepository.findById(request.documentId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "DOCUMENT_NOT_FOUND", "知识文档不存在: " + request.documentId()));
        assertTenant(document.getTenantId(), tenantId);
        if (!document.getOwnerUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DOCUMENT_ACCESS_DENIED",
                    "只有知识文档所有者可以维护课程元数据");
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
        return sourceRepository.save(source);
    }

    @Transactional(readOnly = true)
    public List<EducationKnowledgeSource> listSources(String tenantId, String userId) {
        return sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc(tenantId).stream()
                .filter(source -> visibleDocument(source, userId))
                .toList();
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
