package org.mingharness.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, String> {
    List<ConversationMessage> findByConversationIdOrderBySequenceAsc(String conversationId);
    Optional<ConversationMessage> findByRunIdAndRole(String runId, ConversationMessageRole role);
    Optional<ConversationMessage> findTopByConversationIdOrderBySequenceDesc(String conversationId);
    long countByConversationId(String conversationId);
}
