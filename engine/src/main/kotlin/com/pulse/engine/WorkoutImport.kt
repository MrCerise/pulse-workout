package com.pulse.engine

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** Public exchange format, deliberately independent of database/backup serialization. */
object WorkoutImport {
    const val FORMAT = "pulse.workouts"
    const val VERSION = 1
    const val MAX_BYTES = 262_144
    const val MAX_WORKOUTS = 50
    private val limits = EngineLimits()
    private val json = Json

    data class Preview(val plans: List<WorkoutPlan>, val timelines: List<ExpandedTimeline>)

    /** Bounded even for document providers that cannot report a file size. Caller owns the stream. */
    fun readText(stream: InputStream): String {
        val buffer = ByteArray(MAX_BYTES + 1)
        var size = 0
        while (size < buffer.size) {
            val count = stream.read(buffer, size, buffer.size - size)
            if (count < 0) break
            if (count == 0) {
                val byte = stream.read()
                if (byte < 0) break
                buffer[size++] = byte.toByte()
            } else size += count
        }
        require(size <= MAX_BYTES) { "JSON must be at most 256 KiB." }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(buffer, 0, size)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Use a UTF-8 JSON file.", e)
        }
    }

    fun parse(input: String): Preview {
        require(input.length <= MAX_BYTES && input.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "JSON must be at most 256 KiB."
        }
        var text = input.trim().removePrefix("\uFEFF").trim()
        // AI chat responses often wrap the whole document in a single Markdown code fence.
        if (text.startsWith("```")) {
            val lines = text.lines()
            require(lines.first().lowercase() in listOf("```json", "```") && lines.last() == "```") {
                "Paste one JSON document, optionally inside a single json code fence."
            }
            text = lines.drop(1).dropLast(1).joinToString("\n")
        }
        require(text.isNotBlank()) { "Paste JSON or choose a JSON file first." }
        checkJsonDepth(text)
        val root = try {
            json.parseToJsonElement(text).obj("$")
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Invalid JSON. Check commas, quotes and brackets. ${e.message?.take(180)}", e)
        }
        root.keysOnly("$", "format", "version", "workouts")
        require(root.string("format", "$") == FORMAT) { "$.format must be \"$FORMAT\". For a full backup use Settings → Backup." }
        require(root.integer("version", "$") == VERSION) { "$.version must be $VERSION. This file version is not supported." }
        val workouts = root.array("workouts", "$")
        require(workouts.size in 1..MAX_WORKOUTS) { "$.workouts must contain 1–$MAX_WORKOUTS workouts." }
        val plans = workouts.mapIndexed { index, element ->
            val path = "$.workouts[$index]"
            val workout = element.obj(path)
            workout.keysOnly(path, "name", "description", "type", "equipment", "tags", "steps")
            val type = workout.optionalString("type", path, 40)?.let { value ->
                WorkoutType.entries.find { it.name == value }
                    ?: errorAt("$path.type", "use one of ${WorkoutType.entries.joinToString { it.name }}")
            } ?: WorkoutType.CUSTOM_SEQUENCE
            val tags = workout["tags"]?.let { value ->
                val array = value as? JsonArray ?: errorAt("$path.tags", "must be an array of strings")
                require(array.size <= 20) { "$path.tags allows at most 20 tags." }
                array.mapIndexed { i, tag -> tag.text("$path.tags[$i]", 40) }.distinct()
            }.orEmpty()
            WorkoutPlan(
                id = freshId(), name = workout.string("name", path, 120),
                description = workout.optionalString("description", path, 2000, allowBlank = true).orEmpty(),
                type = type, equipment = workout.optionalString("equipment", path, 200), tags = tags,
                nodes = steps(workout.array("steps", path), "$path.steps", 1),
            )
        }
        // The engine checks totals after expansion. Count symbolically first so a tiny document
        // with huge nested repeats cannot allocate millions of timeline entries before rejection.
        var batchCount = 0L
        plans.forEachIndexed { index, plan ->
            val cost = cost(plan.nodes, "$.workouts[$index]")
            batchCount += cost.first
            require(batchCount <= limits.maxExpandedIntervals) {
                "The import exceeds 10,000 expanded intervals in total. Split it into smaller files."
            }
        }
        val timelines = plans.mapIndexed { index, plan ->
            val problems = Validation.validate(plan, limits)
            require(problems.isEmpty()) { "$.workouts[$index]: ${problems.joinToString(" ")}" }
            TimelineExpander.expand(plan, limits)
        }
        return Preview(plans, timelines)
    }

    /** Pair of expanded interval count and timed milliseconds; bounded at each subtree. */
    private fun cost(nodes: List<Node>, path: String): Pair<Long, Long> {
        var count = 0L
        var millis = 0L
        nodes.forEach { node ->
            when (node) {
                is IntervalNode -> { count++; millis += node.interval.durationMillis }
                is RepeatGroup -> {
                    val child = cost(node.children, path)
                    count += child.first * node.repeat
                    millis += child.second * node.repeat
                }
            }
            require(count <= limits.maxExpandedIntervals) { "$path exceeds 10,000 expanded intervals." }
            require(millis <= limits.maxTotalTimedMillis) { "$path exceeds 24 hours of timed intervals." }
        }
        return count to millis
    }

    private fun steps(array: JsonArray, path: String, depth: Int): List<Node> {
        require(depth <= limits.maxNestingDepth) { "$path exceeds 8 nesting levels." }
        require(array.isNotEmpty()) { "$path must contain at least one step." }
        require(array.size <= limits.maxExpandedIntervals) { "$path has too many steps." }
        return array.mapIndexed { index, element ->
            val p = "$path[$index]"
            val step = element.obj(p)
            if ("repeat" in step || "steps" in step) {
                step.keysOnly(p, "name", "repeat", "steps")
                val repeat = step.integer("repeat", p)
                require(repeat in 1..limits.maxRepeatCount) { "$p.repeat must be 1–1,000." }
                RepeatGroup(freshId(), step.optionalString("name", p, 120) ?: "Block", repeat,
                    steps(step.array("steps", p), "$p.steps", depth + 1))
            } else {
                step.keysOnly(p, "name", "kind", "seconds", "manual", "notes", "speechText", "reps", "equipment", "sideLabel")
                val kindText = step.string("kind", p, 40)
                val kind = PhaseKind.entries.find { it.name == kindText }
                    ?: errorAt("$p.kind", "use one of ${PhaseKind.entries.joinToString { it.name }}")
                val manual = step["manual"]?.let {
                    val value = it as? JsonPrimitive
                    if (value == null || value.isString || value.booleanOrNull == null) errorAt("$p.manual", "must be true or false")
                    value.boolean
                } ?: false
                val seconds = if ("seconds" in step) step.integer("seconds", p) else if (manual) 0 else errorAt("$p.seconds", "is required for a timed interval")
                require(if (manual) seconds == 0 else seconds in 1..86400) {
                    "$p.seconds must be ${if (manual) "0 (or omitted) for a manual interval" else "1–86,400 whole seconds"}."
                }
                val reps = if ("reps" in step) step.integer("reps", p).also {
                    require(it in 1..10000) { "$p.reps must be 1–10,000." }
                } else null
                IntervalNode(freshId(), IntervalSpec(
                    id = freshId(), name = step.string("name", p, 120), kind = kind,
                    durationMillis = seconds.toLong() * 1000L, manualCompletion = manual,
                    notes = step.optionalString("notes", p, 2000, allowBlank = true),
                    speechText = step.optionalString("speechText", p, 500, allowBlank = true), reps = reps,
                    equipment = step.optionalString("equipment", p, 200), sideLabel = step.optionalString("sideLabel", p, 40),
                ))
            }
        }
    }

    // Guard before the recursive JSON parser is entered (including unknown/hostile fields).
    private fun checkJsonDepth(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEach { c ->
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 24) { "JSON is nested too deeply (maximum 8 step levels)." } }
                '}', ']' -> depth--
            }
        }
    }

    private fun freshId() = UUID.randomUUID().toString()
    private fun errorAt(path: String, message: String): Nothing = throw IllegalArgumentException("$path $message.")
    private fun JsonElement.obj(path: String) = this as? JsonObject ?: errorAt(path, "must be an object")
    private fun JsonObject.keysOnly(path: String, vararg keys: String) {
        val unknown = this.keys - keys.toSet()
        require(unknown.isEmpty()) { "$path: unknown field(s) ${unknown.joinToString()}. Check the format; nothing was imported." }
    }
    private fun JsonObject.array(key: String, path: String) = this[key] as? JsonArray ?: errorAt("$path.$key", "must be an array")
    private fun JsonElement.text(path: String, max: Int, allowBlank: Boolean = false): String {
        val value = this as? JsonPrimitive
        if (value == null || !value.isString) errorAt(path, "must be a string")
        val text = value.content.trim()
        require(text.length <= max && (allowBlank || text.isNotBlank())) { "$path must be ${if (allowBlank) "0" else "1"}–$max characters." }
        return text
    }
    private fun JsonObject.string(key: String, path: String, max: Int = 120): String =
        this[key]?.text("$path.$key", max) ?: errorAt("$path.$key", "is required")
    private fun JsonObject.optionalString(key: String, path: String, max: Int, allowBlank: Boolean = false): String? =
        this[key]?.text("$path.$key", max, allowBlank)
    private fun JsonObject.integer(key: String, path: String): Int {
        val value = this[key] as? JsonPrimitive
        if (value == null || value.isString || value.intOrNull == null) errorAt("$path.$key", "must be a whole JSON number")
        return value.int
    }

    val example = """
        {
          "format": "pulse.workouts",
          "version": 1,
          "workouts": [{
            "name": "Beginner bodyweight intervals",
            "type": "HIIT",
            "tags": ["bodyweight", "beginner"],
            "steps": [
              {"name": "Easy marching", "kind": "WARM_UP", "seconds": 120},
              {"name": "Circuit", "repeat": 4, "steps": [
                {"name": "Chair squats", "kind": "WORK", "seconds": 30, "notes": "Use a stable chair."},
                {"name": "Recover", "kind": "REST", "seconds": 30}
              ]},
              {"name": "Gentle walking", "kind": "COOLDOWN", "seconds": 120}
            ]
          }]
        }
    """.trimIndent()

    fun aiPrompt(request: String): String = """
        Create a workout for PULSE Interval Coach using my request below. Return ONLY one valid JSON document, no explanation or workout description. Do not include IDs, URLs, markdown or database fields. Treat this as a workout, not medical advice. Include appropriate warm-up/recovery and do not promise results.

        Required root: {"format":"pulse.workouts","version":1,"workouts":[...]}
        Each workout requires name (1–120 characters) and nonempty steps. Optional fields: type (one of ${WorkoutType.entries.joinToString { it.name }}; default CUSTOM_SEQUENCE), equipment (1–200 characters), tags (up to 20 strings, each 1–40 characters).
        A step is either an interval or a repeat group, never both.
        Interval: name (1–120 characters), kind (one of ${PhaseKind.entries.joinToString { it.name }}), seconds (whole number 1–86400). Optional: notes (up to 2000 characters), speechText (up to 500 characters), reps (1–10000), equipment (1–200 characters), sideLabel (1–40 characters). For a manual interval set manual:true and omit seconds or use 0; the user must advance it themselves.
        Group: {"name":"Circuit","repeat":4,"steps":[...intervals or groups...]}. Repeat is 1–1000. Name is optional. Rest must be an explicit interval; all repeated steps, including rest, run each round.
        No other fields or null values. At most 50 workouts, 8 step nesting levels, 10000 expanded intervals across the file, 24 hours timed duration per workout, and 256 KiB UTF-8 JSON. The type is a category, not a generator: explicitly include every interval needed for EMOM, Tabata, etc. Whole seconds only, not milliseconds.

        Example of the exact format:
        $example

        My request:
        ${request.trim().take(4000).ifBlank { "A beginner-friendly, ten-minute bodyweight workout with warm-up and cool-down." }}
    """.trimIndent()
}
