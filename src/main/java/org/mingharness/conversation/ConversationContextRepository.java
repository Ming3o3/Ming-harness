package org.mingharness.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationContextRepository extends JpaRepository<ConversationContext, String> {
}
