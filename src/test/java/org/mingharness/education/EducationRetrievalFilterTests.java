package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationRetrievalFilterTests {

    @Test
    void shouldMatchConceptTagsAndMasterySnapshotWithoutCaseSensitivity() {
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "FUNCTIONS", null, null,
                Map.of("Functions", 0.35, "Sets", 0.10));
        EducationKnowledgeSource source = new EducationKnowledgeSource(
                "tenant-a", "doc-1", "数学", "高中一年级", "人教A版", "第一章",
                "理解函数", "functions,domain", "sets", 3, "TEXTBOOK");

        assertTrue(filter.matches(source));
        assertTrue(filter.matches(new EducationKnowledgeSource(
                "tenant-a", "doc-2", "数学", "高中一年级", "人教A版", "第一章",
                "理解函数", "FUNCTIONS,domain", "sets", 3, "TEXTBOOK")));
        assertEquals(0.35, filter.masteryFor("functions"));
        assertEquals(0.35, filter.masteryFor("FUNCTIONS"));
        assertEquals(0.10, filter.masteryFor("SETS"));
    }

    @Test
    void shouldTreatCommonChineseConceptDelimitersAsEquivalent() {
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "定义域", null, null);

        assertTrue(filter.matches(new EducationKnowledgeSource(
                "tenant-a", "doc-1", "数学", "高中一年级", "人教A版", "第一章",
                "理解定义域", "函数，定义域；值域", "集合\n不等式", 3, "TEXTBOOK")));
    }

    @Test
    void shouldMatchAConcreteCourseTagInsideAnAssignmentObjective() {
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "九年级", "人教版", "理解二次函数的概念及一般形式", null, null);

        assertTrue(filter.matches(new EducationKnowledgeSource(
                "tenant-a", "doc-1", "数学", "九年级", "人教版", "第二章",
                "理解二次函数", "二次函数,二次函数图像", "一次函数", 3, "TEXTBOOK")));
    }

    @Test
    void shouldExpandRetrievalConceptsToFrozenPrerequisiteGraphWithoutWeakeningCourseGate() {
        EducationDependencyGraph graph = new EducationDependencyGraph("函数", List.of(
                new EducationDependencyPath("集合", 1, 0.1, 0.9)), false);
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null,
                Map.of("函数", 0.2, "集合", 0.1), graph);
        EducationKnowledgeSource prerequisite = new EducationKnowledgeSource(
                "tenant-a", "doc-1", "数学", "高中一年级", "人教A版", "第一章",
                "集合基础", "集合", "", 2, "TEXTBOOK");
        EducationKnowledgeSource wrongCourse = new EducationKnowledgeSource(
                "tenant-a", "doc-2", "数学", "高中二年级", "北师大版", "第一章",
                "集合基础", "集合", "", 2, "TEXTBOOK");

        assertTrue(filter.matchesForRetrieval(prerequisite));
        assertTrue(!filter.matchesForRetrieval(wrongCourse));
        assertTrue(filter.matches(prerequisite) == false);
    }

    @Test
    void shouldUseConservativeMasteryFromFrozenEvidenceForRetrieval() {
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null,
                Map.of("函数", 0.8), null,
                Map.of("函数", new LearnerStateEvidence(0.8, 1, 1)));

        assertTrue(filter.conservativeMasteryFor("函数") < 0.8);
        assertTrue(filter.uncertaintyFor("函数") > 0.0);
    }

    @Test
    void shouldUseProgrammingLanguageAsAnOptionalHardRetrievalConstraint() {
        EducationRetrievalFilter pythonFilter = new EducationRetrievalFilter(
                "计算机", "大学一年级", "Python基础", "函数", "python", null, null,
                Map.of(), null, Map.of());

        EducationKnowledgeSource python = new EducationKnowledgeSource(
                "tenant-a", "doc-python", "计算机", "大学一年级", "Python基础", "函数",
                "理解函数", "函数", "变量", 3, "CODE_EXAMPLE", "Python");
        EducationKnowledgeSource java = new EducationKnowledgeSource(
                "tenant-a", "doc-java", "计算机", "大学一年级", "Python基础", "函数",
                "理解函数", "函数", "变量", 3, "CODE_EXAMPLE", "Java");

        assertTrue(pythonFilter.matchesForRetrieval(python));
        assertTrue(!pythonFilter.matchesForRetrieval(java));
        assertEquals("PYTHON", python.getProgrammingLanguage());
    }
}
