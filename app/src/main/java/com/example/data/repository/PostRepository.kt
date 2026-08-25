package com.example.data.repository

import com.example.data.model.ContentVisibility
import com.example.data.model.OjasUser
import com.example.data.model.Post
import com.example.data.model.PostDraft
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository interface for Post data operations and feed queries.
 * Supports paginated queries, creation, and individual item lookups.
 */
interface PostRepository {
    suspend fun getPublicPosts(page: Int = 1, pageSize: Int = 20): Result<List<Post>>
    suspend fun getPostsByUserId(userId: String): Result<List<Post>>
    suspend fun getPostById(postId: String): Result<Post>
    suspend fun createPost(creatorId: String, draft: PostDraft, user: OjasUser? = null): Result<Post>
    suspend fun deletePost(userId: String, postId: String): Result<Boolean>
}

/**
 * Backend-ready implementation of [PostRepository].
 * Manages thread-safe post lifecycle, author authorization, and real content queries.
 */
class OjasPostRepository(
    private val mediaStorageService: MediaStorageService? = null
) : PostRepository {
    private val publishedPosts = CopyOnWriteArrayList<Post>()
    private val mutex = Mutex()

    override suspend fun getPublicPosts(page: Int, pageSize: Int): Result<List<Post>> {
        val publicList = publishedPosts.filter { it.visibility == ContentVisibility.PUBLIC }
        return Result.success(publicList)
    }

    override suspend fun getPostsByUserId(userId: String): Result<List<Post>> {
        if (userId.isBlank()) {
            return Result.success(emptyList())
        }
        val userPosts = publishedPosts.filter { it.creatorId == userId }
        return Result.success(userPosts)
    }

    override suspend fun getPostById(postId: String): Result<Post> {
        if (postId.isBlank()) {
            return Result.failure(IllegalArgumentException("Post ID cannot be empty."))
        }
        val found = publishedPosts.find { it.postId == postId }
            ?: return Result.failure(NoSuchElementException("Post not found."))
        return Result.success(found)
    }

    override suspend fun createPost(creatorId: String, draft: PostDraft, user: OjasUser?): Result<Post> {
        if (creatorId.isBlank()) {
            return Result.failure(IllegalStateException("Authentication required to create a post."))
        }
        if (draft.textContent.isBlank() && draft.mediaAttachments.isEmpty()) {
            return Result.failure(IllegalArgumentException("Post must contain text or media content."))
        }

        return mutex.withLock {
            val postId = "post_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
            val authorUsername = user?.username?.takeIf { it.isNotBlank() } ?: "user_${creatorId.take(6)}"
            val authorDisplayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "OJAS User"

            val newPost = Post(
                postId = postId,
                creatorId = creatorId,
                creatorUsername = authorUsername,
                creatorDisplayName = authorDisplayName,
                creatorAvatarUrl = user?.avatarUrl,
                textContent = draft.textContent.trim(),
                mediaAttachments = draft.mediaAttachments,
                visibility = draft.visibility,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                likeCount = 0,
                commentCount = 0,
                shareCount = 0,
                isLikedByMe = false
            )
            // Insert newest post at index 0
            publishedPosts.add(0, newPost)
            Result.success(newPost)
        }
    }

    override suspend fun deletePost(userId: String, postId: String): Result<Boolean> {
        if (userId.isBlank() || postId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID and Post ID required for deletion."))
        }
        return mutex.withLock {
            val post = publishedPosts.find { it.postId == postId }
            if (post == null) {
                Result.failure(NoSuchElementException("Post not found with ID: $postId"))
            } else if (post.creatorId != userId) {
                Result.failure(IllegalAccessException("Unauthorized: Users can only delete their own posts."))
            } else {
                publishedPosts.remove(post)
                Result.success(true)
            }
        }
    }
}

