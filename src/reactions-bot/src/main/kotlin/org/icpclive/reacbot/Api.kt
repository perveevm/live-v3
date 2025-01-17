package org.icpclive.reacbot

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Reactions : IntIdTable() {
    val teamId = Reactions.integer("teamId")
    val problemId = Reactions.integer("problemId")
    val runId = Reactions.integer("runID")
    val isOk = Reactions.bool("isOk")
    val fileName = Reactions.varchar("fileName", 200)
    val telegramFileId = Reactions.varchar("telegramFileId", 100).nullable().default(null)
    val rating = Reactions.integer("rating").default(0)
    val voteCount = Reactions.integer("voteCount").default(0)
}

class Reaction(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Reaction>(Reactions)

    var runId by Reactions.runId
    var problemId by Reactions.problemId
    var teamId by Reactions.teamId
    var isOk by Reactions.isOk
    var fileName by Reactions.fileName
    var telegramFileId by Reactions.telegramFileId
    var rating by Reactions.rating
    var voteCount by Reactions.voteCount
}

object Votes : IntIdTable() {
    val reactionId = Votes.integer("reactionId")
    val chatId = Votes.long("chatId")
    val vote = Votes.integer("vote").nullable()
}

class Vote(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Vote>(Votes)

    var runId by Votes.reactionId
    var chatId by Votes.chatId
    var vote by Votes.vote
}
