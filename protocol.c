#include "protocol.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

static int is_digit(char c)
{
    return c >= '0' && c <= '9';
}

PlayerClass class_from_char(char c)
{
    switch (toupper((unsigned char)c))
    {
    case 'G':
        return CLASS_GUERREIRO;
    case 'M':
        return CLASS_MAGO;
    case 'R':
        return CLASS_ARQUEIRO;
    default:
        return CLASS_UNKNOWN;
    }
}

const char *class_to_string(PlayerClass classe)
{
    switch (classe)
    {
    case CLASS_GUERREIRO:
        return "Guerreiro";
    case CLASS_MAGO:
        return "Mago";
    case CLASS_ARQUEIRO:
        return "Arqueiro";
    default:
        return "Desconhecido";
    }
}

ActionType action_from_char(char c)
{
    switch (toupper((unsigned char)c))
    {
    case 'A':
        return ACTION_ATTACK;
    case 'S':
        return ACTION_SKILL;
    case 'D':
        return ACTION_DEFEND;
    case 'H':
        return ACTION_HEAL;
    default:
        return ACTION_INVALID;
    }
}

const char *action_to_string(ActionType action)
{
    switch (action)
    {
    case ACTION_ATTACK:
        return "Ataque";
    case ACTION_SKILL:
        return "Skill";
    case ACTION_DEFEND:
        return "Defesa";
    case ACTION_HEAL:
        return "Cura";
    default:
        return "Invalida";
    }
}

int parse_join_message(const char *msg, JoinMessage *out)
{
    if (!msg || !out)
        return 0;
    if (msg[0] != 'J')
        return 0;
    if (!is_digit(msg[1]))
        return 0;
    out->playerId = msg[1] - '0';
    out->classe = class_from_char(msg[2]);
    return out->classe != CLASS_UNKNOWN;
}

int parse_action_message(const char *msg, ActionMessage *out)
{
    if (!msg || !out)
        return 0;
    if (msg[0] != 'M')
        return 0;

    size_t len = strlen(msg);
    if (len < 4)
        return 0;

    if (!is_digit(msg[1]) || !is_digit(msg[2]))
        return 0;

    out->turnFlag = msg[1] - '0';
    out->playerId = msg[2] - '0';
    out->action = action_from_char(msg[3]);
    out->skillIndex = 0;

    if (out->action == ACTION_INVALID)
        return 0;

    if (out->action == ACTION_SKILL || out->action == ACTION_HEAL)
    {
        if (len < 5 || !is_digit(msg[4]))
            return 0;
        out->skillIndex = msg[4] - '0';
    }

    return 1;
}

int parse_quit_message(const char *msg, int *playerId)
{
    if (!msg || msg[0] != 'Q')
        return 0;
    if (playerId)
    {
        if (is_digit(msg[1]))
            *playerId = msg[1] - '0';
        else
            *playerId = -1;
    }
    return 1;
}

int is_ping_message(const char *msg)
{
    return msg && msg[0] == 'P';
}

int is_scoreboard_request(const char *msg)
{
    return msg && msg[0] == 'G';
}

int format_turn_snapshot(char *buffer, size_t buflen,
                         int turnFlag,
                         int hp1, int mp1, int cdS1, int cdH1,
                         int hp2, int mp2, int cdS2, int cdH2,
                         const char *logLine)
{
    if (!buffer || buflen == 0)
        return 0;

    int written = snprintf(buffer, buflen,
                           "R%d|H1:%d/%d|H2:%d/%d|C1:%d/%d|C2:%d/%d|L:%s",
                           turnFlag,
                           hp1, mp1,
                           hp2, mp2,
                           cdS1, cdH1,
                           cdS2, cdH2,
                           logLine ? logLine : "-");
    if (written < 0 || (size_t)written >= buflen)
        return 0;
    return 1;
}

