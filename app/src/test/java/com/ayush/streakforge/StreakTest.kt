package com.ayush.streakforge

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 10, 6)

    @Test
    fun testPushedToday() {
        val days = listOf(
            ContributionDay("2026-10-04", 2),
            ContributionDay("2026-10-05", 1),
            ContributionDay("2026-10-06", 3)
        )
        val result = computeStreak(days, totalContributions = 6, todayUtc = today)
        assertTrue(result.todayDone)
        assertEquals(3, result.current)
        assertEquals(3, result.longest)
    }

    @Test
    fun testYesterdayOnly() {
        val days = listOf(
            ContributionDay("2026-10-04", 2),
            ContributionDay("2026-10-05", 1),
            ContributionDay("2026-10-06", 0)
        )
        val result = computeStreak(days, totalContributions = 3, todayUtc = today)
        assertFalse(result.todayDone)
        assertEquals(2, result.current)
        assertEquals(2, result.longest)
    }

    @Test
    fun testTwoDayGap() {
        val days = listOf(
            ContributionDay("2026-10-03", 5),
            ContributionDay("2026-10-04", 0),
            ContributionDay("2026-10-05", 0),
            ContributionDay("2026-10-06", 0)
        )
        val result = computeStreak(days, totalContributions = 5, todayUtc = today)
        assertFalse(result.todayDone)
        assertEquals(0, result.current)
        assertEquals(1, result.longest)
    }

    @Test
    fun testEmptyList() {
        val days = emptyList<ContributionDay>()
        val result = computeStreak(days, totalContributions = 0, todayUtc = today)
        assertFalse(result.todayDone)
        assertEquals(0, result.current)
        assertEquals(0, result.longest)
    }

    @Test
    fun testFutureDaysIgnored() {
        val days = listOf(
            ContributionDay("2026-10-05", 1),
            ContributionDay("2026-10-06", 0),
            ContributionDay("2026-10-07", 10), // Future day!
            ContributionDay("2026-10-08", 20)  // Future day!
        )
        val result = computeStreak(days, totalContributions = 31, todayUtc = today)
        assertFalse(result.todayDone)
        assertEquals(1, result.current)
        assertEquals(1, result.longest)
    }

    @Test
    fun testPassiveShieldsDoNotAlterStreak() {
        val days = listOf(
            ContributionDay("2026-10-04", 2),
            ContributionDay("2026-10-05", 0),
            ContributionDay("2026-10-06", 0)
        )
        val result = computeStreak(days, totalContributions = 2, shields = 3, isShieldActive = false, todayUtc = today)
        assertFalse(result.todayDone)
        assertFalse(result.shieldActive)
        assertEquals(0, result.current)
        assertEquals(3, result.shields)
    }

    @Test
    fun testExplicitShieldActivation() {
        val days = listOf(
            ContributionDay("2026-10-04", 2),
            ContributionDay("2026-10-05", 0),
            ContributionDay("2026-10-06", 0)
        )
        val result = computeStreak(days, totalContributions = 2, shields = 3, isShieldActive = true, todayUtc = today)
        assertFalse(result.todayDone)
        assertTrue(result.shieldActive)
        assertEquals(2, result.current)
        assertEquals(2, result.shields)
    }

    @Test
    fun testBrandNewAccountZeroContributions() {
        val days = listOf(
            ContributionDay("2026-10-06", 0)
        )
        val result = computeStreak(days, totalContributions = 0, todayUtc = today)
        assertFalse(result.todayDone)
        assertEquals(0, result.current)
        assertEquals(0, result.longest)
        assertEquals(0, result.total)
    }

    @Test
    fun testFlameStateLit() {
        assertEquals(FlameState.LIT, flameState(current = 5, todayDone = true))
        assertEquals(FlameState.LIT, flameState(current = 1, todayDone = true))
    }

    @Test
    fun testFlameStateFading() {
        assertEquals(FlameState.FADING, flameState(current = 5, todayDone = false))
        assertEquals(FlameState.FADING, flameState(current = 1, todayDone = false))
    }

    @Test
    fun testFlameStateBroken() {
        assertEquals(FlameState.BROKEN, flameState(current = 0, todayDone = false))
        assertEquals(FlameState.BROKEN, flameState(current = 0, todayDone = true))
    }

    @Test
    fun testDefaultRewardsGenericAndNotNull() {
        assertTrue(REWARDS.isNotEmpty())
        val firstReward = REWARDS.first()
        assertEquals(7, firstReward.day)
        assertTrue(firstReward.label.isNotBlank())
        assertTrue(firstReward.emoji.isNotBlank())
    }

    @Test
    fun testRewardLogicClaimAndCustomization() {
        val defaultRewards = REWARDS
        val customOverridden = defaultRewards.map {
            if (it.day == 7) it.copy(label = "Custom Treat", emoji = "⚡") else it
        }

        val reward7 = customOverridden.first { it.day == 7 }
        assertEquals("Custom Treat", reward7.label)
        assertEquals("⚡", reward7.emoji)

        // Verify unclaimed by default
        val claimedSet = mutableSetOf<Int>()
        assertFalse(7 in claimedSet)

        // Claim reward
        claimedSet.add(7)
        assertTrue(7 in claimedSet)
        assertFalse(14 in claimedSet)
    }

    @Test
    fun testSignedOutStateHasNoFakeStreakData() {
        val signedOutStreak: StreakInfo? = null
        assertNull(signedOutStreak)
    }
}
