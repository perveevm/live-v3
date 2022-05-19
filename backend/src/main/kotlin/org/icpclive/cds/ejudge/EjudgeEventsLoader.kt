package org.icpclive.cds.ejudge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinInstant
import org.icpclive.api.ContestInfo
import org.icpclive.api.ContestStatus
import org.icpclive.api.MediaType
import org.icpclive.api.RunInfo
import org.icpclive.cds.ProblemInfo
import org.icpclive.config.Config
import org.icpclive.service.RegularLoaderService
import org.icpclive.service.launchEmulation
import org.icpclive.service.launchICPCServices
import org.icpclive.utils.getLogger
import org.icpclive.utils.guessDatetimeFormat
import org.icpclive.utils.humanReadable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * @author Mike Perveev
 */
class EjudgeEventsLoader {
    suspend fun run() {
        coroutineScope {
            val emulationSpeedProp: String? = properties.getProperty("emulation.speed")
            if (emulationSpeedProp == null) {
                val xmlLoaderFlow = MutableStateFlow(Document(""))
                launch(Dispatchers.IO) {
                    xmlLoader.run(xmlLoaderFlow, 5.seconds)
                }
                val rawRunsFlow = MutableSharedFlow<RunInfo>(
                    extraBufferCapacity = Int.MAX_VALUE,
                    onBufferOverflow = BufferOverflow.SUSPEND
                )
                launchICPCServices(rawRunsFlow, contestInfoFlow)
                xmlLoaderFlow.collect {
                    it.children().forEach {
                    }
                    if (it.children().size != 0) {
                        parseContestInfo(it.children()[0]) { runBlocking { rawRunsFlow.emit(it) } }
                        contestInfoFlow.value = contestData.toApi()
                        if (contestData.status == ContestStatus.RUNNING) {
                            logger.info("Updated for contest time = ${contestData.contestTime}")
                        }
                    }
                }
            } else {
                val allRuns = mutableListOf<RunInfo>()
                val element = xmlLoader.loadOnce()
                parseContestInfo(element.children()[0]) { allRuns.add(it) }
                if (contestData.status != ContestStatus.OVER) {
                    throw IllegalStateException("Emulation mode require over contest")
                }

                val emulationSpeed = emulationSpeedProp.toDouble()
                val emulationStartTime = guessDatetimeFormat(properties.getProperty("emulation.startTime"))
                logger.info("Running in emulation mode with speed x${emulationSpeed} and startTime = ${emulationStartTime.humanReadable}")
                launchEmulation(emulationStartTime, emulationSpeed, allRuns.toList(), contestData.toApi())
            }
        }
    }

    private fun parseProblemsInfo(): List<ProblemInfo> {
        val doc: Document
        runBlocking {
            doc = xmlLoader.loadOnce()
        }

        val config = doc.child(0)
        config.children().forEach {
            if ("problems" == it.tagName()) {
                return it.children().map { element ->
                    ProblemInfo(element.attr("short_name"), element.attr("short_name"))
                }
            }
        }

        logger.error("There is no <problems> tag in external XML log")
        return emptyList()
    }

    private fun parseTeamsInfo(problemsNumber: Int): List<EjudgeTeamInfo> {
        val doc: Document
        runBlocking {
            doc = xmlLoader.loadOnce()
        }

        val config = doc.child(0)
        config.children().forEach {
            if ("users" == it.tagName()) {
                return it.children().withIndex().map { (index, participant) ->
                    val participantName = participant.attr("name")
                    val alias = participant.attr("id")
                    val groups = mutableSetOf<String>()
                    val medias = mutableMapOf<MediaType, String>()
                    EjudgeTeamInfo(
                        index,
                        participantName,
                        participantName,
                        alias,
                        groups,
                        participantName,
                        medias,
                        problemsNumber)
                }
            }
        }

        logger.error("There is no <users> tag in external XML log")
        return emptyList()
    }

    private fun parseContestInfo(element: Element, onRunChanges: (RunInfo) -> Unit) {
        val dur = element.attr("duration").toLong()
        val startTime = LocalDateTime.parse(element.attr("start_time"), dateTimeFormat)
        val endTime = startTime.plusSeconds(dur)
        val currentTime = LocalDateTime.parse(element.attr("current_time"), dateTimeFormat)

        val status: ContestStatus = if (currentTime.isAfter(endTime) || currentTime.isEqual(endTime)) {
            ContestStatus.OVER
        } else if (currentTime.isBefore(startTime)) {
            ContestStatus.BEFORE
        } else {
            ContestStatus.RUNNING
        }

        if (status == ContestStatus.RUNNING && contestData.status !== ContestStatus.RUNNING) {
            // TODO: remove this scum
            contestData.startTime = Instant.ofEpochSecond(
                startTime.toEpochSecond(ZoneOffset.from(OffsetDateTime.now().offset))).toKotlinInstant()
        }
        contestData.status = status
        contestData.contestTime = Duration.between(startTime, currentTime).toKotlinDuration()

        element.children().forEach {
            if ("runs" == it.tagName()) {
                parseRuns(contestData, it, onRunChanges)
            }
        }
    }

    private fun parseRuns(
        contestInfo: EjudgeContestInfo,
        element: Element,
        onRunChanges: (RunInfo) -> Unit
    ) {
        if (contestInfo.status == ContestStatus.BEFORE) {
            return
        }
        element.children().forEach { run ->
            parseRunInfo(contestInfo, run, onRunChanges)
        }
    }

    private fun parseRunInfo(
        contestInfo: EjudgeContestInfo,
        element: Element,
        onRunChanges: (RunInfo) -> Unit
    ) {
        val time = (element.attr("time").toLong() * 1000).milliseconds
        if (time > contestInfo.contestTime) {
            return
        }

        val teamSystemId = element.attr("user_id").toInt()
        val teamId = contestInfo.getParticipantByContestSystemId(teamSystemId)!!.id
        val runId = element.attr("run_id").toInt()

        // Ejudge has 1-indexed problem numeration
        val problemId = element.attr("prob_id").toInt() - 1
        val isFrozen = time >= contestInfo.freezeTime
        val oldRun = contestInfo.teams[teamId].runs[problemId].getOrDefault(runId, null)
        val result = when {
            isFrozen -> ""
            else -> statusMap.getOrDefault(element.attr("status"), "WA")
        }
        val percentage = when {
            isFrozen -> 0.0
            "" == result -> 0.0
            else -> 1.0
        }

        val run = RunInfo(
            id = oldRun?.id ?: element.attr("run_id").toInt(),
            isAccepted = "AC" == result,
            isJudged = "" != result,
            isAddingPenalty = "AC" != result && "CE" != result,
            result = result,
            problemId = problemId,
            teamId = teamId,
            percentage = percentage,
            time = time.inWholeMilliseconds,
            isFirstSolvedRun = false
        )

        if (run != oldRun) {
            onRunChanges(run)
        }
        contestInfo.teams[teamId].runs[problemId][runId] = run
    }

    private var contestData: EjudgeContestInfo
    private var contestInfoFlow: MutableStateFlow<ContestInfo>
    private val properties: Properties = Config.loadProperties("events")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    private val xmlLoader = object : RegularLoaderService<Document>(null) {
        override val url = properties.getProperty("url")
        override fun processLoaded(data: String) = Jsoup.parse(data, "", Parser.xmlParser())
    }

    init {
        val problemsInfo = parseProblemsInfo()
        val teamsInfo = parseTeamsInfo(problemsInfo.size)

        problemsInfo.forEach { println(it.letter + " " + it.name + " " + it.color) }
        teamsInfo.forEach { println(it.id.toString() + " " + it.contestSystemId + " " + it.name) }

        contestData = EjudgeContestInfo(problemsInfo, teamsInfo, kotlinx.datetime.Instant.fromEpochMilliseconds(0), ContestStatus.UNKNOWN)
        contestData.contestLength = properties.getProperty("contest.length")?.toInt()?.milliseconds ?: 5.hours
        contestData.freezeTime = properties.getProperty("freeze.time")?.toInt()?.milliseconds ?: 4.hours
        contestInfoFlow = MutableStateFlow(contestData.toApi())
    }

    companion object {
        private val logger = getLogger(EjudgeEventsLoader::class)
        private val statusMap = mapOf(
            "OK" to "AC",
            "CE" to "CE",
            "RT" to "RE",
            "TL" to "TL",
            "PE" to "PE",
            "WA" to "WA",
            "CF" to "FL",
            "PT" to "",
            "AC" to "OK",
            "IG" to "",
            "DQ" to "",
            "PD" to "",
            "ML" to "ML",
            "SE" to "SV",
            "SV" to "",
            "WT" to "IL",
            "PR" to "",
            "RJ" to "",
            "SK" to "",
            "SY" to "",
            "SM" to "",
            "RU" to "",
            "CD" to "",
            "CG" to "",
            "AV" to "",
            "RJ" to "",
            "EM" to "",
            "VS" to "",
            "VT" to "",
        )
    }
}