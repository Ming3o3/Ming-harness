package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
