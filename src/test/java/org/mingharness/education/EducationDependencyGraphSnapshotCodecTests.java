package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationDependencyGraphSnapshotCodecTests {

    @Test
    void shouldRoundTripBoundedDependencyGraphAndMasterySnapshot() {
        EducationDependencyGraph source = new EducationDependencyGraph("函数", List.of(
                new EducationDependencyPath("集合", 1, 0.2, 0.8),
                new EducationDependencyPath("定义域", 2, 0.4, 0.6)), true);

        EducationDependencyGraph decoded = EducationDependencyGraphSnapshotCodec.decode(
                EducationDependencyGraphSnapshotCodec.encode(source), "函数");

        assertEquals(source, decoded);
        assertTrue(decoded.truncated());
        assertEquals(0.8, decoded.prerequisites().get(0).deficit(), 0.000001);
    }

    @Test
    void shouldUseFrozenGraphFromEducationConfiguration() {
        EducationDependencyGraph graph = new EducationDependencyGraph("函数", List.of(
                new EducationDependencyPath("集合", 1, 0.1, 0.9)), false);
        EducationRunConfiguration configuration = new EducationRunConfiguration(
                true, "profile", "goal", null, null, null, null, null,
                "函数目标", 0.1, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.10", null, null, null, "FULL")
                .withDependencyGraphSnapshot(EducationDependencyGraphSnapshotCodec.encode(graph));

        assertEquals(graph, configuration.dependencyGraph());
        assertEquals(graph, configuration.retrievalFilter().dependencyGraphOrNull());
    }

    @Test
    void shouldDecodeLegacySnapshotsWithoutStateRiskFields() {
        EducationDependencyGraph decoded = EducationDependencyGraphSnapshotCodec.decode(
                "{\"targetConcept\":\"函数\",\"prerequisites\":["
                        + "{\"conceptKey\":\"集合\",\"depth\":1,"
                        + "\"masteryScore\":0.2,\"deficit\":0.8}],\"truncated\":false}",
                "函数");

        assertEquals(0.0, decoded.prerequisites().get(0).uncertainty());
        assertEquals(0.0, decoded.prerequisites().get(0).forgettingRisk());
        assertEquals(0.8, decoded.prerequisites().get(0).deficit());
    }

    @Test
    void shouldRoundTripStateRiskFieldsInNewSnapshots() {
        EducationDependencyGraph source = new EducationDependencyGraph("函数", List.of(
                new EducationDependencyPath("集合", 1, 0.2, 0.8, 0.45, 0.30)), false);

        EducationDependencyGraph decoded = EducationDependencyGraphSnapshotCodec.decode(
                EducationDependencyGraphSnapshotCodec.encode(source), "函数");

        assertEquals(0.45, decoded.prerequisites().get(0).uncertainty(), 0.000001);
        assertEquals(0.30, decoded.prerequisites().get(0).forgettingRisk(), 0.000001);
        assertEquals(source.prerequisites().get(0).statePriority(),
                decoded.prerequisites().get(0).statePriority(), 0.000001);
    }

    @Test
    void shouldHideFrozenGraphFromTheDependencyGraphAblationWhileKeepingMastery() {
        EducationDependencyGraph graph = new EducationDependencyGraph("函数", List.of(
                new EducationDependencyPath("集合", 1, 0.1, 0.9)), false);
        EducationRunConfiguration configuration = new EducationRunConfiguration(
                true, "profile", "goal", null, null, null, null, null,
                "函数目标", 0.1, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.10", null, null, null,
                "NO_DEPENDENCY_GRAPH")
                .withDependencyGraphSnapshot(EducationDependencyGraphSnapshotCodec.encode(graph));

        assertNull(configuration.retrievalFilter().dependencyGraphOrNull());
        assertEquals(0.1, configuration.retrievalFilter().masteryFor("函数"));
    }

    @Test
    void shouldFreezePointEstimateModeInTheRunRetrievalFilter() {
        EducationRunConfiguration configuration = new EducationRunConfiguration(
                true, "profile", "goal", null, null, null, null, null,
                "函数目标", 0.1, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.10", null, null, null,
                "FULL_POINT_ESTIMATE");

        assertFalse(configuration.retrievalFilter().uncertaintyAware());
        assertTrue(configuration.retrievalFilter().dependencyGraphOrNull() == null);
        assertTrue(configuration.retrievalStrategyValue().usesDependencyGraph());
    }
}
