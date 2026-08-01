package org.mingharness.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ConversationAttachmentRepository extends JpaRepository<ConversationAttachment, String> {

    List<ConversationAttachment> findByIdIn(Collection<String> ids);

    List<ConversationAttachment> findByConversationIdAndMessageIdIsNotNullOrderByCreatedAtAsc(String conversationId);

    List<ConversationAttachment> findByMessageIdInOrderByCreatedAtAsc(Collection<String> messageIds);
}
