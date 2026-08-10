package org.mingharness.education.api;

/** 测评记录引用的课程知识源摘要，不包含上下文正文。 */
public record AssessmentEvidenceReference(
        String documentId,
        String title,
        String citation
) {
}
