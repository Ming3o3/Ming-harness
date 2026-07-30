package org.mingharness.context;

import jakarta.validation.Valid;
import org.mingharness.context.api.ContextBuilderResponse;
import org.mingharness.context.api.CreateDocumentRequest;
import org.mingharness.context.api.CreateMemoryRequest;
import org.mingharness.context.api.DocumentView;
import org.mingharness.context.api.MemoryView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/context")
public class ContextController {

    private final ContextService contextService;
    private final ContextBuilder contextBuilder;

    public ContextController(ContextService contextService, ContextBuilder contextBuilder) {
        this.contextService = contextService;
        this.contextBuilder = contextBuilder;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView createDocument(
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
            @RequestHeader(name = "X-User-Id", defaultValue = "operator") String userId,
            @Valid @RequestBody CreateDocumentRequest request) {
        return DocumentView.from(contextService.createDocument(tenantId, userId, request));
    }

    @GetMapping("/documents")
    public List<DocumentView> listDocuments(
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
            @RequestHeader(name = "X-User-Id", defaultValue = "operator") String userId) {
        return contextService.listDocuments(tenantId, userId).stream().map(DocumentView::from).toList();
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            @PathVariable String documentId,
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
            @RequestHeader(name = "X-User-Id", defaultValue = "operator") String userId) {
        contextService.deleteDocument(tenantId, userId, documentId);
    }

    @PostMapping("/memories")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryView createMemory(
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
            @RequestHeader(name = "X-User-Id", defaultValue = "operator") String userId,
            @Valid @RequestBody CreateMemoryRequest request) {
        return MemoryView.from(contextService.createMemory(tenantId, userId, request));
    }

    @GetMapping("/memories")
    public List<MemoryView> listMemories(
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
            @RequestHeader(name = "X-User-Id", defaultValue = "operator") String userId) {
        return contextService.listMemories(tenantId, userId).stream().map(MemoryView::from).toList();
    }

    @DeleteMapping("/memories/{memoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMemory(
            @PathVariable String memoryId,
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
            @RequestHeader(name = "X-User-Id", defaultValue = "operator") String userId) {
        contextService.deleteMemory(tenantId, userId, memoryId);
    }

    @GetMapping("/preview")
    public ContextBuilderResponse preview(
            @RequestParam String query,
            @RequestParam(defaultValue = "4000") int maxChars,
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
            @RequestHeader(name = "X-User-Id", defaultValue = "operator") String userId) {
        var result = contextBuilder.build(tenantId, userId, query, Math.min(maxChars, 20_000));
        return new ContextBuilderResponse(result.text(), result.evidences());
    }
}
