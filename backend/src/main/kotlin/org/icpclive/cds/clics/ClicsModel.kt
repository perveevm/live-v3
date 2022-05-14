package org.icpclive.cds.clics

import kotlinx.datetime.Instant
import org.icpclive.api.ContestStatus
import org.icpclive.cds.clics.api.*
import org.icpclive.cds.clics.model.*
import org.icpclive.utils.getLogger
import java.awt.Color
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class ClicsModel {
    private val judgementTypes = mutableMapOf<String, ClicsJudgementTypeInfo>()
    val problems = mutableMapOf<String, ClicsProblemInfo>()
    private val organisations = mutableMapOf<String, ClicsOrganisationInfo>()
    val teams = mutableMapOf<String, ClicsTeamInfo>()
    private val submissionCdsIdToInt = mutableMapOf<String, Int>()
    val submissions = mutableMapOf<String, ClicsRunInfo>()
    private val judgements = mutableMapOf<String, Judgement>()

    var startTime = Instant.fromEpochMilliseconds(0)
    var contestLength = 5.hours
    var freezeTime = 4.hours
    var status = ContestStatus.BEFORE

    val contestInfo: ClicsContestInfo
        get() = ClicsContestInfo(
            problemsMap = problems,
            teams = teams.values.toList(),
            startTime = startTime,
            contestLength = contestLength,
            freezeTime = freezeTime,
            status = status
        )

    fun processContest(contest: Contest) {
        contest.start_time?.let { startTime = it }
        contestLength = contest.duration
        contest.scoreboard_freeze_duration?.let { freezeTime = contestLength - it }
    }

    fun processProblem(problem: Problem) {
        problems[problem.id] = ClicsProblemInfo(
            id = problem.ordinal - 1, // todo: это не может работать
            letter = problem.label,
            name = problem.name,
            color = problem.rgb?.let { Color.decode(it) } ?: Color.GRAY,
            testCount = problem.test_data_count
        )
    }

    fun processOrganization(organization: Organization) {
        organisations[organization.id] = ClicsOrganisationInfo(
            id = organization.id,
            name = organization.name,
            formalName = organization.formal_name ?: organization.name,
            logo = organization.logo.lastOrNull()?.href,
            hashtag = organization.twitter_hashtag
        )
        // todo: update team if something changed
    }

    fun processTeam(team: Team) {
        val id = team.id
        val teamOrganization = team.organization_id?.let { organisations[it] }
        teams[id] = ClicsTeamInfo(
            id = id.hashCode(),
            name = teamOrganization?.formalName ?: team.name,
            shortName = teamOrganization?.name ?: team.name,
            contestSystemId = id,
            groups = emptySet(),
            hashTag = teamOrganization?.hashtag,
            photo = team.photo.firstOrNull()?.href,
            video = team.video.firstOrNull()?.href,
            screens = team.desktop.map { it.href },
            cameras = team.webcam.map { it.href },
        )
    }

    fun processJudgementType(judgementType: JudgementType) {
        judgementTypes[judgementType.id] = ClicsJudgementTypeInfo(
            id = judgementType.id,
            isAccepted = judgementType.solved,
            isAddingPenalty = judgementType.penalty,
        )
        logger.info("Add judgementType $judgementType")
    }

    fun processSubmission(submission: Submission): ClicsRunInfo {
        val id = synchronized(submissionCdsIdToInt) {
            return@synchronized submissionCdsIdToInt.putIfAbsent(submission.id, submissionCdsIdToInt.size + 1)
                ?: submissionCdsIdToInt[submission.id]!!
        }
        val problem = problems[submission.problem_id]
            ?: throw IllegalStateException("Failed to load submission with problem_id ${submission.problem_id}")
        val team = teams[submission.team_id]
            ?: throw IllegalStateException("Failed to load submission with team_id ${submission.team_id}")
        val run = ClicsRunInfo(
            id = id,
            problem = problem,
            teamId = team.id,
            submissionTime = submission.contest_time
        )
        submissions[submission.id] = run
        return run
    }

    fun processJudgement(judgement: Judgement): ClicsRunInfo {
        val run = submissions[judgement.submission_id]
            ?: throw IllegalStateException("Failed to load judgment with submission_id ${judgement.submission_id}")
        judgements[judgement.id] = judgement
        if (run.time.milliseconds >= freezeTime) return run // TODO: why we can know it?
        judgement.end_contest_time?.let { run.lastUpdateTime = it.toLong(DurationUnit.MILLISECONDS) }
        judgement.judgement_type_id?.let { run.judgementType = judgementTypes[it] }
        logger.info("Process $judgement")
        return run
    }

    fun processRun(casesRun: Run): ClicsRunInfo {
        val judgement = judgements[casesRun.judgement_id]
            ?: throw IllegalStateException("Failed to load run with judgment_id ${casesRun.judgement_id}")
        val run = submissions[judgement.submission_id]
            ?: throw IllegalStateException("Failed to load run with judgment_id ${casesRun.judgement_id}, submission_id ${judgement.submission_id}")
        if (run.time.milliseconds >= freezeTime) return run // TODO: why we can know it?
        run.lastUpdateTime = casesRun.contest_time.toLong(DurationUnit.MILLISECONDS)
        val judgementType = judgementTypes[casesRun.judgement_type_id]
        if (judgementType?.isAccepted == true) { // may be WA runs also need to add
            run.passedCaseRun.add(casesRun.ordinal)
        }
        logger.info("$casesRun with verdict $judgementType")
        return run
    }

    fun processState(state: State) {
        status = when {
            state.ended != null -> ContestStatus.OVER
            state.started != null -> ContestStatus.RUNNING
            else -> ContestStatus.BEFORE
        }
    }

    companion object {
        val logger = getLogger(ClicsModel::class)
    }
}
