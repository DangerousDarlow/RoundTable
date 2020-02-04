package com.noicesoftware.roundtable.controllers

import com.noicesoftware.roundtable.model.Game
import com.noicesoftware.roundtable.model.Player
import com.noicesoftware.roundtable.model.PlayersResponse
import com.noicesoftware.roundtable.model.RedactedPlayer
import com.noicesoftware.roundtable.redis.GameRepository
import org.slf4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
@RequestMapping("game")
class GameController(
        val gameRepository: GameRepository,
        val logger: Logger
) {

    @PostMapping("create")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestHeader(PLAYER_HEADER) playerId: UUID, @RequestBody playerName: String): UUID {
        val player = Player(playerId, playerName)
        val game = Game(
                id = UUID.randomUUID(),
                created = OffsetDateTime.now(ZoneOffset.UTC),
                players = mapOf(player.id to player)
        )

        logger.info("Create game ${game.toLogStr()}: player ${player.toLogStr()}")

        gameRepository.set(game)
        return game.id
    }

    @PostMapping("{id}/join")
    fun join(@RequestHeader(PLAYER_HEADER) playerId: UUID, @PathVariable id: UUID, @RequestBody playerName: String) {
        val oldGame = gameRepository.get(id)
        val player = Player(playerId, playerName)

        logger.info("Join game ${oldGame.toLogStr()}: player ${player.toLogStr()}")

        if (oldGame.players.containsKey(player.id))
            return

        val newGame = oldGame.copy(players = oldGame.players.plus(player.id to player))
        gameRepository.set(newGame)
    }

    @GetMapping("{id}/players")
    fun getPlayers(@RequestHeader(PLAYER_HEADER) playerId: UUID, @PathVariable id: UUID): PlayersResponse {
        val game = gameRepository.get(id)

        if (!game.players.containsKey(playerId)) {
            logger.warn("Game ${game.toLogStr()} does not contain player ($playerId)")
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        return PlayersResponse(
                you = game.players[playerId],
                others = game.players.filter { it.key != playerId }.map { RedactedPlayer(it.value.name) })
    }

    companion object {
        const val PLAYER_HEADER = "x-player"
    }
}