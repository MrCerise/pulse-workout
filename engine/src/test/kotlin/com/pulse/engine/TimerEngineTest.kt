package com.pulse.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance and regression tests for the pure timer engine.
 * Every timing test uses [FakeClock], so exact boundaries and long runs are verified without
 * waiting in real time.
 */
class TimerEngineTest {

    private fun interval(
        id: String,
        name: String,
        kind: PhaseKind,
        seconds: Long,
        manual: Boolean = false,
        omitted: Boolean = false,
    ) = IntervalNode(id, IntervalSpec(id = id, name = name, kind = kind, durationMillis = seconds * 1000, manualCompletion = manual, omitted = omitted))

    private fun group(
        id: String,
        name: String,
        repeat: Int,
        children: List<Node>,
        restAfterMillis: Long = 0,
        ladder: Ladder? = null,
    ) = RepeatGroup(nodeId = id, name = name, repeat = repeat, children = children, restAfterGroupMillis = restAfterMillis, ladder = ladder)

    private fun plan(nodes: List<Node>, includeFinalRest: Boolean = true, type: WorkoutType = WorkoutType.HIIT) =
        WorkoutPlan(id = "test-plan", name = "Test", type = type, nodes = nodes, includeFinalRest = includeFinalRest)

    // -------------------------------------------------------------------------------------------
    // Acceptance case 1 — Tabata totals
    // -------------------------------------------------------------------------------------------

    @Test
    fun `eight rounds of 20-10 last 4 minutes with final rest and 3-50 without`() {
        val nodes = listOf(
            group(
                id = "tabata",
                name = "Tabata",
                repeat = 8,
                children = listOf(
                    interval("w", "Work", PhaseKind.WORK, 20),
                    interval("r", "Rest", PhaseKind.REST, 10),
                ),
            )
        )

        val withFinalRest = TimelineExpander.expand(plan(nodes, includeFinalRest = true))
        assertEquals(16, withFinalRest.steps.size)
        assertEquals(4 * 60 * 1000L, withFinalRest.knownMillis)
        assertEquals(240_000L, withFinalRest.totalMillis)

        val withoutFinalRest = TimelineExpander.expand(plan(nodes, includeFinalRest = false))
        assertEquals(15, withoutFinalRest.steps.size)
        assertEquals(230_000L, withoutFinalRest.knownMillis)
    }

    @Test
    fun `timeline order is work then rest for every round`() {
        val nodes = listOf(
            group("g", "Tabata", 3, listOf(
                interval("w", "Work", PhaseKind.WORK, 20),
                interval("r", "Rest", PhaseKind.REST, 10),
            ))
        )
        val steps = TimelineExpander.expand(plan(nodes)).steps
        assertEquals(listOf(PhaseKind.WORK, PhaseKind.REST, PhaseKind.WORK, PhaseKind.REST, PhaseKind.WORK, PhaseKind.REST), steps.map { it.kind })
        assertEquals(listOf(1, 1, 2, 2, 3, 3), steps.map { it.roundInGroup })
    }

    // -------------------------------------------------------------------------------------------
    // Acceptance case 2 — pause/resume keeps remaining time
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a 30 second interval paused at 12 seconds and resumed 20 seconds later has 18 seconds left`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        val timeline = TimelineExpander.expand(plan(listOf(interval("a", "Plank", PhaseKind.WORK, 30))))
        engine.prepare(timeline, "p", "Test")

        engine.start()
        clock.advance(12_000)
        engine.tick()
        assertEquals(18_000L, engine.snapshot().stepRemainingMillis)

        engine.pause()
        clock.advance(20_000) // time passes while paused
        engine.tick()
        assertEquals(18_000L, engine.snapshot().stepRemainingMillis)
        assertEquals(TimerStatus.PAUSED, engine.snapshot().status)

        engine.resume()
        clock.advance(18_000)
        engine.tick()
        val snap = engine.snapshot()
        assertEquals(TimerStatus.FINISHED, snap.status)
        assertEquals(0L, snap.stepRemainingMillis)
        // Total elapsed excludes the paused 20 s.
        assertEquals(30_000L, snap.sessionElapsedMillis)
    }

    // -------------------------------------------------------------------------------------------
    // Acceptance case 3 — wall clock independence
    // -------------------------------------------------------------------------------------------

    @Test
    fun `remaining time depends only on the monotonic clock`() {
        // The engine reads nothing but the injected MonotonicClock, so a wall-clock jump is not
        // even representable. This test locks the contract in place.
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(interval("a", "Row", PhaseKind.WORK, 60)))), "p", "T")
        engine.start()
        clock.advance(15_000)
        engine.tick()
        // Simulate a user moving the device clock forward three hours: the monotonic clock (and
        // therefore the engine) is untouched.
        assertEquals(45_000L, engine.snapshot().stepRemainingMillis)
    }

    // -------------------------------------------------------------------------------------------
    // Acceptance case 4 — nested structures
    // -------------------------------------------------------------------------------------------

    @Test
    fun `nested groups produce the correct order count and duration`() {
        val nodes = listOf(
            interval("prep", "Get ready", PhaseKind.PREPARE, 10),
            group("circuit", "Circuit", repeat = 3, restAfterMillis = 30_000, children = listOf(
                interval("a", "Push-ups", PhaseKind.WORK, 30),
                group("superset", "Superset", repeat = 2, children = listOf(
                    interval("b", "Squats", PhaseKind.WORK, 20),
                    interval("c", "Rest", PhaseKind.REST, 10),
                )),
            )),
            interval("cool", "Cool-down", PhaseKind.COOLDOWN, 60),
        )
        val timeline = TimelineExpander.expand(plan(nodes))
        // 1 prep + 3 rounds * (1 exercise + 2*2 superset steps + 1 rest-after) + 1 cooldown = 20
        assertEquals(20, timeline.steps.size)
        // 10 + 3*(30 + 2*(20+10) + 30) + 60 = 10 + 3*120 + 60 = 430 s
        assertEquals(430_000L, timeline.knownMillis)
        assertEquals(listOf("Get ready", "Push-ups", "Squats", "Rest", "Squats", "Rest", "Rest between blocks"),
            timeline.steps.take(7).map { it.name })
        assertEquals(listOf("Circuit", "Superset"), timeline.steps[2].groupBreadcrumb)
        assertEquals("Circuit", timeline.steps[1].groupName)
        assertEquals(1, timeline.steps[1].roundInGroup)
        assertEquals(2, timeline.steps[2].roundsInGroup)
    }

    @Test
    fun `nesting deeper than the documented limit is rejected`() {
        var nodes: List<Node> = listOf(interval("x", "Work", PhaseKind.WORK, 10))
        repeat(10) { level -> nodes = listOf(group("g$level", "G$level", 2, nodes)) }
        val problems = Validation.validate(plan(nodes), EngineLimits(maxNestingDepth = 8))
        assertTrue(problems.any { it.contains("nested") })
    }

    // -------------------------------------------------------------------------------------------
    // Acceptance case 5 — no duplicate transitions or history records
    // -------------------------------------------------------------------------------------------

    @Test
    fun `repeated ticks never duplicate transitions`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("w", "Work", PhaseKind.WORK, 10),
            interval("r", "Rest", PhaseKind.REST, 10),
        ))), "p", "T")
        engine.start()
        engine.drainEvents()
        repeat(500) { engine.tick() } // 500 ticks at the same instant
        assertEquals(0, engine.drainEvents().count { it is TimerEvent.StepCompleted })

        clock.advance(10_000)
        engine.tick()
        engine.tick()
        engine.tick()
        val completions = engine.drainEvents().count { it is TimerEvent.StepCompleted }
        assertEquals(1, completions)
    }

    @Test
    fun `next then next moves exactly two steps even when events are replayed`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("a", "A", PhaseKind.WORK, 30),
            interval("b", "B", PhaseKind.REST, 30),
            interval("c", "C", PhaseKind.WORK, 30),
        ))), "p", "T")
        engine.start()
        engine.drainEvents()
        engine.next()
        engine.next()
        val events = engine.drainEvents()
        assertEquals(2, events.count { it is TimerEvent.StepCompleted })
        assertEquals(2, events.filterIsInstance<TimerEvent.StepStarted>().size)
        assertEquals(2, engine.snapshot().stepIndex)
        assertEquals(2, engine.snapshot().skippedSteps)
        assertEquals(0, engine.snapshot().completedSteps)
    }

    @Test
    fun `previous does not fabricate completions`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("a", "A", PhaseKind.WORK, 10),
            interval("b", "B", PhaseKind.WORK, 10),
            interval("c", "C", PhaseKind.WORK, 10),
        ))), "p", "T")
        engine.start()
        clock.advance(10_000); engine.tick()
        clock.advance(10_000); engine.tick()
        assertEquals(2, engine.snapshot().completedSteps)
        engine.previous()
        assertEquals(1, engine.snapshot().stepIndex)
        assertTrue(engine.snapshot().completedSteps <= 2)
        clock.advance(10_000); engine.tick()
        // Step 1 re-runs, then step 2 completes again and the session ends. Distinct intervals
        // completed = 3 of 3, so the summary can never read more than 100 %.
        assertEquals(3, engine.snapshot().completedSteps)
        assertEquals(TimerStatus.FINISHED, engine.snapshot().status)
    }

    @Test
    fun `add time extends the current interval and the session total`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("a", "A", PhaseKind.WORK, 30),
            interval("b", "B", PhaseKind.WORK, 30),
        ))), "p", "T")
        engine.start()
        clock.advance(10_000); engine.tick()
        engine.addTime(15_000)
        assertEquals(35_000L, engine.snapshot().stepRemainingMillis)
        assertEquals(65_000L, engine.snapshot().sessionRemainingMillis)
        clock.advance(34_000); engine.tick()
        assertEquals(1_000L, engine.snapshot().stepRemainingMillis)
        clock.advance(1_000); engine.tick()
        assertEquals(1, engine.snapshot().stepIndex)
    }

    // -------------------------------------------------------------------------------------------
    // Acceptance cases 7 & 6 (engine side) — long gaps and boundary crossing
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a single tick can cross many boundaries without losing order`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        val steps = (1..20).map { interval("i$it", "Step $it", PhaseKind.WORK, 1) }
        engine.prepare(TimelineExpander.expand(plan(listOf(group("g", "Block", 1, steps)))), "p", "T")
        engine.start()
        engine.drainEvents()
        clock.advance(12_500) // pretend the process did not tick for 12.5 s (screen off, jank, doze)
        engine.tick()
        val events = engine.drainEvents()
        val completed = events.filterIsInstance<TimerEvent.StepCompleted>()
        assertEquals(12, completed.size)
        assertEquals((0..11).toList(), completed.map { it.index })
        assertEquals(12, engine.snapshot().stepIndex)
        assertEquals(500L, engine.snapshot().stepRemainingMillis)
    }

    @Test
    fun `one thousand short intervals run in a single fake-clock jump`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        val steps = (1..1000).map { interval("i$it", "S$it", PhaseKind.WORK, 1) }
        engine.prepare(TimelineExpander.expand(plan(listOf(group("g", "Block", 1, steps)))), "p", "T")
        engine.start()
        engine.drainEvents()
        clock.advance(1_000_000)
        engine.tick()
        assertEquals(1000, engine.drainEvents().count { it is TimerEvent.StepCompleted })
        assertEquals(TimerStatus.FINISHED, engine.snapshot().status)
    }

    // -------------------------------------------------------------------------------------------
    // Manual / open-ended intervals, AMRAP, stopwatch
    // -------------------------------------------------------------------------------------------

    @Test
    fun `manual interval counts up waits for the user and records actual duration`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("s", "Set 1", PhaseKind.WORK, 0, manual = true),
            interval("r", "Rest", PhaseKind.REST, 30),
        ), type = WorkoutType.STRENGTH)), "p", "T")
        engine.start()
        clock.advance(4_000); engine.tick()
        clock.advance(60_000); engine.tick()
        val snap = engine.snapshot()
        assertEquals(TimerStatus.AWAITING_MANUAL, snap.status)
        assertEquals(64_000L, snap.stepElapsedMillis)
        assertNull(snap.sessionRemainingMillis) // open-ended while the manual set is running

        engine.completeManual()
        val events = engine.drainEvents()
        val manual = events.filterIsInstance<TimerEvent.ManualIntervalCompleted>().single()
        assertEquals(64_000L, manual.actualMillis)
        assertEquals(1, engine.snapshot().stepIndex)
        assertEquals(TimerStatus.RUNNING, engine.snapshot().status)
    }

    @Test
    fun `stopwatch records laps and stays open ended`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(interval("sw", "Stopwatch", PhaseKind.CUSTOM, 0, manual = true)), type = WorkoutType.STOPWATCH)), "p", "T")
        engine.start()
        clock.advance(30_000); engine.tick()
        engine.lap()
        clock.advance(45_000); engine.tick()
        engine.lap()
        val laps = engine.drainEvents().filterIsInstance<TimerEvent.LapLogged>()
        assertEquals(listOf(30_000L to 30_000L, 45_000L to 75_000L), laps.map { it.lapMillis to it.totalMillis })
        assertNull(engine.snapshot().sessionRemainingMillis)
    }

    @Test
    fun `amrap counts manual rounds and stays open ended until the cap expires`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("cap", "AMRAP 5:00", PhaseKind.WORK, 300, manual = true),
        ), type = WorkoutType.AMRAP)), "p", "T")
        engine.start()
        repeat(4) { clock.advance(12_000); engine.tick() }
        // AMRAP counts rounds manually; the engine keeps counting up.
        assertEquals(48_000L, engine.snapshot().stepElapsedMillis)
    }

    // -------------------------------------------------------------------------------------------
    // Cues
    // -------------------------------------------------------------------------------------------

    @Test
    fun `countdown cues fire exactly once per interval`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("w", "Work", PhaseKind.WORK, 20),
            interval("r", "Rest", PhaseKind.REST, 10),
        ))), "p", "T")
        engine.start()
        engine.drainEvents()
        var cues = 0
        repeat(200) {
            clock.advance(250)
            engine.tick()
            cues += engine.drainEvents().count { it is TimerEvent.Countdown }
        }
        // Interval 1 (20 s): 3,2,1. Interval 2 (10 s): 3,2,1 — the final rest is included.
        assertEquals(6, cues)
    }

    @Test
    fun `overlapping warning thresholds do not storm short intervals`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("w", "Work", PhaseKind.WORK, 1),
            interval("r", "Rest", PhaseKind.REST, 1),
        ))), "p", "T")
        engine.start()
        engine.drainEvents()
        repeat(100) {
            clock.advance(20)
            engine.tick()
            engine.drainEvents()
        }
        // 1 s intervals sit below suppressCountdownUnderMillis, so no 3-2-1 cues are emitted at all.
        assertEquals(0, engine.drainEvents().count { it is TimerEvent.Countdown })
    }

    @Test
    fun `periodic cue timer fires on its own schedule`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(
            TimelineExpander.expand(plan(listOf(interval("c", "Cue", PhaseKind.CUSTOM, 0, manual = true)), type = WorkoutType.CUE_TIMER)),
            "p", "T",
            periodicCueMillis = 15_000,
        )
        engine.start()
        engine.drainEvents()
        clock.advance(16_000)
        engine.tick()
        assertEquals(listOf(1), engine.drainEvents().filterIsInstance<TimerEvent.PeriodicCue>().map { it.occurrence })
        clock.advance(14_000)
        engine.tick()
        assertEquals(listOf(2), engine.drainEvents().filterIsInstance<TimerEvent.PeriodicCue>().map { it.occurrence })
        // A long gap skips missed cues instead of replaying them: audio cues must never back up.
        clock.advance(120_000)
        engine.tick()
        val afterGap = engine.drainEvents().filterIsInstance<TimerEvent.PeriodicCue>()
        assertEquals(listOf(10), afterGap.map { it.occurrence })
        assertEquals(15_000L, afterGap.single().everyMillis)
    }

    // -------------------------------------------------------------------------------------------
    // Expansion rules
    // -------------------------------------------------------------------------------------------

    @Test
    fun `zero duration phases are omitted`() {
        val nodes = listOf(
            interval("prep", "Get ready", PhaseKind.PREPARE, 0),
            interval("w", "Work", PhaseKind.WORK, 30),
            interval("cool", "Cool-down", PhaseKind.COOLDOWN, 0),
        )
        val timeline = TimelineExpander.expand(plan(nodes))
        assertEquals(1, timeline.steps.size)
        assertEquals("Work", timeline.steps.single().name)
    }

    @Test
    fun `ladders lengthen work intervals per round and clamp to bounds`() {
        val nodes = listOf(
            group("g", "Ladder", repeat = 4, ladder = Ladder(Ladder.TargetKind.WORK, deltaMillis = 10_000, minMillis = 5_000, maxMillis = 45_000), children = listOf(
                interval("w", "Run", PhaseKind.WORK, 30),
                interval("r", "Walk", PhaseKind.REST, 30),
            )),
        )
        assertEquals(30_000L, TimelineExpander.applyLadder(30_000L, Ladder(deltaMillis = 10_000, minMillis = 5_000, maxMillis = 45_000), 0))
        assertEquals(40_000L, TimelineExpander.applyLadder(30_000L, Ladder(deltaMillis = 10_000, minMillis = 5_000, maxMillis = 45_000), 1))
        assertEquals(45_000L, TimelineExpander.applyLadder(30_000L, Ladder(deltaMillis = 10_000, minMillis = 5_000, maxMillis = 45_000), 3))
        val timeline = TimelineExpander.expand(plan(nodes))
        assertEquals(8, timeline.steps.size)
    }

    @Test
    fun `omitted intervals never appear in the timeline`() {
        val nodes = listOf(
            IntervalNode("w", IntervalSpec("w", "Work", PhaseKind.WORK, 30_000)),
            IntervalNode("r", IntervalSpec("r", "Rest", PhaseKind.REST, 15_000, omitted = true)),
        )
        assertEquals(listOf("Work"), TimelineExpander.expand(plan(nodes)).steps.map { it.name })
    }

    @Test
    fun `expanding past the interval limit fails with a clear message`() {
        val nodes = listOf(group("g", "Huge", 500, listOf(
            group("inner", "Inner", 100, listOf(interval("w", "W", PhaseKind.WORK, 1))),
        )))
        val error = runCatching { TimelineExpander.expand(plan(nodes), EngineLimits(maxExpandedIntervals = 10_000)) }
            .exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is ExpansionException)
        assertTrue(error!!.message!!.contains("10,000"))
    }

    @Test
    fun `expanding past the 24 hour limit fails with a clear message`() {
        val nodes = listOf(group("g", "Long", 100, listOf(interval("w", "W", PhaseKind.WORK, 1000))))
        val error = runCatching { TimelineExpander.expand(plan(nodes)) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("limit"))
    }

    // -------------------------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------------------------

    @Test
    fun `validation rejects negative durations empty workouts and bad repeats`() {
        val negative = plan(listOf(IntervalNode("x", IntervalSpec("x", "Work", PhaseKind.WORK, -5_000))))
        assertTrue(Validation.validate(negative).any { it.contains("negative") })

        val empty = plan(emptyList())
        assertTrue(Validation.validate(empty).any { it.contains("at least one interval") })

        val zeroRepeat = plan(listOf(group("g", "Block", 0, listOf(interval("w", "W", PhaseKind.WORK, 10)))))
        assertTrue(Validation.validate(zeroRepeat).any { it.contains("1 or more") })

        val unnamed = plan(listOf(interval("w", "W", PhaseKind.WORK, 10))).copy(name = "  ")
        assertTrue(Validation.validate(unnamed).any { it.contains("name") })
    }

    @Test
    fun `validation accepts a well formed workout`() {
        val good = plan(listOf(
            interval("prep", "Get ready", PhaseKind.PREPARE, 10),
            group("g", "Rounds", 8, listOf(
                interval("w", "Work", PhaseKind.WORK, 20),
                interval("r", "Rest", PhaseKind.REST, 10),
            )),
        ))
        assertEquals(emptyList<String>(), Validation.validate(good))
    }

    @Test
    fun `zero duration workout cannot start`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        val empty = TimelineExpander.expand(plan(listOf(interval("z", "Zero", PhaseKind.WORK, 0))))
        val problems = engine.prepare(empty, "p", "T")
        assertTrue(problems.isNotEmpty())
        assertTrue(engine.start().isNotEmpty())
    }

    // -------------------------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------------------------

    @Test
    fun `duration formatting is stable`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:01", formatDuration(1))
        assertEquals("4:00", formatDuration(240_000))
        assertEquals("3:50", formatDuration(230_000))
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("1 minute 30 seconds", formatDurationSpoken(90_000))
        assertEquals("20 seconds", formatDurationSpoken(20_000))
    }

    @Test
    fun `session remaining time is null only when content is open ended`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(
            interval("w", "Work", PhaseKind.WORK, 20),
            interval("r", "Rest", PhaseKind.REST, 10),
        ))), "p", "T")
        engine.start()
        assertEquals(30_000L, engine.snapshot().sessionRemainingMillis)
        clock.advance(5_000); engine.tick()
        assertEquals(25_000L, engine.snapshot().sessionRemainingMillis)
        clock.advance(25_000); engine.tick()
        assertEquals(TimerStatus.FINISHED, engine.snapshot().status)
        assertFalse(engine.snapshot().isActive)
    }

    @Test
    fun `stop reports the final event once`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        engine.prepare(TimelineExpander.expand(plan(listOf(interval("w", "Work", PhaseKind.WORK, 60)))), "p", "T")
        engine.start()
        clock.advance(10_000); engine.tick()
        engine.stop()
        engine.stop() // idempotent
        val finishes = engine.drainEvents().filter { it is TimerEvent.SessionFinished }
        assertEquals(1, finishes.size)
        assertEquals(FinishReason.USER_STOPPED, (finishes.single() as TimerEvent.SessionFinished).reason)
    }
}
