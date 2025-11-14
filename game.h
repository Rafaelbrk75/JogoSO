#ifndef GAME_H
#define GAME_H

#include "protocol.h"

typedef struct
{
    ActionType action;
    int skillIndex;
    int autoAssigned;
    int fallbackToAttack;
} TurnCommand;

typedef struct
{
    PlayerClass classe;
    int hp;
    int mp;
    int skillCooldown;
    int healCooldown;
} PlayerState;

typedef struct
{
    PlayerState players[2];
    int turnNumber;
} GameState;

typedef struct
{
    int damageTaken[2];
    int healed[2];
    ActionType executed[2];
    int fallback[2];
    int autoAssigned[2];
    int winnerId;
    int tie;
    char reason[64];
    char logLine[256];
} TurnResult;

void game_init(GameState *state, PlayerClass classP1, PlayerClass classP2);
void game_start_turn(GameState *state);
void game_apply_turn(GameState *state,
                     TurnCommand cmdP1,
                     TurnCommand cmdP2,
                     TurnResult *result);
int game_is_over(const GameState *state);

#endif /* GAME_H */

