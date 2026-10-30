package com.mrcerise.intervalcoach

import com.mrcerise.intervalcoach.journey.*
import com.pulse.engine.TimelineExpander
import com.pulse.engine.Validation
import org.junit.Assert.*
import org.junit.Test

class JourneyGenerationTest {
    @Test fun completedFoundationContinuesAtEveryLevelAndFillsGaps() {
        JourneyLevel.entries.forEach { level ->
            val foundation = JourneyCatalogue.path(level).map { it.id }.toSet()
            assertEquals(JourneyCatalogue.at(level, 24), JourneyCatalogue.recommended(level, foundation))
            val extended = foundation + (24..60).map { JourneyCatalogue.at(level, it).id }
            assertEquals(JourneyCatalogue.at(level, 61), JourneyCatalogue.recommended(level, extended))
            val gap = extended - JourneyCatalogue.at(level, 39).id
            assertEquals(JourneyCatalogue.at(level, 39), JourneyCatalogue.recommended(level, gap))
            assertEquals(JourneyCatalogue.at(level, 0), JourneyCatalogue.recommended(level, emptySet()))
        }
    }

    @Test fun distantSessionsAreReproducibleLocalizedAndPlayableWithinLevelLimits() {
        JourneyLevel.entries.forEach { level ->
            val peak = JourneyCatalogue.path(level).last()
            for (index in listOf(24, 25, 29, 30, 101, 10_000, 1_000_000)) {
                val node = JourneyCatalogue.at(level, index)
                assertEquals(node, JourneyCatalogue.node(node.id))
                assertTrue(JourneyCatalogue.belongsToLevel(node.id, level))
                assertEquals(index / 6, node.chapter)
                assertEquals(index % 6, node.position)
                assertTrue(node.workSeconds in 25 + level.ordinal * 10..peak.workSeconds)
                assertTrue(node.rounds in 2 + level.ordinal..peak.rounds)
                assertTrue(node.restSeconds >= peak.restSeconds)
                listOf(false, true).forEach { french ->
                    val plan = node.plan(french)
                    assertTrue(Validation.validate(plan).toString(), Validation.validate(plan).isEmpty())
                    val timeline = TimelineExpander.expand(plan)
                    assertFalse(timeline.hasOpenEnded)
                    assertTrue(timeline.steps.all { it.durationMillis > 0 })
                    assertTrue(timeline.knownMillis in 60_000L..2_400_000L)
                    assertTrue(JourneyCatalogue.chapterTitle(node.chapter).localized(french).isNotBlank())
                }
            }
        }
    }

    @Test fun generatorVariesTheWorkoutRatherThanOnlyItsName() {
        JourneyLevel.entries.forEach { level ->
            val variations = (4..30).map { chapter ->
                val node = JourneyCatalogue.at(level, chapter * 6)
                listOf(node.exercises, node.workSeconds, node.restSeconds, node.rounds)
            }.distinct()
            assertTrue("Expected varied movement orders and timing at $level", variations.size > 10)
        }
    }

    @Test fun generatedIdsAreUnambiguousAndDoNotReplaceExistingMilestones() {
        JourneyCatalogue.nodes.forEach { node ->
            assertEquals(node, JourneyCatalogue.node(node.id))
            assertEquals(node, JourneyCatalogue.at(node.level, node.chapter * 6 + node.position))
        }
        listOf(
            "journey-generated-v1-beginner-c4-s1",
            "journey-generated-v1-beginner-c05-s1",
            "journey-generated-v1-beginner-c5-s7",
            "journey-generated-v2-beginner-c5-s1",
            "journey-generated-v1-invalid-c5-s1",
            "journey-generated-v1-beginner-c9999999999999999-s1",
        ).forEach { assertNull(it, JourneyCatalogue.node(it)) }
    }
}
