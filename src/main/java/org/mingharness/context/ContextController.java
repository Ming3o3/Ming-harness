package org.mingharness.context;

import jakarta.validation.Valid;
import org.mingharness.context.api.ContextBuilderResponse;
import org.mingharness.context.api.ContextReindexRequest;
import org.mingharness.context.api.ContextReindexResponse;
import org.mingharness.context.api.CreateDocumentRequest;
import org.mingharness.context.api.CreateMemoryRequest;
import org.mingharness.context.api.DocumentView;
import org.mingharness.context.api.MemoryView;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ContextIndexRebuildService indexRebuildService;

    public ContextController(ContextService contextService, ContextBuilder contextBuilder,
                             ContextIndexRebuildService indexRebuildService) {
        this.contextService = contextService;
        this.contextBuilder = contextBuilder;
        this.indexRebuildService = indexRebuildService;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {
        return DocumentView.from(contextService.createDocument(identity().tenantId(), identity().userId(), request));
    }

    @GetMapping("/documents")
    public List<DocumentView> listDocuments() {
        return contextService.listDocuments(identity().tenantId(), identity().userId()).stream().map(DocumentView::from).toList();
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable String documentId) {
        contextService.deleteDocument(identity().tenantId(), identity().userId(), documentId);
    }

    @PostMapping("/memories")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryView createMemory(
            @Valid @RequestBody CreateMemoryRequest request) {
        return MemoryView.from(contextService.createMemory(identity().tenantId(), identity().userId(), request));
    }

    @GetMapping("/memories")
    public List<MemoryView> listMemories() {
        return contextService.listMemories(identity().tenantId(), identity().userId()).stream().map(MemoryView::from).toList();
    }

    @DeleteMapping("/memories/{memoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMemory(@PathVariable String memoryId) {
        contextService.deleteMemory(identity().tenantId(), identity().userId(), memoryId);
    }

    @GetMapping("/preview")
    public ContextBuilderResponse preview(
            @RequestParam String query,
            @RequestParam(defaultValue = "4000") int maxChars) {
        var result = contextBuilder.build(identity().tenantId(), identity().userId(), query, Math.min(maxChars, 20_000));
        return new ContextBuilderResponse(result.text(), result.evidences());
    }

    @PostMapping("/reindex")
    public ContextReindexResponse reindex(
            @Valid @RequestBody(required = false) ContextReindexRequest request) {
        return indexRebuildService.rebuild(identity().tenantId(), request);
    }

    private HarnessIdentity identity() {
        return HarnessIdentityContext.require();
    }
}
