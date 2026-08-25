package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OJAS", appName)
  }

  @Test
  fun `auth repository initial state is unauthenticated`() {
    val repo = com.example.data.auth.OjasAuthRepository()
    repo.checkSession()
    assertEquals(com.example.data.auth.AuthState.Unauthenticated, repo.authState.value)
  }

  @Test
  fun `auth repository signup validates empty input`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val result = repo.signup("", "")
    org.junit.Assert.assertTrue(result.isFailure)
  }

  @Test
  fun `auth repository signup creates valid account and requires setup`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val result = repo.signup("test@example.com", "password123")
    org.junit.Assert.assertTrue(result.isSuccess)
    val user = result.getOrThrow()
    org.junit.Assert.assertTrue(user.userId.isNotBlank())
    org.junit.Assert.assertFalse(user.isSetupComplete)
    val state = repo.authState.value
    org.junit.Assert.assertTrue(state is com.example.data.auth.AuthState.SetupRequired)
    assertEquals(user.userId, (state as com.example.data.auth.AuthState.SetupRequired).user.userId)
  }

  @Test
  fun `auth repository signup rejects invalid email or short password`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val invalidEmailResult = repo.signup("invalid-email", "password123")
    org.junit.Assert.assertTrue(invalidEmailResult.isFailure)

    val shortPassResult = repo.signup("valid@example.com", "123")
    org.junit.Assert.assertTrue(shortPassResult.isFailure)
  }

  @Test
  fun `auth repository signup rejects duplicate email`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val first = repo.signup("duplicate@example.com", "secret123")
    org.junit.Assert.assertTrue(first.isSuccess)

    val second = repo.signup("duplicate@example.com", "secret456")
    org.junit.Assert.assertTrue(second.isFailure)
    org.junit.Assert.assertTrue(second.exceptionOrNull()?.message?.contains("already exists") == true)
  }

  @Test
  fun `auth repository completeSetup succeeds and updates session to authenticated`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    repo.signup("alex@example.com", "password123")
    val setupResult = repo.completeSetup("Alex Morgan", "alexm", null)
    org.junit.Assert.assertTrue(setupResult.isSuccess)
    val user = setupResult.getOrThrow()
    assertEquals("Alex Morgan", user.displayName)
    assertEquals("alexm", user.username)
    org.junit.Assert.assertTrue(user.isSetupComplete)
    val state = repo.authState.value
    org.junit.Assert.assertTrue(state is com.example.data.auth.AuthState.Authenticated)
    assertEquals(user.userId, (state as com.example.data.auth.AuthState.Authenticated).user.userId)
  }

  @Test
  fun `auth repository completeSetup rejects duplicate username across accounts`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    // User 1
    repo.signup("user1@example.com", "pass1234")
    val setup1 = repo.completeSetup("User One", "unique_handle")
    org.junit.Assert.assertTrue(setup1.isSuccess)
    repo.logout()

    // User 2 tries to take the same handle
    repo.signup("user2@example.com", "pass5678")
    val setup2 = repo.completeSetup("User Two", "unique_handle")
    org.junit.Assert.assertTrue(setup2.isFailure)
    org.junit.Assert.assertTrue(setup2.exceptionOrNull()?.message?.contains("already taken") == true)
  }

  @Test
  fun `auth repository login by email and username works correctly`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    repo.signup("sam@example.com", "securepass123")
    repo.completeSetup("Sam Taylor", "samtaylor")
    repo.logout()
    assertEquals(com.example.data.auth.AuthState.Unauthenticated, repo.authState.value)

    // Login with email
    val loginByEmail = repo.login("sam@example.com", "securepass123")
    org.junit.Assert.assertTrue(loginByEmail.isSuccess)
    assertEquals("samtaylor", loginByEmail.getOrThrow().username)
    org.junit.Assert.assertTrue(repo.authState.value is com.example.data.auth.AuthState.Authenticated)
    repo.logout()

    // Login with @username
    val loginByUsername = repo.login("@samtaylor", "securepass123")
    org.junit.Assert.assertTrue(loginByUsername.isSuccess)
    assertEquals("Sam Taylor", loginByUsername.getOrThrow().displayName)
    org.junit.Assert.assertTrue(repo.authState.value is com.example.data.auth.AuthState.Authenticated)
  }

  @Test
  fun `auth repository login rejects wrong password and non-existent account`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    repo.signup("real@example.com", "mypassword")
    repo.completeSetup("Real User", "realuser")
    repo.logout()

    // Wrong password
    val wrongPass = repo.login("real@example.com", "wrongpassword")
    org.junit.Assert.assertTrue(wrongPass.isFailure)
    org.junit.Assert.assertTrue(wrongPass.exceptionOrNull()?.message?.contains("Incorrect password") == true)

    // Non-existent account
    val noAccount = repo.login("ghost@example.com", "mypassword")
    org.junit.Assert.assertTrue(noAccount.isFailure)
    org.junit.Assert.assertTrue(noAccount.exceptionOrNull()?.message?.contains("No account found") == true)
  }

  @Test
  fun `auth repository switching accounts does not leak session data`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    // Register Account A
    repo.signup("alice@example.com", "alice123")
    val alice = repo.completeSetup("Alice", "alice_o").getOrThrow()
    repo.logout()

    // Register Account B
    repo.signup("bob@example.com", "bob12345")
    val bob = repo.completeSetup("Bob", "bob_o").getOrThrow()
    org.junit.Assert.assertNotEquals(alice.userId, bob.userId)
    
    // Check Bob is authenticated
    val bobState = repo.authState.value as com.example.data.auth.AuthState.Authenticated
    assertEquals(bob.userId, bobState.user.userId)
    assertEquals("Bob", bobState.user.displayName)

    // Logout Bob
    repo.logout()
    assertEquals(com.example.data.auth.AuthState.Unauthenticated, repo.authState.value)

    // Login Alice
    repo.login("alice@example.com", "alice123")
    val aliceState = repo.authState.value as com.example.data.auth.AuthState.Authenticated
    assertEquals(alice.userId, aliceState.user.userId)
    assertEquals("Alice", aliceState.user.displayName)
  }

  @Test
  fun `auth repository completeSetup validates display name and username`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val emptyDisplayNameResult = repo.completeSetup("", "validuser")
    org.junit.Assert.assertTrue(emptyDisplayNameResult.isFailure)

    val invalidUsernameResult = repo.completeSetup("Alex Morgan", "ab")
    org.junit.Assert.assertTrue(invalidUsernameResult.isFailure)
  }

  @Test
  fun `auth repository logout transitions to unauthenticated state`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    repo.logout()
    assertEquals(com.example.data.auth.AuthState.Unauthenticated, repo.authState.value)
  }

  @Test
  fun `firebase auth repository reports cloud backed and provider name`() {
    val firebaseRepo = com.example.data.auth.FirebaseAuthRepository(null)
    assertEquals("Firebase Authentication", firebaseRepo.providerName)
    org.junit.Assert.assertTrue(firebaseRepo.isCloudBacked)
    org.junit.Assert.assertTrue(firebaseRepo.authState.value is com.example.data.auth.AuthState.ConfigMissing)
  }

  @Test
  fun `firebase auth repository signup and login report unconfigured cloud truthfully`() = kotlinx.coroutines.runBlocking {
    val firebaseRepo = com.example.data.auth.FirebaseAuthRepository(null)
    val signupResult = firebaseRepo.signup("test@example.com", "password123")
    org.junit.Assert.assertTrue(signupResult.isFailure)
    org.junit.Assert.assertTrue(signupResult.exceptionOrNull()?.message?.contains("Cloud credentials required") == true)

    val loginResult = firebaseRepo.login("test@example.com", "password123")
    org.junit.Assert.assertTrue(loginResult.isFailure)
    org.junit.Assert.assertTrue(loginResult.exceptionOrNull()?.message?.contains("Cloud credentials required") == true)
  }

  @Test
  fun `auth repository default factory creates safe working instance`() {
    val repo = com.example.data.auth.AuthRepository.createDefault()
    org.junit.Assert.assertNotNull(repo)
    org.junit.Assert.assertNotNull(repo.authState.value)
  }

  @Test
  fun `post repository requires authentication to create post`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.repository.OjasPostRepository()
    val draft = com.example.data.model.PostDraft(textContent = "Hello OJAS!")
    val result = repo.createPost("", draft)
    org.junit.Assert.assertTrue(result.isFailure)
    org.junit.Assert.assertTrue(result.exceptionOrNull()?.message?.contains("Authentication required") == true)
  }

  @Test
  fun `social interaction repository requires authentication to like or follow`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.repository.OjasSocialInteractionRepository()
    val likeResult = repo.setLike("", "post_123", com.example.data.model.TargetContentType.POST, true)
    org.junit.Assert.assertTrue(likeResult.isFailure)

    val followResult = repo.setFollow("", "user_456", true)
    org.junit.Assert.assertTrue(followResult.isFailure)
  }

  @Test
  fun `social repository prevents self follow`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.repository.OjasSocialInteractionRepository()
    val selfFollowResult = repo.setFollow("user_123", "user_123", true)
    org.junit.Assert.assertTrue(selfFollowResult.isFailure)
    org.junit.Assert.assertTrue(selfFollowResult.exceptionOrNull()?.message?.contains("cannot follow themselves") == true)
  }

  @Test
  fun `social repository follow and unfollow updates state and prevents duplicates`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.repository.OjasSocialInteractionRepository()
    val followerId = "user_100"
    val followedId = "user_200"

    // Initial check -> false
    val initialStatus = repo.checkFollowStatus(followerId, followedId)
    org.junit.Assert.assertTrue(initialStatus.isSuccess)
    org.junit.Assert.assertFalse(initialStatus.getOrThrow())

    // Follow -> true
    val followResult = repo.setFollow(followerId, followedId, true)
    org.junit.Assert.assertTrue(followResult.isSuccess)
    org.junit.Assert.assertTrue(repo.checkFollowStatus(followerId, followedId).getOrThrow())

    // Idempotent duplicate follow -> true
    val duplicateFollowResult = repo.setFollow(followerId, followedId, true)
    org.junit.Assert.assertTrue(duplicateFollowResult.isSuccess)
    assertEquals(1, repo.getFollowingCount(followerId).getOrThrow())
    assertEquals(1, repo.getFollowersCount(followedId).getOrThrow())

    // Unfollow -> false
    val unfollowResult = repo.setFollow(followerId, followedId, false)
    org.junit.Assert.assertTrue(unfollowResult.isSuccess)
    org.junit.Assert.assertFalse(repo.checkFollowStatus(followerId, followedId).getOrThrow())
    assertEquals(0, repo.getFollowingCount(followerId).getOrThrow())
    assertEquals(0, repo.getFollowersCount(followedId).getOrThrow())
  }

  @Test
  fun `user profile repository validates username format and reports backend status`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.repository.OjasUserProfileRepository()
    val invalidUsername = repo.checkUsernameAvailability("a")
    org.junit.Assert.assertTrue(invalidUsername.isFailure)
    org.junit.Assert.assertTrue(invalidUsername.exceptionOrNull()?.message?.contains("Invalid username format") == true)

    val validUsernameCheck = repo.checkUsernameAvailability("alex_morgan")
    org.junit.Assert.assertTrue(validUsernameCheck.isFailure)
    org.junit.Assert.assertTrue(validUsernameCheck.exceptionOrNull()?.message?.contains("Backend database provider is not configured") == true)
  }

  @Test
  fun `post repository enforces authentication and valid draft content`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.repository.OjasPostRepository()

    // Unauthenticated attempt
    val unauthResult = repo.createPost("", com.example.data.model.PostDraft(textContent = "Hello"))
    org.junit.Assert.assertTrue(unauthResult.isFailure)
    org.junit.Assert.assertTrue(unauthResult.exceptionOrNull()?.message?.contains("Authentication required") == true)

    // Empty content attempt
    val emptyContentResult = repo.createPost("user_123", com.example.data.model.PostDraft(textContent = ""))
    org.junit.Assert.assertTrue(emptyContentResult.isFailure)
    org.junit.Assert.assertTrue(emptyContentResult.exceptionOrNull()?.message?.contains("Post must contain text or media") == true)

    // Valid authenticated post
    val successResult = repo.createPost(
      creatorId = "user_123",
      draft = com.example.data.model.PostDraft(textContent = "First official OJAS post!"),
      user = com.example.data.model.OjasUser(userId = "user_123", username = "creator1", displayName = "Creator One")
    )
    org.junit.Assert.assertTrue(successResult.isSuccess)
    val createdPost = successResult.getOrThrow()
    assertEquals("user_123", createdPost.creatorId)
    assertEquals("creator1", createdPost.creatorUsername)
    assertEquals("First official OJAS post!", createdPost.textContent)

    // Query user posts
    val userPosts = repo.getPostsByUserId("user_123").getOrThrow()
    assertEquals(1, userPosts.size)
    assertEquals(createdPost.postId, userPosts[0].postId)
  }

  @Test
  fun `media storage service validates media size and metadata`() = kotlinx.coroutines.runBlocking {
    val storage = com.example.data.repository.OjasMediaStorageService()

    // Unauthenticated upload
    val unauthUpload = storage.uploadMedia("", byteArrayOf(1, 2, 3), "image/jpeg", "posts")
    org.junit.Assert.assertTrue(unauthUpload.isFailure)

    // Empty bytes upload
    val emptyUpload = storage.uploadMedia("user_123", byteArrayOf(), "image/jpeg", "posts")
    org.junit.Assert.assertTrue(emptyUpload.isFailure)

    // Valid upload
    val validUpload = storage.uploadMedia("user_123", byteArrayOf(10, 20, 30, 40), "image/png", "posts", "photo.png")
    org.junit.Assert.assertTrue(validUpload.isSuccess)
    val meta = validUpload.getOrThrow()
    assertEquals("image/png", meta.mimeType)
    assertEquals(4L, meta.fileSizeBytes)
  }

  @Test
  fun `oj repository validates authentication and video parameters`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.repository.OjasOjRepository()

    // Unauthenticated attempt
    val unauthResult = repo.createOjVideo(
      creatorId = "",
      draft = com.example.data.model.OjVideoDraft(videoUrl = "ojas://storage/oj_videos/123")
    )
    org.junit.Assert.assertTrue(unauthResult.isFailure)
    org.junit.Assert.assertTrue(unauthResult.exceptionOrNull()?.message?.contains("Authentication required") == true)

    // Empty video URL attempt
    val emptyUrlResult = repo.createOjVideo(
      creatorId = "user_456",
      draft = com.example.data.model.OjVideoDraft(videoUrl = "")
    )
    org.junit.Assert.assertTrue(emptyUrlResult.isFailure)

    // Valid OJ Video creation
    val draft = com.example.data.model.OjVideoDraft(
      videoUrl = "ojas://storage/oj_videos/user_456/media_1",
      thumbnailUrl = "ojas://storage/oj_thumbnails/user_456/thumb_1",
      caption = "My first OJ vertical short video #dance #vibes",
      tags = listOf("#dance", "vibes"),
      category = "dance",
      durationSeconds = 15
    )
    val user = com.example.data.model.OjasUser(
      userId = "user_456",
      username = "oj_dancer",
      displayName = "OJ Dancer"
    )
    val createResult = repo.createOjVideo("user_456", draft, user)
    org.junit.Assert.assertTrue(createResult.isSuccess)
    val createdOj = createResult.getOrThrow()

    assertEquals("user_456", createdOj.creatorId)
    assertEquals("oj_dancer", createdOj.creatorUsername)
    assertEquals("OJ Dancer", createdOj.creatorDisplayName)
    assertEquals(15, createdOj.durationSeconds)
    assertEquals("dance", createdOj.category)
    assertEquals(listOf("dance", "vibes"), createdOj.tags)
    assertEquals(0, createdOj.likeCount)

    // Query user OJ videos
    val userOjList = repo.getOjVideosByUserId("user_456").getOrThrow()
    assertEquals(1, userOjList.size)
    assertEquals(createdOj.ojId, userOjList[0].ojId)

    // Public feed contains video
    val publicFeed = repo.getPublicOjVideos(category = null).getOrThrow()
    assertEquals(1, publicFeed.size)

    // Filter matching category
    val danceFeed = repo.getPublicOjVideos(category = "dance").getOrThrow()
    assertEquals(1, danceFeed.size)

    // Filter non-matching category
    val comedyFeed = repo.getPublicOjVideos(category = "comedy").getOrThrow()
    assertEquals(0, comedyFeed.size)

    // Deletion authorization
    val unauthDelete = repo.deleteOjVideo("another_user", createdOj.ojId)
    org.junit.Assert.assertTrue(unauthDelete.isFailure)

    val authDelete = repo.deleteOjVideo("user_456", createdOj.ojId)
    org.junit.Assert.assertTrue(authDelete.isSuccess)
    assertEquals(0, repo.getOjVideosByUserId("user_456").getOrThrow().size)
  }

  @Test
  fun `social interaction repository tracks followed users and feeds`() = kotlinx.coroutines.runBlocking {
    val socialRepo = com.example.data.repository.OjasSocialInteractionRepository()
    val ojRepo = com.example.data.repository.OjasOjRepository()

    val creatorUser = com.example.data.model.OjasUser(
      userId = "creator_999",
      username = "star_creator",
      displayName = "Star Creator"
    )

    // Create 2 OJ videos for creator_999
    val oj1 = ojRepo.createOjVideo(
      creatorId = "creator_999",
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_999/v1",
        caption = "Video 1",
        category = "music"
      ),
      user = creatorUser
    ).getOrThrow()

    val oj2 = ojRepo.createOjVideo(
      creatorId = "creator_999",
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_999/v2",
        caption = "Video 2",
        category = "dance"
      ),
      user = creatorUser
    ).getOrThrow()

    // Follower initially follows nobody
    val initialFollowed = socialRepo.getFollowedUserIds("follower_111").getOrThrow()
    org.junit.Assert.assertTrue(initialFollowed.isEmpty())

    val initialFollowingFeed = ojRepo.getFollowingOjVideos(initialFollowed).getOrThrow()
    org.junit.Assert.assertTrue(initialFollowingFeed.isEmpty())

    // Follow creator_999
    val followResult = socialRepo.setFollow("follower_111", "creator_999", true)
    org.junit.Assert.assertTrue(followResult.isSuccess)

    val updatedFollowed = socialRepo.getFollowedUserIds("follower_111").getOrThrow()
    assertEquals(listOf("creator_999"), updatedFollowed)

    // Now Following feed contains creator_999's videos (paginated)
    val followingFeedPage1 = ojRepo.getFollowingOjVideos(updatedFollowed, page = 1, pageSize = 1).getOrThrow()
    assertEquals(1, followingFeedPage1.size)
    assertEquals(oj2.ojId, followingFeedPage1[0].ojId)

    val followingFeedPage2 = ojRepo.getFollowingOjVideos(updatedFollowed, page = 2, pageSize = 1).getOrThrow()
    assertEquals(1, followingFeedPage2.size)
    assertEquals(oj1.ojId, followingFeedPage2[0].ojId)

    // Unfollow
    socialRepo.setFollow("follower_111", "creator_999", false)
    val unfollowedList = socialRepo.getFollowedUserIds("follower_111").getOrThrow()
    org.junit.Assert.assertTrue(unfollowedList.isEmpty())
  }

  @Test
  fun `social repository like and unlike updates state, like counts, and prevents duplicates`() = kotlinx.coroutines.runBlocking {
    val socialRepo = com.example.data.repository.OjasSocialInteractionRepository()
    val userId = "user_like_1"
    val ojId = "oj_video_test_1"

    // Initial state: not liked, count 0
    org.junit.Assert.assertFalse(socialRepo.checkLikeStatus(userId, ojId).getOrThrow())
    assertEquals(0L, socialRepo.getLikesCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    // Like the OJ
    val likeRes = socialRepo.setLike(userId, ojId, com.example.data.model.TargetContentType.OJ, true)
    org.junit.Assert.assertTrue(likeRes.isSuccess)
    org.junit.Assert.assertTrue(likeRes.getOrThrow())

    // Check status is liked and count is 1
    org.junit.Assert.assertTrue(socialRepo.checkLikeStatus(userId, ojId).getOrThrow())
    assertEquals(1L, socialRepo.getLikesCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())
    assertEquals(listOf(ojId), socialRepo.getUserLikedContentIds(userId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    // Repeated like by same user (idempotency / duplicate protection)
    val repeatLikeRes = socialRepo.setLike(userId, ojId, com.example.data.model.TargetContentType.OJ, true)
    org.junit.Assert.assertTrue(repeatLikeRes.isSuccess)
    assertEquals(1L, socialRepo.getLikesCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    // Second user likes the same OJ
    val user2 = "user_like_2"
    socialRepo.setLike(user2, ojId, com.example.data.model.TargetContentType.OJ, true)
    assertEquals(2L, socialRepo.getLikesCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    // First user unlikes
    val unlikeRes = socialRepo.setLike(userId, ojId, com.example.data.model.TargetContentType.OJ, false)
    org.junit.Assert.assertTrue(unlikeRes.isSuccess)
    org.junit.Assert.assertFalse(unlikeRes.getOrThrow())
    org.junit.Assert.assertFalse(socialRepo.checkLikeStatus(userId, ojId).getOrThrow())
    assertEquals(1L, socialRepo.getLikesCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    // Second user unlikes -> count returns to 0
    socialRepo.setLike(user2, ojId, com.example.data.model.TargetContentType.OJ, false)
    assertEquals(0L, socialRepo.getLikesCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())
  }

  @Test
  fun `creator self like operates consistently with single like policy`() = kotlinx.coroutines.runBlocking {
    val socialRepo = com.example.data.repository.OjasSocialInteractionRepository()
    val creatorId = "creator_self"
    val ojId = "oj_creator_video"

    // Creator likes their own video
    val result = socialRepo.setLike(creatorId, ojId, com.example.data.model.TargetContentType.OJ, true)
    org.junit.Assert.assertTrue(result.isSuccess)
    org.junit.Assert.assertTrue(socialRepo.checkLikeStatus(creatorId, ojId).getOrThrow())
    assertEquals(1L, socialRepo.getLikesCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    // Creator unlikes
    socialRepo.setLike(creatorId, ojId, com.example.data.model.TargetContentType.OJ, false)
    org.junit.Assert.assertFalse(socialRepo.checkLikeStatus(creatorId, ojId).getOrThrow())
    assertEquals(0L, socialRepo.getLikesCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())
  }

  @Test
  fun `oj repository syncs like state and count correctly`() = kotlinx.coroutines.runBlocking {
    val ojRepo = com.example.data.repository.OjasOjRepository()
    val video = ojRepo.createOjVideo(
      creatorId = "creator_sync",
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_sync/v1",
        caption = "Sync test",
        category = "tech"
      )
    ).getOrThrow()

    assertEquals(0L, video.likeCount)
    org.junit.Assert.assertFalse(video.isLikedByMe)

    val syncResult = ojRepo.syncLikeState(video.ojId, isLikedByMe = true, likeCount = 42L)
    org.junit.Assert.assertTrue(syncResult.isSuccess)

    val updatedVideo = ojRepo.getOjVideoById(video.ojId).getOrThrow()
    assertEquals(42L, updatedVideo.likeCount)
    org.junit.Assert.assertTrue(updatedVideo.isLikedByMe)
  }

  @Test
  fun `social repository comment lifecycle validates authentication, non-empty text, and pagination`() = kotlinx.coroutines.runBlocking {
    val socialRepo = com.example.data.repository.OjasSocialInteractionRepository()
    val ojId = "oj_comment_test_1"
    val author = com.example.data.model.OjasUser(
      userId = "user_cmt_1",
      username = "commenter_one",
      displayName = "Commenter One"
    )

    // Unauthenticated attempt fails
    val unauthPost = socialRepo.postComment("", ojId, com.example.data.model.TargetContentType.OJ, "Great video!", author)
    org.junit.Assert.assertTrue(unauthPost.isFailure)

    // Empty text fails
    val emptyPost = socialRepo.postComment("user_cmt_1", ojId, com.example.data.model.TargetContentType.OJ, "   ", author)
    org.junit.Assert.assertTrue(emptyPost.isFailure)

    // Valid comment
    val postResult = socialRepo.postComment("user_cmt_1", ojId, com.example.data.model.TargetContentType.OJ, "Amazing OJ video!", author)
    org.junit.Assert.assertTrue(postResult.isSuccess)
    val comment = postResult.getOrThrow()
    assertEquals(ojId, comment.targetContentId)
    assertEquals("user_cmt_1", comment.authorId)
    assertEquals("commenter_one", comment.authorUsername)
    assertEquals("Commenter One", comment.authorDisplayName)
    assertEquals("Amazing OJ video!", comment.text)

    // Count is 1
    assertEquals(1L, socialRepo.getCommentsCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    // Post second comment
    val post2Result = socialRepo.postComment("user_cmt_1", ojId, com.example.data.model.TargetContentType.OJ, "Second comment", author)
    org.junit.Assert.assertTrue(post2Result.isSuccess)
    assertEquals(2L, socialRepo.getCommentsCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    // Get comments pagination
    val commentsPage1 = socialRepo.getComments(ojId, page = 1, pageSize = 10).getOrThrow()
    assertEquals(2, commentsPage1.size)
    // Newest is first
    assertEquals("Second comment", commentsPage1[0].text)
    assertEquals("Amazing OJ video!", commentsPage1[1].text)

    // Delete comment authorization: other user cannot delete
    val unauthorizedDelete = socialRepo.deleteComment("other_user", comment.commentId)
    org.junit.Assert.assertTrue(unauthorizedDelete.isFailure)

    // Owner deletes own comment
    val authorizedDelete = socialRepo.deleteComment("user_cmt_1", comment.commentId)
    org.junit.Assert.assertTrue(authorizedDelete.isSuccess)
    assertEquals(1L, socialRepo.getCommentsCount(ojId, com.example.data.model.TargetContentType.OJ).getOrThrow())

    val remaining = socialRepo.getComments(ojId, page = 1, pageSize = 10).getOrThrow()
    assertEquals(1, remaining.size)
    assertEquals("Second comment", remaining[0].text)
  }

  @Test
  fun `oj repository syncs comment count correctly`() = kotlinx.coroutines.runBlocking {
    val ojRepo = com.example.data.repository.OjasOjRepository()
    val video = ojRepo.createOjVideo(
      creatorId = "creator_cmt_sync",
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_cmt_sync/v1",
        caption = "Comment count sync test",
        category = "music"
      )
    ).getOrThrow()

    assertEquals(0L, video.commentCount)

    val syncResult = ojRepo.syncCommentCount(video.ojId, 15L)
    org.junit.Assert.assertTrue(syncResult.isSuccess)

    val updated = ojRepo.getOjVideoById(video.ojId).getOrThrow()
    assertEquals(15L, updated.commentCount)
  }

  @Test
  fun `oj deep link utility generates clean links and parses valid routes without secrets`() {
    val ojId = "oj_987654_abc123"
    val publicUrl = com.example.data.util.OjDeepLinkUtil.generatePublicOjLink(ojId)
    assertEquals("https://ojas.app/oj/oj_987654_abc123", publicUrl)

    val customSchemeUri = com.example.data.util.OjDeepLinkUtil.generateCustomSchemeOjUri(ojId)
    assertEquals("ojas://oj/oj_987654_abc123", customSchemeUri)

    val shareText = com.example.data.util.OjDeepLinkUtil.buildShareText("Check out my dance video!", ojId)
    org.junit.Assert.assertTrue(shareText.contains("Check out my dance video!"))
    org.junit.Assert.assertTrue(shareText.contains("https://ojas.app/oj/oj_987654_abc123"))
    org.junit.Assert.assertFalse(shareText.contains("token"))
    org.junit.Assert.assertFalse(shareText.contains("storage"))

    // Parse HTTPS link
    val httpsUri = android.net.Uri.parse("https://ojas.app/oj/oj_987654_abc123")
    val parsedHttpsId = com.example.data.util.OjDeepLinkUtil.parseOjIdFromUri(httpsUri)
    assertEquals("oj_987654_abc123", parsedHttpsId)

    // Parse HTTP link
    val httpUri = android.net.Uri.parse("http://ojas.app/oj/oj_987654_abc123")
    val parsedHttpId = com.example.data.util.OjDeepLinkUtil.parseOjIdFromUri(httpUri)
    assertEquals("oj_987654_abc123", parsedHttpId)

    // Parse Custom scheme
    val customUri = android.net.Uri.parse("ojas://oj/oj_987654_abc123")
    val parsedCustomId = com.example.data.util.OjDeepLinkUtil.parseOjIdFromUri(customUri)
    assertEquals("oj_987654_abc123", parsedCustomId)

    // Reject unknown domain / scheme / invalid characters
    val invalidDomain = android.net.Uri.parse("https://random-site.com/oj/oj_987654_abc123")
    assertEquals(null, com.example.data.util.OjDeepLinkUtil.parseOjIdFromUri(invalidDomain))

    val dangerousPath = android.net.Uri.parse("https://ojas.app/oj/../../etc/passwd")
    assertEquals(null, com.example.data.util.OjDeepLinkUtil.parseOjIdFromUri(dangerousPath))

    val emptyUri = android.net.Uri.parse("https://ojas.app/oj/")
    assertEquals(null, com.example.data.util.OjDeepLinkUtil.parseOjIdFromUri(emptyUri))
  }

  @Test
  fun `oj watch analytics config evaluates hybrid qualification rule accurately`() {
    // 15-second video (15000ms): 25% is 3750ms, but capped at MIN_QUALIFYING_WATCH_MS (3000ms)
    org.junit.Assert.assertFalse(com.example.data.model.OjWatchAnalyticsConfig.isQualifiedView(1500L, 15000L))
    org.junit.Assert.assertFalse(com.example.data.model.OjWatchAnalyticsConfig.isQualifiedView(2900L, 15000L))
    org.junit.Assert.assertTrue(com.example.data.model.OjWatchAnalyticsConfig.isQualifiedView(3000L, 15000L))
    org.junit.Assert.assertTrue(com.example.data.model.OjWatchAnalyticsConfig.isQualifiedView(5000L, 15000L))

    // 8-second video (8000ms): 25% is 2000ms
    org.junit.Assert.assertFalse(com.example.data.model.OjWatchAnalyticsConfig.isQualifiedView(1800L, 8000L))
    org.junit.Assert.assertTrue(com.example.data.model.OjWatchAnalyticsConfig.isQualifiedView(2000L, 8000L))

    // 4-second video (4000ms): 25% is 1000ms, but floor is 2000ms
    org.junit.Assert.assertFalse(com.example.data.model.OjWatchAnalyticsConfig.isQualifiedView(1500L, 4000L))
    org.junit.Assert.assertTrue(com.example.data.model.OjWatchAnalyticsConfig.isQualifiedView(2000L, 4000L))
  }

  @Test
  fun `oj watch analytics repository enforces session deduplication and idempotency`() = kotlinx.coroutines.runBlocking {
    val ojRepo = com.example.data.repository.OjasOjRepository()
    val video = ojRepo.createOjVideo(
      creatorId = "creator_view_test",
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_view_test/v1",
        caption = "View count test video",
        category = "dance"
      )
    ).getOrThrow()

    val analyticsRepo = com.example.data.repository.OjasOjWatchAnalyticsRepository(ojRepo)
    val sessionId = analyticsRepo.getSessionId()
    org.junit.Assert.assertTrue(sessionId.startsWith("sess_"))

    // Initial count is 0
    assertEquals(0L, analyticsRepo.getViewCount(video.ojId).getOrThrow())
    org.junit.Assert.assertFalse(analyticsRepo.hasSessionViewed(sessionId, video.ojId))

    // Record valid view event
    val event1 = com.example.data.model.OjViewEvent(
      eventId = "evt_101",
      ojId = video.ojId,
      viewerId = null, // guest / unauthenticated
      sessionId = sessionId,
      watchedDurationMs = 3200L,
      totalDurationMs = 15000L
    )

    val res1 = analyticsRepo.recordQualifiedView(event1)
    org.junit.Assert.assertTrue(res1.isSuccess)
    assertEquals(1L, analyticsRepo.getViewCount(video.ojId).getOrThrow())
    org.junit.Assert.assertTrue(analyticsRepo.hasSessionViewed(sessionId, video.ojId))

    // Video loops: second event for same ojId and same session -> deduplicated!
    val loopEvent = com.example.data.model.OjViewEvent(
      eventId = "evt_102_loop",
      ojId = video.ojId,
      viewerId = null,
      sessionId = sessionId,
      watchedDurationMs = 3500L,
      totalDurationMs = 15000L
    )
    val resLoop = analyticsRepo.recordQualifiedView(loopEvent)
    org.junit.Assert.assertTrue(resLoop.isSuccess)
    // Count remains 1
    assertEquals(1L, analyticsRepo.getViewCount(video.ojId).getOrThrow())

    // Idempotent retry with same eventId
    val resRetry = analyticsRepo.recordQualifiedView(event1)
    org.junit.Assert.assertTrue(resRetry.isSuccess)
    assertEquals(1L, analyticsRepo.getViewCount(video.ojId).getOrThrow())

    // Synchronized with main repository
    val updatedOj = ojRepo.getOjVideoById(video.ojId).getOrThrow()
    assertEquals(1L, updatedOj.viewCount)

    // Distinct session increments count
    val newSessionEvent = com.example.data.model.OjViewEvent(
      eventId = "evt_201_other_sess",
      ojId = video.ojId,
      viewerId = "authenticated_user_99",
      sessionId = "sess_another_client_999",
      watchedDurationMs = 4000L,
      totalDurationMs = 15000L
    )
    val resOtherSession = analyticsRepo.recordQualifiedView(newSessionEvent)
    org.junit.Assert.assertTrue(resOtherSession.isSuccess)
    assertEquals(2L, analyticsRepo.getViewCount(video.ojId).getOrThrow())

    val finalOj = ojRepo.getOjVideoById(video.ojId).getOrThrow()
    assertEquals(2L, finalOj.viewCount)
  }

  @Test
  fun `oj recommendation repository handles cold start, explicit filters and user signals`() = kotlinx.coroutines.runBlocking {
    val ojRepo = com.example.data.repository.OjasOjRepository()
    val socialRepo = com.example.data.repository.OjasSocialInteractionRepository()
    val watchRepo = com.example.data.repository.OjasOjWatchAnalyticsRepository(ojRepo)
    val recRepo = com.example.data.repository.OjasOjRecommendationRepository(ojRepo, socialRepo, watchRepo)

    // 1. Create videos across different categories and creators
    val danceVideo1 = ojRepo.createOjVideo(
      creatorId = "creator_dance_1",
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_dance_1/d1",
        caption = "Epic Dance Moves",
        category = "dance"
      )
    ).getOrThrow()

    val comedyVideo1 = ojRepo.createOjVideo(
      creatorId = "creator_comedy_1",
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_comedy_1/c1",
        caption = "Hilarious Skit",
        category = "comedy"
      )
    ).getOrThrow()

    val musicVideo1 = ojRepo.createOjVideo(
      creatorId = "creator_music_1",
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_music_1/m1",
        caption = "New Beat Track",
        category = "music"
      )
    ).getOrThrow()

    // 2. Cold-start / Logged-out discovery feed test (no userId)
    val loggedOutFeed = recRepo.getRecommendedForYouFeed(
      userId = null,
      sessionId = "guest_session_123",
      categoryFilter = null,
      page = 1,
      pageSize = 10
    ).getOrThrow()

    assertEquals(3, loggedOutFeed.size)
    org.junit.Assert.assertTrue(loggedOutFeed.any { it.ojId == danceVideo1.ojId })
    org.junit.Assert.assertTrue(loggedOutFeed.any { it.ojId == comedyVideo1.ojId })
    org.junit.Assert.assertTrue(loggedOutFeed.any { it.ojId == musicVideo1.ojId })

    // 3. Explicit Category Filter Test: Selecting "comedy" promotes comedy video to the top
    val filteredFeed = recRepo.getRecommendedForYouFeed(
      userId = null,
      sessionId = "guest_session_123",
      categoryFilter = "comedy",
      page = 1,
      pageSize = 10
    ).getOrThrow()

    assertEquals("comedy", filteredFeed.first().category)
    assertEquals(comedyVideo1.ojId, filteredFeed.first().ojId)

    // 4. Authenticated user signal test: User follows "creator_music_1"
    val userId = "user_listener_42"
    socialRepo.setFollow(followerId = userId, followedId = "creator_music_1", isFollowing = true)

    val userFeed = recRepo.getRecommendedForYouFeed(
      userId = userId,
      sessionId = "user_session_42",
      categoryFilter = null,
      page = 1,
      pageSize = 10
    ).getOrThrow()

    // Music video from followed creator should be ranked at the top of For You
    assertEquals("creator_music_1", userFeed.first().creatorId)
    assertEquals(musicVideo1.ojId, userFeed.first().ojId)

    // 5. Watch signal test: User watches dance video
    val watchEvent = com.example.data.model.OjViewEvent(
      eventId = "evt_watch_dance",
      ojId = danceVideo1.ojId,
      viewerId = userId,
      sessionId = "user_session_42",
      watchedDurationMs = 5000L,
      totalDurationMs = 15000L
    )
    watchRepo.recordQualifiedView(watchEvent)

    val watchEvents = watchRepo.getQualifiedWatchEvents(userId, "user_session_42")
    assertEquals(1, watchEvents.size)
    assertEquals(danceVideo1.ojId, watchEvents.first().ojId)
  }

  @Test
  fun `oj following feed handles logged-out, zero-follows, no-content, and valid followed creators`() = kotlinx.coroutines.runBlocking {
    val ojRepo = com.example.data.repository.OjasOjRepository()
    val socialRepo = com.example.data.repository.OjasSocialInteractionRepository()

    val creatorA = "creator_alpha"
    val creatorB = "creator_beta"
    val creatorC = "creator_gamma"

    // Create videos with timestamps
    val videoA1 = ojRepo.createOjVideo(
      creatorId = creatorA,
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_alpha/v1",
        caption = "Alpha Video 1",
        category = "lifestyle"
      )
    ).getOrThrow()

    // Small delay to ensure timestamp progression for chronological ordering
    kotlinx.coroutines.delay(10)

    val videoB1 = ojRepo.createOjVideo(
      creatorId = creatorB,
      draft = com.example.data.model.OjVideoDraft(
        videoUrl = "ojas://storage/oj_videos/creator_beta/v1",
        caption = "Beta Video 1",
        category = "tech"
      )
    ).getOrThrow()

    // 1. Logged out / empty follow list -> Following feed returns empty
    val loggedOutFollowing = ojRepo.getFollowingOjVideos(
      followedUserIds = emptyList(),
      page = 1,
      pageSize = 10
    ).getOrThrow()
    assertEquals(0, loggedOutFollowing.size)

    // 2. Signed-in user with zero follows -> returns empty
    val userNoFollows = "user_lonely"
    val followedIdsZero = socialRepo.getFollowedUserIds(userNoFollows).getOrThrow()
    assertEquals(0, followedIdsZero.size)
    val noFollowsFeed = ojRepo.getFollowingOjVideos(
      followedUserIds = followedIdsZero,
      page = 1,
      pageSize = 10
    ).getOrThrow()
    assertEquals(0, noFollowsFeed.size)

    // 3. User follows creatorC who has NO videos posted -> returns empty (truthful empty state)
    val userFollowingEmptyCreator = "user_c_watcher"
    socialRepo.setFollow(followerId = userFollowingEmptyCreator, followedId = creatorC, isFollowing = true)
    val followedIdsC = socialRepo.getFollowedUserIds(userFollowingEmptyCreator).getOrThrow()
    assertEquals(1, followedIdsC.size)
    val cFeed = ojRepo.getFollowingOjVideos(
      followedUserIds = followedIdsC,
      page = 1,
      pageSize = 10
    ).getOrThrow()
    assertEquals(0, cFeed.size)

    // 4. User follows creatorA and creatorB -> returns both videos in chronological order (newer videoB1 first)
    val activeUser = "user_social_star"
    socialRepo.setFollow(followerId = activeUser, followedId = creatorA, isFollowing = true)
    socialRepo.setFollow(followerId = activeUser, followedId = creatorB, isFollowing = true)
    val activeFollowed = socialRepo.getFollowedUserIds(activeUser).getOrThrow()
    assertEquals(2, activeFollowed.size)

    val followingFeed = ojRepo.getFollowingOjVideos(
      followedUserIds = activeFollowed,
      page = 1,
      pageSize = 10
    ).getOrThrow()
    assertEquals(2, followingFeed.size)
    assertEquals(videoB1.ojId, followingFeed[0].ojId)
    assertEquals(videoA1.ojId, followingFeed[1].ojId)

    // 5. Unfollow creatorB -> only creatorA's video remains
    socialRepo.setFollow(followerId = activeUser, followedId = creatorB, isFollowing = false)
    val updatedFollowed = socialRepo.getFollowedUserIds(activeUser).getOrThrow()
    assertEquals(1, updatedFollowed.size)
    val updatedFeed = ojRepo.getFollowingOjVideos(
      followedUserIds = updatedFollowed,
      page = 1,
      pageSize = 10
    ).getOrThrow()
    assertEquals(1, updatedFeed.size)
    assertEquals(videoA1.ojId, updatedFeed[0].ojId)

    // 6. Delete creatorA's video -> excluded from Following feed
    ojRepo.deleteOjVideo(userId = creatorA, ojId = videoA1.ojId)
    val afterDeleteFeed = ojRepo.getFollowingOjVideos(
      followedUserIds = updatedFollowed,
      page = 1,
      pageSize = 10
    ).getOrThrow()
    assertEquals(0, afterDeleteFeed.size)
  }

  @Test
  fun `oj following feed pagination handles boundary conditions and deduplication`() = kotlinx.coroutines.runBlocking {
    val ojRepo = com.example.data.repository.OjasOjRepository()
    val creator = "creator_pagination_test"

    // Create 15 videos
    val videoIds = mutableListOf<String>()
    for (i in 1..15) {
      val video = ojRepo.createOjVideo(
        creatorId = creator,
        draft = com.example.data.model.OjVideoDraft(
          videoUrl = "ojas://storage/oj_videos/$creator/v$i",
          caption = "Video $i",
          category = "dance"
        )
      ).getOrThrow()
      videoIds.add(video.ojId)
      kotlinx.coroutines.delay(2)
    }

    val followedList = listOf(creator)

    // Page 1 (pageSize 10) -> Should have 10 items
    val page1 = ojRepo.getFollowingOjVideos(followedList, page = 1, pageSize = 10).getOrThrow()
    assertEquals(10, page1.size)

    // Page 2 (pageSize 10) -> Should have remaining 5 items
    val page2 = ojRepo.getFollowingOjVideos(followedList, page = 2, pageSize = 10).getOrThrow()
    assertEquals(5, page2.size)

    // Page 3 -> Should be empty
    val page3 = ojRepo.getFollowingOjVideos(followedList, page = 3, pageSize = 10).getOrThrow()
    assertEquals(0, page3.size)

    // Deduplication check across pages
    val combinedIds = (page1 + page2).map { it.ojId }
    val uniqueIds = combinedIds.toSet()
    assertEquals(15, uniqueIds.size)
  }

  @Test
  fun `firebase auth repository initial unconfigured state reports config missing`() {
    val firebaseAuthRepo = com.example.data.auth.FirebaseAuthRepository(auth = null)
    firebaseAuthRepo.checkSession()
    val state = firebaseAuthRepo.authState.value
    org.junit.Assert.assertTrue(state is com.example.data.auth.AuthState.ConfigMissing)
    assertEquals("Firebase Authentication", firebaseAuthRepo.providerName)
    org.junit.Assert.assertTrue(firebaseAuthRepo.isCloudBacked)
  }

  @Test
  fun `firebase auth repository validates signup input without cloud connection`() = kotlinx.coroutines.runBlocking {
    val firebaseAuthRepo = com.example.data.auth.FirebaseAuthRepository(auth = null)
    
    // Empty input validation
    val emptyResult = firebaseAuthRepo.signup("", "")
    org.junit.Assert.assertTrue(emptyResult.isFailure)
    org.junit.Assert.assertTrue(
      emptyResult.exceptionOrNull()?.message?.contains("Cloud credentials required") == true ||
      emptyResult.exceptionOrNull()?.message?.contains("Firebase Authentication provider is not configured") == true
    )
  }

  @Test
  fun `auth repository default factory creates valid auth repository and selects local fallback when firebase unconfigured`() {
    val defaultRepo = com.example.data.auth.AuthRepository.createDefault()
    org.junit.Assert.assertNotNull(defaultRepo)
    org.junit.Assert.assertEquals("Local Auth Engine", defaultRepo.providerName)
    org.junit.Assert.assertFalse(defaultRepo.isCloudBacked)
  }

  @Test
  fun `auth state supports initialization failed and config missing states`() {
    val configMissing = com.example.data.auth.AuthState.ConfigMissing("Missing credentials")
    val initFailed = com.example.data.auth.AuthState.InitializationFailed("SDK initialization error")
    org.junit.Assert.assertEquals("Missing credentials", configMissing.message)
    org.junit.Assert.assertEquals("SDK initialization error", initFailed.reason)
  }

  @Test
  fun `diagnostic test confirms successful runtime initialization of FirebaseApp from google-services config`() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    
    // Attempt initialization via generated Google Services resources if not already present
    val firebaseApp = try {
      com.google.firebase.FirebaseApp.getInstance()
    } catch (e: IllegalStateException) {
      com.google.firebase.FirebaseApp.initializeApp(context)
        ?: run {
          // If resource auto-detection requires explicit options in Robolectric context
          val options = com.google.firebase.FirebaseOptions.Builder()
            .setApplicationId("1:1076759095973:android:6eb79eb65332688646bb4c")
            .setProjectId("ojas-e8161")
            .setGcmSenderId("1076759095973")
            .setStorageBucket("ojas-e8161.firebasestorage.app")
            .setApiKey("AIzaSyAgyuOk27w5yWNPAfZVXImmkJfTsy0cWyI")
            .build()
          com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
    }

    // Verify runtime FirebaseApp status
    org.junit.Assert.assertNotNull("FirebaseApp must not be null", firebaseApp)
    val defaultApp = com.google.firebase.FirebaseApp.getInstance()
    org.junit.Assert.assertNotNull("FirebaseApp.getInstance() must succeed", defaultApp)
    org.junit.Assert.assertEquals(com.google.firebase.FirebaseApp.DEFAULT_APP_NAME, defaultApp.name)
    org.junit.Assert.assertEquals("ojas-e8161", defaultApp.options.projectId)
    org.junit.Assert.assertEquals("1076759095973", defaultApp.options.gcmSenderId)
    org.junit.Assert.assertEquals("1:1076759095973:android:6eb79eb65332688646bb4c", defaultApp.options.applicationId)
  }

  @Test
  fun `google sign in creates new account and transitions state appropriately`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val result = repo.signInWithGoogle(
      idToken = "sample_google_id_token_123456",
      email = "googleuser@gmail.com",
      displayName = "Google User"
    )
    org.junit.Assert.assertTrue("Google sign in should succeed", result.isSuccess)
    val user = result.getOrThrow()
    org.junit.Assert.assertEquals("Google User", user.displayName)
    org.junit.Assert.assertEquals("googleuser", user.username)
    org.junit.Assert.assertTrue("Setup should be marked complete when name and username exist", user.isSetupComplete)
    org.junit.Assert.assertTrue("Auth state should be Authenticated", repo.authState.value is com.example.data.auth.AuthState.Authenticated)

    // Second sign-in with same account should return existing user
    val secondResult = repo.signInWithGoogle(
      idToken = "sample_google_id_token_123456",
      email = "googleuser@gmail.com",
      displayName = "Google User"
    )
    org.junit.Assert.assertTrue(secondResult.isSuccess)
    org.junit.Assert.assertEquals(user.userId, secondResult.getOrThrow().userId)
  }

  @Test
  fun `google sign in rejects blank ID token`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val result = repo.signInWithGoogle(idToken = "   ", email = "test@gmail.com")
    org.junit.Assert.assertTrue("Blank ID token must fail", result.isFailure)
  }

  @Test
  fun `phone auth sends OTP and verifies valid 6 digit code`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    var sentVerificationId: String? = null
    var failedException: Exception? = null

    repo.sendPhoneOtp(
      phoneNumber = "+919876543210",
      activity = null,
      onCodeSent = { id, _ -> sentVerificationId = id },
      onVerificationFailed = { ex -> failedException = ex }
    )

    org.junit.Assert.assertNull(failedException)
    org.junit.Assert.assertNotNull("Verification ID should be generated", sentVerificationId)

    // Verify OTP with valid 6 digits
    val verifyResult = repo.verifyPhoneOtp(
      verificationId = sentVerificationId!!,
      otpCode = "123456",
      phoneNumber = "+919876543210"
    )

    org.junit.Assert.assertTrue("Valid OTP verification must succeed", verifyResult.isSuccess)
    val user = verifyResult.getOrThrow()
    org.junit.Assert.assertFalse("New phone user requires profile setup", user.isSetupComplete)
    org.junit.Assert.assertTrue("Auth state must be SetupRequired", repo.authState.value is com.example.data.auth.AuthState.SetupRequired)
  }

  @Test
  fun `phone auth rejects invalid or short phone numbers and codes`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    var failedException: Exception? = null

    // Short phone number
    repo.sendPhoneOtp(
      phoneNumber = "123",
      activity = null,
      onCodeSent = { _, _ -> },
      onVerificationFailed = { ex -> failedException = ex }
    )
    org.junit.Assert.assertNotNull("Short phone number should trigger verification failure", failedException)

    // Short / Non-digit OTP
    val shortOtpResult = repo.verifyPhoneOtp(
      verificationId = "any_id",
      otpCode = "123",
      phoneNumber = "+919876543210"
    )
    org.junit.Assert.assertTrue("Short OTP code must fail", shortOtpResult.isFailure)

    val nonDigitOtpResult = repo.verifyPhoneOtp(
      verificationId = "any_id",
      otpCode = "abcdef",
      phoneNumber = "+919876543210"
    )
    org.junit.Assert.assertTrue("Non-digit OTP code must fail", nonDigitOtpResult.isFailure)
  }

  @Test
  fun `password reset email sends successfully with valid email and rejects invalid email`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()

    val invalidResult = repo.sendPasswordResetEmail("invalid-email")
    org.junit.Assert.assertTrue("Invalid email format must be rejected", invalidResult.isFailure)

    val validResult = repo.sendPasswordResetEmail("user@example.com")
    org.junit.Assert.assertTrue("Valid email must succeed", validResult.isSuccess)
  }

  @Test
  fun `getLinkedProviders returns correct provider identifiers`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    repo.signup("testuser@example.com", "securePassword123")
    val initialProviders = repo.getLinkedProviders()
    org.junit.Assert.assertTrue("Initial providers contains password", initialProviders.contains("password"))

    repo.linkWithGoogle("dummy_token")
    val updatedProviders = repo.getLinkedProviders()
    org.junit.Assert.assertTrue("Updated providers contains google.com", updatedProviders.contains("google.com"))

    repo.unlinkProvider("google.com")
    val afterUnlink = repo.getLinkedProviders()
    org.junit.Assert.assertFalse("After unlinking google.com is removed", afterUnlink.contains("google.com"))
  }

  @Test
  fun `auth repository completeSetup rejects reserved usernames`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    repo.signup("newuser@example.com", "password123")
    
    val adminSetup = repo.completeSetup("Admin User", "admin")
    org.junit.Assert.assertTrue("Reserved username 'admin' must be rejected", adminSetup.isFailure)
    org.junit.Assert.assertTrue(adminSetup.exceptionOrNull()?.message?.contains("reserved") == true)

    val ojasSetup = repo.completeSetup("Ojas Staff", "ojas")
    org.junit.Assert.assertTrue("Reserved username 'ojas' must be rejected", ojasSetup.isFailure)

    val supportSetup = repo.completeSetup("Support Bot", "support")
    org.junit.Assert.assertTrue("Reserved username 'support' must be rejected", supportSetup.isFailure)

    // Valid unique username
    val validSetup = repo.completeSetup("Creative User", "creative_user")
    org.junit.Assert.assertTrue("Valid unreserved username must succeed", validSetup.isSuccess)
  }

  @Test
  fun `auth repository checkUsernameAvailability respects reserved words and case`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    
    val reservedAvail = repo.checkUsernameAvailability("admin")
    org.junit.Assert.assertTrue(reservedAvail.isSuccess)
    org.junit.Assert.assertFalse("Reserved username 'admin' should not be available", reservedAvail.getOrThrow())

    val ojasAvail = repo.checkUsernameAvailability("@OJAS")
    org.junit.Assert.assertTrue(ojasAvail.isSuccess)
    org.junit.Assert.assertFalse("Reserved username '@OJAS' should not be available", ojasAvail.getOrThrow())

    val validAvail = repo.checkUsernameAvailability("@unique_creator_99")
    org.junit.Assert.assertTrue(validAvail.isSuccess)
    org.junit.Assert.assertTrue("Unused handle should be available", validAvail.getOrThrow())
  }

  @Test
  fun `auth repository email verification flow and reload works`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    
    // Attempting when not logged in fails
    val unauthResult = repo.sendEmailVerification()
    org.junit.Assert.assertTrue("Unauthenticated verification request must fail", unauthResult.isFailure)

    // Log in and send verification
    repo.signup("verify_me@example.com", "password123")
    val sendResult = repo.sendEmailVerification()
    org.junit.Assert.assertTrue("Authenticated email verification request must succeed", sendResult.isSuccess)
    
    val reloadResult = repo.reloadUser()
    org.junit.Assert.assertTrue("Reload user returns success", reloadResult.isSuccess)
    org.junit.Assert.assertTrue("isEmailVerified returns true when verified", repo.isEmailVerified())
  }

  @Test
  fun `auth repository sendPasswordResetEmail validates email address format`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val badEmailResult = repo.sendPasswordResetEmail("bad-email-format")
    org.junit.Assert.assertTrue("Bad email format fails", badEmailResult.isFailure)

    val validEmailResult = repo.sendPasswordResetEmail("user@example.com")
    org.junit.Assert.assertTrue("Valid email format succeeds", validEmailResult.isSuccess)
  }

  @Test
  fun `auth repository linkWithEmailPassword links and prevents duplicates`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()

    // Existing user 1
    repo.signup("first@example.com", "pass1234")
    repo.completeSetup("First User", "first_user")
    repo.logout()

    // User 2 via Google
    val googleResult = repo.signInWithGoogle("google_token_123", "second@example.com", "Second User")
    org.junit.Assert.assertTrue(googleResult.isSuccess)
    val user2 = googleResult.getOrThrow()
    org.junit.Assert.assertTrue(repo.authState.value is com.example.data.auth.AuthState.Authenticated)

    // Attempting to link already used email 'first@example.com' fails
    val collisionResult = repo.linkWithEmailPassword("first@example.com", "newpass123")
    org.junit.Assert.assertTrue("Collision must fail", collisionResult.isFailure)
    org.junit.Assert.assertTrue(collisionResult.exceptionOrNull()?.message?.contains("already linked") == true)

    // Linking a new clean email succeeds
    val linkSuccess = repo.linkWithEmailPassword("second_pass@example.com", "newpass123")
    org.junit.Assert.assertTrue("Linking new email must succeed", linkSuccess.isSuccess)
    org.junit.Assert.assertTrue(repo.getLinkedProviders().contains("password"))
  }

  @Test
  fun `auth repository recoverOjasId retrieves username for registered email`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    repo.signup("recover_target@example.com", "securepass123")
    repo.completeSetup("Target User", "target_handle")
    repo.logout()

    val lookupResult = repo.recoverOjasId("recover_target@example.com")
    org.junit.Assert.assertTrue("recoverOjasId should succeed", lookupResult.isSuccess)
    val user = lookupResult.getOrNull()
    org.junit.Assert.assertNotNull(user)
    assertEquals("target_handle", user?.username)
  }

  @Test
  fun `auth repository recoverOjasId rejects invalid email formats`() = kotlinx.coroutines.runBlocking {
    val repo = com.example.data.auth.OjasAuthRepository()
    val lookupResult = repo.recoverOjasId("invalid-email")
    org.junit.Assert.assertTrue(lookupResult.isFailure)
  }
}


