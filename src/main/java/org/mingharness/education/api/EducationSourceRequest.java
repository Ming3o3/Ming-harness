package org.mingharness.education.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 文档的课程约束元数据；正文仍通过 context 文档接口管理。 */
public record EducationSourceRequest(
        @NotBlank(message = "知识文档 ID 不能为空")
        @Size(max = 128, message = "知识文档 ID 过长") String documentId,
        @NotBlank(message = "学科不能为空")
        @Size(max = 128, message = "学科长度不能超过 128 个字符") String subject,
        @NotBlank(message = "年级不能为空")
        @Size(max = 128, message = "年级长度不能超过 128 个字符") String gradeLevel,
        @NotBlank(message = "课程版本不能为空")
        @Size(max = 128, message = "课程版本长度不能超过 128 个字符") String curriculumVersion,
        @Size(max = 255, message = "章节长度不能超过 255 个字符") String chapter,
        @Size(max = 4000, message = "学习目标不能超过 4000 个字符") String learningObjectives,
        @Size(max = 2000, message = "知识点标签不能超过 2000 个字符") String conceptTags,
        @Size(max = 2000, message = "前置知识标签不能超过 2000 个字符") String prerequisiteConcepts,
        @Min(value = 1, message = "难度必须在 1 到 5 之间")
        @Max(value = 5, message = "难度必须在 1 到 5 之间") Integer difficultyLevel,
        @Size(max = 64, message = "来源类型长度不能超过 64 个字符") String sourceType,
        @Size(max = 64, message = "编程语言长度不能超过 64 个字符") String programmingLanguage
) {

    /** 兼容尚未填写编程语言标签的旧调用方。 */
    public EducationSourceRequest(String documentId, String subject, String gradeLevel,
                                  String curriculumVersion, String chapter,
                                  String learningObjectives, String conceptTags,
                                  String prerequisiteConcepts, Integer difficultyLevel,
                                  String sourceType) {
        this(documentId, subject, gradeLevel, curriculumVersion, chapter, learningObjectives,
                conceptTags, prerequisiteConcepts, difficultyLevel, sourceType, null);
    }

    public int effectiveDifficultyLevel() {
        return difficultyLevel == null ? 3 : Math.max(1, Math.min(5, difficultyLevel));
    }
}
