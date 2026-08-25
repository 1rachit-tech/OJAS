package com.example.data.model

/**
 * Message read status.
 */
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

/**
 * Single direct message model foundation.
 */
data class MessageRecord(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val senderDisplayName: String,
    val text: String,
    val mediaAttachment: MediaAttachment? = null,
    val status: MessageStatus = MessageStatus.SENT,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Conversation / thread model foundation.
 */
data class ConversationRecord(
    val conversationId: String,
    val participantIds: List<String>,
    val participantProfiles: List<OjasUser> = emptyList(),
    val lastMessage: MessageRecord? = null,
    val unreadCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
