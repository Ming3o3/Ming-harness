package org.mingharness.conversation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.ConversationAttachmentView;
import org.mingharness.conversation.api.ConversationSummary;
import org.mingharness.conversation.api.CreateConversationRequest;
import org.mingharness.conversation.api.SendConversationMessageRequest;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 将拖入聊天框的文本文件导入受控工作区。浏览器不会上传或泄露本机绝对路径。
     */
    @PostMapping(path = "/{conversationId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<ConversationAttachmentView> upload(@PathVariable String conversationId,
                                                   @RequestPart("files") List<MultipartFile> files) {
        HarnessIdentity identity = identity();
        return conversationService.upload(conversationId, identity.tenantId(), identity.userId(), files);
    }

    /** 删除尚未发送的临时附件，已绑定到聊天记录的文件不能通过该接口撤回。 */
    @DeleteMapping("/{conversationId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardPendingAttachment(@PathVariable String conversationId, @PathVariable String attachmentId) {
        HarnessIdentity identity = identity();
        conversationService.discardPendingAttachment(conversationId, attachmentId,
                identity.tenantId(), identity.userId());
    }

    private HarnessIdentity identity() {
        return HarnessIdentityContext.require();
    }
}
