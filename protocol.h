#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <stddef.h>

#define SERVER_PORT 5050
#define MAX_MESSAGE_SIZE 256
#define MAX_LOG_SIZE 256

typedef enum
{
    CLASS_GUERREIRO = 0,
    CLASS_MAGO,
    CLASS_ARQUEIRO,
    CLASS_UNKNOWN = 99
} PlayerClass;

typedef enum
{
    ACTION_ATTACK = 0,
    ACTION_SKILL,
    ACTION_DEFEND,
    ACTION_HEAL,
    ACTION_INVALID = 99
} ActionType;

typedef struct
{
    int playerId;
    PlayerClass classe;
} JoinMessage;

typedef struct
{
    int turnFlag;
    int playerId;
    ActionType action;
    int skillIndex;
} ActionMessage;

PlayerClass class_from_char(char c);
const char *class_to_string(PlayerClass classe);
ActionType action_from_char(char c);
const char *action_to_string(ActionType action);

int parse_join_message(const char *msg, JoinMessage *out);
int parse_action_message(const char *msg, ActionMessage *out);
int parse_quit_message(const char *msg, int *playerId);
int is_ping_message(const char *msg);
int is_scoreboard_request(const char *msg);

int format_turn_snapshot(char *buffer, size_t buflen,
                         int turnFlag,
                         int hp1, int mp1, int cdS1, int cdH1,
                         int hp2, int mp2, int cdS2, int cdH2,
                         const char *logLine);

#endif /* PROTOCOL_H */

