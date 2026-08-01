package org.mingharness.conversation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.ConversationSummary;
import org.mingharness.conversation.api.CreateConversationRequest;
import org.mingharness.conversation.api.SendConversationMessageRequest;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 面向 Vue 聊天工作台的会话接口，身份和租户边界复用现有适配器。 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDetail create(@Valid @RequestBody(required = false) CreateConversationRequest request) {
        HarnessIdentity identity = identity();
        return conversationService.create(identity.tenantId(), identity.userId(), request);
    }

    @GetMapping
    public List<ConversationSummary> list() {
        HarnessIdentity identity = identity();
        return conversationService.list(identity.tenantId(), identity.userId());
    }

    @GetMapping("/{conversationId}")
    public ConversationDetail detail(@PathVariable String conversationId) {
        HarnessIdentity identity = identity();
        return conversationService.detail(conversationId, identity.tenantId(), identity.userId());
    }

    @PostMapping("/{conversationId}/messages")
    public ConversationDetail send(@PathVariable String conversationId,
                                   @Valid @RequestBody SendConversationMessageRequest request,
                                   HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        String permissions = identity.usesTrustedPermissions()
                ? identity.permissionsCsv() : httpRequest.getHeader("X-Permissions");
        return conversationService.send(conversationId, identity.tenantId(), identity.userId(), request,
                httpRequest.getHeader("Idempotency-Key"), permissions);
    }

    private HarnessIdentity identity() {
        return HarnessIdentityContext.require();
    }
}
