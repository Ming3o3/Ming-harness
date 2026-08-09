package org.mingharness.education;

import jakarta.validation.Valid;
import org.mingharness.education.api.EducationSourceRequest;
import org.mingharness.education.api.EducationSourceView;
import org.mingharness.education.api.LearnerMasteryView;
import org.mingharness.education.api.LearnerProfileRequest;
import org.mingharness.education.api.LearnerProfileView;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 教育知识源和学习者状态接口；正文仍由 /api/context 管理。 */
@RestController
@RequestMapping("/api/education")
public class EducationController {

    private final EducationKnowledgeService knowledgeService;
    private final EducationLearnerService learnerService;

    public EducationController(EducationKnowledgeService knowledgeService,
                                EducationLearnerService learnerService) {
        this.knowledgeService = knowledgeService;
        this.learnerService = learnerService;
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationSourceView upsertSource(@Valid @RequestBody EducationSourceRequest request) {
        HarnessIdentity identity = identity();
        return EducationSourceView.from(knowledgeService.upsertSource(identity.tenantId(), identity.userId(), request));
    }

    @GetMapping("/sources")
    public List<EducationSourceView> listSources() {
        HarnessIdentity identity = identity();
        return knowledgeService.listSources(identity.tenantId(), identity.userId()).stream()
                .map(EducationSourceView::from).toList();
    }

    @DeleteMapping("/sources/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(@PathVariable String documentId) {
        HarnessIdentity identity = identity();
        knowledgeService.deleteSource(identity.tenantId(), identity.userId(), documentId);
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public LearnerProfileView upsertProfile(@Valid @RequestBody LearnerProfileRequest request) {
        HarnessIdentity identity = identity();
        return LearnerProfileView.from(learnerService.upsertProfile(identity.tenantId(), identity.userId(), request));
    }

    @GetMapping("/profiles")
    public List<LearnerProfileView> listProfiles() {
        HarnessIdentity identity = identity();
        return learnerService.listProfiles(identity.tenantId(), identity.userId()).stream()
                .map(LearnerProfileView::from).toList();
    }

    @GetMapping("/profiles/active")
    public LearnerProfileView activeProfile() {
        HarnessIdentity identity = identity();
        return LearnerProfileView.from(learnerService.activeProfile(identity.tenantId(), identity.userId()));
    }

    @PostMapping("/profiles/{profileId}/mastery")
    public LearnerMasteryView updateMastery(@PathVariable String profileId,
                                            @Valid @RequestBody MasteryUpdateRequest request) {
        HarnessIdentity identity = identity();
        return LearnerMasteryView.from(learnerService.updateMastery(identity.tenantId(), identity.userId(),
                profileId, request));
    }

    @GetMapping("/profiles/{profileId}/mastery")
    public List<LearnerMasteryView> listMastery(@PathVariable String profileId) {
        HarnessIdentity identity = identity();
        return learnerService.listMastery(identity.tenantId(), identity.userId(), profileId).stream()
                .map(LearnerMasteryView::from).toList();
    }

    private HarnessIdentity identity() {
        return HarnessIdentityContext.require();
    }
}
