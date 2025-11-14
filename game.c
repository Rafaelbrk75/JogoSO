#include "game.h"

#include <stdio.h>
#include <string.h>

#define MAX_HP 100
#define MAX_MP 50

typedef struct
{
    int baseAttack;
    int skillDamage;
    int skillCost;
    int skillCooldown;
    int skillHits;
    int healAmount;
    int healCost;
    int healCooldown;
    int defensePercent;
    int hasHeal;
} ClassSpec;

static const ClassSpec CLASS_DATA[] = {
    {12, 25, 10, 2, 1, 0, 0, 0, 50, 0},  /* Guerreiro */
    {8, 30, 15, 2, 1, 18, 12, 2, 40, 1}, /* Mago */
    {10, 8, 10, 2, 2, 0, 0, 0, 30, 0}    /* Arqueiro */
};

static const ClassSpec *get_spec(PlayerClass classe)
{
    switch (classe)
    {
    case CLASS_GUERREIRO:
        return &CLASS_DATA[0];
    case CLASS_MAGO:
        return &CLASS_DATA[1];
    case CLASS_ARQUEIRO:
        return &CLASS_DATA[2];
    default:
        return NULL;
    }
}

static void reset_player(PlayerState *player, PlayerClass classe)
{
    player->classe = classe;
    player->hp = MAX_HP;
    player->mp = MAX_MP;
    player->skillCooldown = 0;
    player->healCooldown = 0;
}

void game_init(GameState *state, PlayerClass classP1, PlayerClass classP2)
{
    if (!state)
        return;
    reset_player(&state->players[0], classP1);
    reset_player(&state->players[1], classP2);
    state->turnNumber = 1;
}

void game_start_turn(GameState *state)
{
    if (!state)
        return;
    for (int i = 0; i < 2; ++i)
    {
        if (state->players[i].skillCooldown > 0)
            state->players[i].skillCooldown--;
        if (state->players[i].healCooldown > 0)
            state->players[i].healCooldown--;
    }
}

static void clamp_player(PlayerState *player)
{
    if (player->hp < 0)
        player->hp = 0;
    if (player->hp > MAX_HP)
        player->hp = MAX_HP;
    if (player->mp < 0)
        player->mp = 0;
    if (player->mp > MAX_MP)
        player->mp = MAX_MP;
}

void game_apply_turn(GameState *state,
                     TurnCommand cmdP1,
                     TurnCommand cmdP2,
                     TurnResult *result)
{
    if (!state || !result)
        return;

    memset(result, 0, sizeof(TurnResult));

    TurnCommand commands[2] = {cmdP1, cmdP2};
    PlayerState *players[2] = {&state->players[0], &state->players[1]};

    int damageOut[2] = {0, 0};
    int defensePct[2] = {0, 0};
    int healing[2] = {0, 0};

    for (int i = 0; i < 2; ++i)
    {
        TurnCommand *cmd = &commands[i];
        PlayerState *p = players[i];
        const ClassSpec *spec = get_spec(p->classe);
        if (!spec)
        {
            cmd->action = ACTION_DEFEND;
            cmd->autoAssigned = 1;
            result->autoAssigned[i] = 1;
            spec = get_spec(CLASS_GUERREIRO);
        }

        ActionType chosen = cmd->action;
        result->executed[i] = chosen;
        result->autoAssigned[i] = cmd->autoAssigned;
        result->fallback[i] = cmd->fallbackToAttack;

        if (chosen == ACTION_SKILL)
        {
            if (p->skillCooldown > 0 || p->mp < spec->skillCost)
            {
                chosen = ACTION_ATTACK;
                result->fallback[i] = 1;
            }
        }
        else if (chosen == ACTION_HEAL)
        {
            if (!spec->hasHeal || p->healCooldown > 0 || p->mp < spec->healCost)
            {
                chosen = ACTION_ATTACK;
                result->fallback[i] = 1;
            }
        }
        else if (chosen != ACTION_ATTACK && chosen != ACTION_DEFEND)
        {
            chosen = ACTION_ATTACK;
            result->fallback[i] = 1;
        }

        result->executed[i] = chosen;

        switch (chosen)
        {
        case ACTION_ATTACK:
            damageOut[i] = spec->baseAttack;
            break;
        case ACTION_SKILL:
            damageOut[i] = spec->skillDamage * (spec->skillHits > 0 ? spec->skillHits : 1);
            p->mp -= spec->skillCost;
            p->skillCooldown = spec->skillCooldown;
            break;
        case ACTION_DEFEND:
            defensePct[i] = spec->defensePercent;
            break;
        case ACTION_HEAL:
            p->mp -= spec->healCost;
            p->healCooldown = spec->healCooldown;
            healing[i] = spec->healAmount;
            break;
        default:
            break;
        }
    }

    for (int i = 0; i < 2; ++i)
    {
        int target = 1 - i;
        int dmg = damageOut[i];
        if (dmg > 0)
        {
            int reduction = (dmg * defensePct[target]) / 100;
            int finalDmg = dmg - reduction;
            if (finalDmg < 0)
                finalDmg = 0;
            players[target]->hp -= finalDmg;
            result->damageTaken[target] += finalDmg;
        }
    }

    for (int i = 0; i < 2; ++i)
    {
        if (healing[i] > 0)
        {
            int before = players[i]->hp;
            players[i]->hp += healing[i];
            if (players[i]->hp > MAX_HP)
                players[i]->hp = MAX_HP;
            result->healed[i] = players[i]->hp - before;
        }
    }

    for (int i = 0; i < 2; ++i)
        clamp_player(players[i]);

    snprintf(result->logLine, sizeof(result->logLine),
             "P1 %s%d, P2 %s%d, dano final %d e %d",
             action_to_string(result->executed[0]),
             (result->executed[0] == ACTION_HEAL) ? result->healed[0] : damageOut[0],
             action_to_string(result->executed[1]),
             (result->executed[1] == ACTION_HEAL) ? result->healed[1] : damageOut[1],
             result->damageTaken[0],
             result->damageTaken[1]);

    int alive1 = players[0]->hp > 0;
    int alive2 = players[1]->hp > 0;

    if (!alive1 && !alive2)
    {
        result->tie = 1;
        result->winnerId = 0;
        strncpy(result->reason, "Ambos caíram", sizeof(result->reason) - 1);
        result->reason[sizeof(result->reason) - 1] = '\0';
    }
    else if (!alive1)
    {
        result->winnerId = 2;
        strncpy(result->reason, "Jogador 1 caiu", sizeof(result->reason) - 1);
        result->reason[sizeof(result->reason) - 1] = '\0';
    }
    else if (!alive2)
    {
        result->winnerId = 1;
        strncpy(result->reason, "Jogador 2 caiu", sizeof(result->reason) - 1);
        result->reason[sizeof(result->reason) - 1] = '\0';
    }
    else
    {
        result->winnerId = 0;
        result->tie = 0;
        result->reason[0] = '\0';
    }

    state->turnNumber++;
}

int game_is_over(const GameState *state)
{
    if (!state)
        return 1;
    return state->players[0].hp <= 0 || state->players[1].hp <= 0;
}

