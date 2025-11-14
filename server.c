#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>

#include "game.h"
#include "net.h"
#include "protocol.h"
#include "queue.h"
#include "sharedmem.h"

typedef struct ServerContext ServerContext;

typedef struct
{
    ServerContext *server;
    SOCKET socket;
    int id;
    int connected;
    int disconnected;
    PlayerClass classe;
    MessageQueue queue;
} ClientContext;

struct ServerContext
{
    SOCKET listenSock;
    ClientContext clients[2];
    SharedMemory shared;
    GameState game;
    FILE *logFile;
    HANDLE logMutex;
    int matchRunning;
    int shuttingDown;
};

static void append_log(ServerContext *server, const char *fmt, ...)
{
    if (!server || !server->logFile)
        return;

    WaitForSingleObject(server->logMutex, INFINITE);
    va_list args;
    va_start(args, fmt);

    time_t now = time(NULL);
    struct tm tm_now;
    localtime_s(&tm_now, &now);
    char ts[32];
    strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", &tm_now);
    fprintf(server->logFile, "[%s] ", ts);
    vfprintf(server->logFile, fmt, args);
    fprintf(server->logFile, "\n");
    fflush(server->logFile);

    va_end(args);
    ReleaseMutex(server->logMutex);
}

static void send_error(ClientContext *client, const char *message)
{
    char buffer[MAX_MESSAGE_SIZE];
    snprintf(buffer, sizeof(buffer), "X01|%s", message ? message : "Erro desconhecido");
    net_send_line(client->socket, buffer);
}

static void send_scoreboard(ServerContext *server, ClientContext *client)
{
    if (!sharedmem_lock(&server->shared))
        return;
    SharedState snapshot = *server->shared.state;
    sharedmem_unlock(&server->shared);

    char buffer[MAX_MESSAGE_SIZE];
    snprintf(buffer, sizeof(buffer),
             "B{\"G\":%d,\"M\":%d,\"R\":%d,\"Ativa\":%d,\"Ultimo\":\"%.60s\"}",
             snapshot.vitoriasClasse[CLASS_GUERREIRO],
             snapshot.vitoriasClasse[CLASS_MAGO],
             snapshot.vitoriasClasse[CLASS_ARQUEIRO],
             snapshot.partidasAtivas,
             snapshot.ultimoLog);
    net_send_line(client->socket, buffer);
}

static void broadcast(ServerContext *server, const char *message)
{
    for (int i = 0; i < 2; ++i)
    {
        ClientContext *client = &server->clients[i];
        if (client->connected)
            net_send_line(client->socket, message);
    }
}

static int process_client_message(ServerContext *server, ClientContext *client)
{
    char buffer[MAX_MESSAGE_SIZE];
    
    int result = net_recv_line(client->socket, buffer, sizeof(buffer));
    if (result == 0)
    {
        return 1;
    }
    if (result < 0)
    {
        append_log(server, "Cliente %d desconectado", client->id);
        client->connected = 0;
        client->disconnected = 1;
        queue_push(&client->queue, "Q");
        return 0;
    }

    append_log(server, "RX[%d]: %s", client->id, buffer);

    if (is_ping_message(buffer))
    {
        net_send_line(client->socket, "O");
        return 1;
    }

    if (is_scoreboard_request(buffer))
    {
        send_scoreboard(server, client);
        return 1;
    }

    if (buffer[0] == 'J' || buffer[0] == 'M')
    {
        if (!queue_push(&client->queue, buffer))
            send_error(client, "Fila cheia, tente novamente");
        return 1;
    }

    if (buffer[0] == 'Q')
    {
        queue_push(&client->queue, buffer);
        append_log(server, "Cliente %d solicitou saida", client->id);
        client->connected = 0;
        return 0;
    }

    send_error(client, "Mensagem desconhecida");
    return 1;
}

static int wait_for_join(ServerContext *server, ClientContext *client, PlayerClass *outClass)
{
    (void)server; /* Parâmetro reservado para uso futuro */
    char msg[MAX_MESSAGE_SIZE];
    
    DWORD startTime = GetTickCount();
    while (GetTickCount() - startTime < 60000)
    {
        if (!client->connected)
            return 0;
            
        if (queue_pop(&client->queue, msg, sizeof(msg), 0))
        {
            JoinMessage parsed;
            if (!parse_join_message(msg, &parsed))
                continue;
            if (parsed.playerId != client->id)
                continue;

            client->classe = parsed.classe;
            if (outClass)
                *outClass = parsed.classe;
            return 1;
        }
        
        Sleep(10);
    }
    return 0;
}

static TurnCommand default_defense(void)
{
    TurnCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.action = ACTION_DEFEND;
    cmd.autoAssigned = 1;
    return cmd;
}

static TurnCommand parse_turn_command(ClientContext *client, const char *msg, int expectedTurn)
{
    TurnCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.action = ACTION_DEFEND;

    ActionMessage parsed;
    if (!parse_action_message(msg, &parsed))
    {
        send_error(client, "Formato de ação inválido");
        return cmd;
    }
    if (parsed.playerId != client->id)
    {
        send_error(client, "ID incorreto");
        return cmd;
    }
    if (parsed.turnFlag != expectedTurn)
    {
        send_error(client, "Turno incorreto");
        return cmd;
    }

    cmd.action = parsed.action;
    cmd.skillIndex = parsed.skillIndex;
    cmd.autoAssigned = 0;
    cmd.fallbackToAttack = 0;
    return cmd;
}

static int fetch_turn_command(ServerContext *server, ClientContext *client, int turnFlag, TurnCommand *outCmd)
{
    char msg[MAX_MESSAGE_SIZE];
    
    DWORD startTime = GetTickCount();
    while (GetTickCount() - startTime < 20000)
    {
        if (!client->connected || client->disconnected)
            return 0;
            
        if (queue_pop(&client->queue, msg, sizeof(msg), 0))
        {
            if (msg[0] == 'Q')
            {
                append_log(server, "Jogador %d saiu", client->id);
                client->connected = 0;
                client->disconnected = 1;
                return 0;
            }

            *outCmd = parse_turn_command(client, msg, turnFlag);
            return 1;
        }
        
        Sleep(10);
    }
    
    if (client->disconnected)
        return 0;
    append_log(server, "Timeout para jogador %d, defesa aplicada", client->id);
    *outCmd = default_defense();
    return 1;
}

static void send_turn_snapshot(ServerContext *server, const TurnResult *result, int turnFlag)
{
    char buffer[MAX_MESSAGE_SIZE];
    format_turn_snapshot(buffer, sizeof(buffer),
                         turnFlag,
                         server->game.players[0].hp,
                         server->game.players[0].mp,
                         server->game.players[0].skillCooldown,
                         server->game.players[0].healCooldown,
                         server->game.players[1].hp,
                         server->game.players[1].mp,
                         server->game.players[1].skillCooldown,
                         server->game.players[1].healCooldown,
                         result->logLine);
    broadcast(server, buffer);
    append_log(server, "TX: %s", buffer);
    sharedmem_update_turn(&server->shared, result->logLine);
}

static void send_end_message(ServerContext *server, const TurnResult *result)
{
    char buffer[MAX_MESSAGE_SIZE];
    if (result->tie)
        snprintf(buffer, sizeof(buffer), "E0|reason:%s", result->reason);
    else
        snprintf(buffer, sizeof(buffer), "E%d|reason:%s", result->winnerId,
                 result->reason[0] ? result->reason : "Fim de jogo");
    broadcast(server, buffer);
    append_log(server, "TX: %s", buffer);
}

static void announce_class(ServerContext *server, int playerId, PlayerClass classe)
{
    char buffer[32];
    char classChar = 'G';
    switch (classe)
    {
    case CLASS_GUERREIRO:
        classChar = 'G';
        break;
    case CLASS_MAGO:
        classChar = 'M';
        break;
    case CLASS_ARQUEIRO:
        classChar = 'R';
        break;
    default:
        classChar = 'X';
        break;
    }
    snprintf(buffer, sizeof(buffer), "C%d%c", playerId, classChar);
    broadcast(server, buffer);
}

static void run_match(ServerContext *server)
{
    append_log(server, "Gerenciador de partida iniciado");

    net_send_line(server->clients[0].socket, "S1");
    net_send_line(server->clients[1].socket, "S2");

    PlayerClass classP1 = CLASS_UNKNOWN;
    PlayerClass classP2 = CLASS_UNKNOWN;

    if (!wait_for_join(server, &server->clients[0], &classP1))
    {
        append_log(server, "Jogador 1 não selecionou classe");
        broadcast(server, "X02|Jogador 1 nao escolheu classe");
        sharedmem_register_result(&server->shared, CLASS_UNKNOWN);
        return;
    }
    announce_class(server, 1, classP1);

    if (!wait_for_join(server, &server->clients[1], &classP2))
    {
        append_log(server, "Jogador 2 não selecionou classe");
        broadcast(server, "X02|Jogador 2 nao escolheu classe");
        sharedmem_register_result(&server->shared, CLASS_UNKNOWN);
        return;
    }
    announce_class(server, 2, classP2);

    sharedmem_update_turn(&server->shared, "Partida iniciada");
    game_init(&server->game, classP1, classP2);
    server->matchRunning = 1;

    int manualWinner = 0;
    int manualTie = 0;
    char manualReason[64] = "";

    while (!server->shuttingDown && server->matchRunning)
    {
        if (!server->clients[0].connected || !server->clients[1].connected)
        {
            append_log(server, "Um dos jogadores desconectou, encerrando partida");
            if (!server->clients[0].connected && server->clients[1].connected)
            {
                manualWinner = 2;
                strncpy(manualReason, "Jogador 1 desconectou", sizeof(manualReason) - 1);
            }
            else if (!server->clients[1].connected && server->clients[0].connected)
            {
                manualWinner = 1;
                strncpy(manualReason, "Jogador 2 desconectou", sizeof(manualReason) - 1);
            }
            else
            {
                manualTie = 1;
                strncpy(manualReason, "Ambos desconectaram", sizeof(manualReason) - 1);
            }
            break;
        }

        int turnFlag = (server->game.turnNumber % 2 == 0) ? 2 : 1;

        char turnMsg[16];
        snprintf(turnMsg, sizeof(turnMsg), "T%d", turnFlag);
        broadcast(server, turnMsg);
        append_log(server, "TX: %s", turnMsg);

        game_start_turn(&server->game);

        TurnCommand cmdP1 = default_defense();
        TurnCommand cmdP2 = default_defense();

        if (!fetch_turn_command(server, &server->clients[0], turnFlag, &cmdP1))
        {
            append_log(server, "Falha ao obter comando do jogador 1");
            if (!server->clients[0].connected && server->clients[1].connected)
            {
                manualWinner = 2;
                strncpy(manualReason, "Jogador 1 desconectou", sizeof(manualReason) - 1);
            }
            else
            {
                manualTie = 1;
                strncpy(manualReason, "Acao invalida do jogador 1", sizeof(manualReason) - 1);
            }
            break;
        }
        if (!fetch_turn_command(server, &server->clients[1], turnFlag, &cmdP2))
        {
            append_log(server, "Falha ao obter comando do jogador 2");
            if (!server->clients[1].connected && server->clients[0].connected)
            {
                manualWinner = 1;
                strncpy(manualReason, "Jogador 2 desconectou", sizeof(manualReason) - 1);
            }
            else
            {
                manualTie = 1;
                strncpy(manualReason, "Acao invalida do jogador 2", sizeof(manualReason) - 1);
            }
            break;
        }

        TurnResult result;
        game_apply_turn(&server->game, cmdP1, cmdP2, &result);
        send_turn_snapshot(server, &result, turnFlag);

        if (result.winnerId != 0 || result.tie)
        {
            send_end_message(server, &result);
            if (result.tie)
                sharedmem_register_result(&server->shared, CLASS_UNKNOWN);
            else
                sharedmem_register_result(&server->shared,
                                          server->game.players[result.winnerId - 1].classe);
            break;
        }
    }

    if ((manualWinner != 0 || manualTie) && server->matchRunning)
    {
        TurnResult endResult;
        memset(&endResult, 0, sizeof(endResult));
        endResult.winnerId = manualWinner;
        endResult.tie = manualTie;
        strncpy(endResult.reason, manualReason, sizeof(endResult.reason) - 1);
        send_end_message(server, &endResult);
        if (manualWinner != 0)
        {
            sharedmem_register_result(&server->shared,
                                      server->game.players[manualWinner - 1].classe);
        }
        else
        {
            sharedmem_register_result(&server->shared, CLASS_UNKNOWN);
        }
    }

    server->matchRunning = 0;
    append_log(server, "Partida encerrada");
}

static void init_client(ClientContext *client, ServerContext *server, SOCKET sock, int id)
{
    memset(client, 0, sizeof(ClientContext));
    client->server = server;
    client->socket = sock;
    client->id = id;
    client->connected = 1;
    queue_init(&client->queue);
    
    u_long mode = 1;
    ioctlsocket(sock, FIONBIO, &mode);
}

static void cleanup_client(ClientContext *client)
{
    if (!client)
        return;
    client->connected = 0;
    net_close_socket(client->socket);
    queue_destroy(&client->queue);
}

int main(void)
{
    ServerContext server;
    memset(&server, 0, sizeof(server));

    server.logMutex = CreateMutexA(NULL, FALSE, NULL);
    server.logFile = fopen("server.log", "a");
    if (!server.logFile)
    {
        fprintf(stderr, "Nao foi possivel abrir server.log\n");
        return EXIT_FAILURE;
    }

    if (!net_initialize())
    {
        fprintf(stderr, "Falha ao inicializar rede\n");
        return EXIT_FAILURE;
    }

    if (!sharedmem_init(&server.shared, 1))
    {
        fprintf(stderr, "Falha ao inicializar memoria compartilhada\n");
        return EXIT_FAILURE;
    }

    server.listenSock = net_create_server_socket(SERVER_PORT);
    if (server.listenSock == INVALID_SOCKET)
    {
        fprintf(stderr, "Falha ao abrir socket do servidor\n");
        return EXIT_FAILURE;
    }

    append_log(&server, "Servidor iniciado na porta %d", SERVER_PORT);

    for (int i = 0; i < 2; ++i)
    {
        SOCKET clientSock = net_accept_client(server.listenSock);
        if (clientSock == INVALID_SOCKET)
        {
            fprintf(stderr, "Falha ao aceitar cliente\n");
            return EXIT_FAILURE;
        }

        init_client(&server.clients[i], &server, clientSock, i + 1);
        char welcome[16];
        snprintf(welcome, sizeof(welcome), "W%d", i + 1);
        net_send_line(clientSock, welcome);
    }

    fd_set readfds;
    struct timeval timeout;
    
    while (!server.shuttingDown)
    {
        FD_ZERO(&readfds);
        FD_SET(server.listenSock, &readfds);
        int maxfd = (int)server.listenSock;
        
        for (int i = 0; i < 2; ++i)
        {
            if (server.clients[i].connected && server.clients[i].socket != INVALID_SOCKET)
            {
                FD_SET(server.clients[i].socket, &readfds);
                if ((int)server.clients[i].socket > maxfd)
                    maxfd = (int)server.clients[i].socket;
            }
        }

        timeout.tv_sec = 0;
        timeout.tv_usec = 100000;
        
        int activity = select(0, &readfds, NULL, NULL, &timeout);
        
        if (activity == SOCKET_ERROR)
        {
            int err = WSAGetLastError();
            if (err != WSAEINTR)
            {
                append_log(&server, "Erro no select: %d", err);
                break;
            }
            continue;
        }

        for (int i = 0; i < 2; ++i)
        {
            if (server.clients[i].connected && 
                server.clients[i].socket != INVALID_SOCKET &&
                FD_ISSET(server.clients[i].socket, &readfds))
            {
                process_client_message(&server, &server.clients[i]);
            }
        }

        if (!server.matchRunning && server.clients[0].connected && server.clients[1].connected)
        {
            run_match(&server);
        }

        if (!server.clients[0].connected && !server.clients[1].connected)
            break;
    }

    server.shuttingDown = 1;

    for (int i = 0; i < 2; ++i)
    {
        cleanup_client(&server.clients[i]);
    }

    net_close_socket(server.listenSock);
    sharedmem_close(&server.shared);
    net_cleanup();

    if (server.logFile)
        fclose(server.logFile);

    if (server.logMutex)
        CloseHandle(server.logMutex);
    return EXIT_SUCCESS;
}
