package com.ideaforge.ai.core.cloud.providers

import com.ideaforge.ai.core.cloud.BuildPhase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GitHubActionsProviderTest {

    private lateinit var provider: GitHubActionsProvider

    @Before
    fun setup() {
        provider = GitHubActionsProvider("test-token", "test-owner", "test-repo")
    }

    @Test
    fun `provider name is GitHub Actions`() {
        assertEquals("GitHub Actions", provider.name)
    }

    @Test
    fun `isTransientError detects timeout`() {
        assertTrue(provider.isTransientError(Exception("Connection timed out")))
    }

    @Test
    fun `isTransientError detects connection reset`() {
        assertTrue(provider.isTransientError(Exception("Connection reset")))
    }

    @Test
    fun `isTransientError detects503`() {
        assertTrue(provider.isTransientError(Exception("HTTP 503 Service Unavailable")))
    }

    @Test
    fun `isTransientError detects502`() {
        assertTrue(provider.isTransientError(Exception("HTTP 502 Bad Gateway")))
    }

    @Test
    fun `isTransientError detects429 rate limit`() {
        assertTrue(provider.isTransientError(Exception("429 Rate limit exceeded")))
    }

    @Test
    fun `isTransientError detects rate limit`() {
        assertTrue(provider.isTransientError(Exception("API rate limit exceeded")))
    }

    @Test
    fun `isTransientError detects unavailable`() {
        assertTrue(provider.isTransientError(Exception("Service unavailable")))
    }

    @Test
    fun `isTransientError returns false for non-transient errors`() {
        assertFalse(provider.isTransientError(Exception("HTTP 404 Not Found")))
        assertFalse(provider.isTransientError(Exception("Invalid token")))
        assertFalse(provider.isTransientError(Exception("Permission denied")))
    }

    @Test
    fun `isTransientError handles null message`() {
        assertFalse(provider.isTransientError(Exception()))
    }

    @Test
    fun `isTransientError handles case insensitive`() {
        assertTrue(provider.isTransientError(Exception("TIMEOUT")))
        assertTrue(provider.isTransientError(Exception("Connection Reset")))
    }
}
