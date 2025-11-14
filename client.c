#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <conio.h>

#include <winsock2.h>
#include <ws2tcpip.h>

#include "net.h"
#include "protocol.h"

typedef struct
{
    SOCKET socket;
    int running;
    int playerId;
    PlayerClass classe;
    int awaitingClass;
    int awaitingAction;
    int currentTurnFlag;
} ClientState;

static void safe_print(const char *fmt, ...)
{
    va_list args;
    va_start(args, fmt);
    vprintf(fmt, args);
    va_end(args);
    fflush(stdout);
}

static void handle_result_message(ClientState *app, const char *msg)
{
    char copy[MAX_MESSAGE_SIZE];
    strncpy(copy, msg, sizeof(copy) - 1);
    copy[sizeof(copy) - 1] = '\0';

    char *tokens[6] = {0};
    int idx = 0;
    char *context = NULL;
    char *token = strtok_s(copy, "|", &context);
    while (token && idx < 6)
    {
        tokens[idx++] = token;
        token = strtok_s(NULL, "|", &context);
    }

    if (idx < 5)
        return;

    int turnFlag = atoi(tokens[0] + 1);
    int hp1 = 0, mp1 = 0, hp2 = 0, mp2 = 0;
    int cdS1 = 0, cdH1 = 0, cdS2 = 0, cdH2 = 0;
    sscanf(tokens[1], "H1:%d/%d", &hp1, &mp1);
    sscanf(tokens[2], "H2:%d/%d", &hp2, &mp2);
    sscanf(tokens[3], "C1:%d/%d", &cdS1, &cdH1);
    sscanf(tokens[4], "C2:%d/%d", &cdS2, &cdH2);

    const char *logLine = tokens[5] ? tokens[5] + 2 : "-";

    system("cls");
    safe_print("=== Duel RPG Online ===\n");
    safe_print("Turno sinalizado: %d\n", turnFlag);
    safe_print("Jogador 1: HP %3d | MP %3d | CD Skill %d | CD Cura %d\n", hp1, mp1, cdS1, cdH1);
    safe_print("Jogador 2: HP %3d | MP %3d | CD Skill %d | CD Cura %d\n", hp2, mp2, cdS2, cdH2);
    safe_print("Resumo: %s\n\n", logLine);

    app->awaitingAction = 0;
}

static void handle_end_message(ClientState *app, const char *msg)
{
    int winner = 0;
    char reason[128] = "";
    sscanf(msg, "E%d|reason:%127[^\n]", &winner, reason);
    safe_print("\n=== Fim de jogo ===\n");
    int pid = app->playerId;

    if (winner == 0)
        safe_print("Empate! Motivo: %s\n", reason);
    else if (winner == pid)
        safe_print("Vitoria sua! Motivo: %s\n", reason);
    else
        safe_print("Derrota. Motivo: %s\n", reason);

    app->running = 0;
}

static void handle_server_message(ClientState *app, const char *msg)
{
    if (msg[0] == 'W')
    {
        int id = msg[1] - '0';
        app->playerId = id;
        safe_print("Conectado como jogador %d. Aguarde oponente...\n", id);
    }
    else if (msg[0] == 'S')
    {
        int id = msg[1] - '0';
        if (id == app->playerId)
        {
            app->awaitingClass = 1;
            safe_print("Selecione sua classe (G=Guerreiro, M=Mago, R=Arqueiro):\n");
            return;
        }
    }
    else if (msg[0] == 'C')
    {
        int id = msg[1] - '0';
        PlayerClass clazz = class_from_char(msg[2]);
        safe_print("Jogador %d escolheu %s.\n", id, class_to_string(clazz));
        if (id == app->playerId)
        {
            app->classe = clazz;
            app->awaitingClass = 0;
        }
    }
    else if (msg[0] == 'T')
    {
        int flag = msg[1] - '0';
        app->currentTurnFlag = flag;
        app->awaitingAction = 1;
        safe_print("\nSeu turno sinalizado (%d). Escolha acao (A/S/D/H, F5 para placar, help para ajuda, Q para sair):\n", flag);
    }
    else if (msg[0] == 'R')
    {
        handle_result_message(app, msg);
    }
    else if (msg[0] == 'E')
    {
        handle_end_message(app, msg);
    }
    else if (msg[0] == 'B')
    {
        safe_print("\nPlacar global: %s\n", msg + 1);
    }
    else if (msg[0] == 'X')
    {
        safe_print("Erro do servidor: %s\n", msg + 2);
    }
}

static void show_help(void)
{
    safe_print("\nComandos disponiveis:\n"
               "  A - Ataque basico\n"
               "  S - Skill da classe\n"
               "  D - Defesa\n"
               "  H - Cura (apenas mago)\n"
               "  F5 ou G - Solicita placar global\n"
               "  help - Mostra esta ajuda\n"
               "  Q - Sair da partida\n\n");
}

static int process_input(ClientState *state, const char *line)
{
    size_t len = strlen(line);
    if (len > 0 && (line[len - 1] == '\n' || line[len - 1] == '\r'))
        len--;
    
    if (len == 0)
        return 1;

    char upperLine[64];
    for (size_t i = 0; i < len && i < sizeof(upperLine) - 1; ++i)
        upperLine[i] = (char)toupper((unsigned char)line[i]);
    upperLine[len] = '\0';

    if (strcmp(upperLine, "HELP") == 0)
    {
        show_help();
        return 1;
    }

    if (strcmp(upperLine, "F5") == 0 || strcmp(upperLine, "G") == 0)
    {
        net_send_line(state->socket, "G");
        return 1;
    }

    if (strcmp(upperLine, "Q") == 0)
    {
        if (state->playerId > 0)
        {
            char quitMsg[8];
            snprintf(quitMsg, sizeof(quitMsg), "Q%d", state->playerId);
            net_send_line(state->socket, quitMsg);
        }
        state->running = 0;
        return 0;
    }

    if (state->awaitingClass)
    {
        if (len != 1)
        {
            safe_print("Entrada invalida, use G, M ou R.\n");
            return 1;
        }
        PlayerClass clazz = class_from_char(upperLine[0]);
        if (clazz == CLASS_UNKNOWN)
        {
            safe_print("Classe invalida. Use G (Guerreiro), M (Mago) ou R (Arqueiro).\n");
            return 1;
        }
        char classChar = 'G';
        switch (clazz)
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
        char joinMsg[8];
        snprintf(joinMsg, sizeof(joinMsg), "J%d%c", state->playerId, classChar);
        if (net_send_line(state->socket, joinMsg))
            safe_print("Classe %s enviada. Aguarde inicio.\n", class_to_string(clazz));
        return 1;
    }

    if (state->awaitingAction)
    {
        if (len != 1)
        {
            safe_print("Acao invalida, use A S D ou H.\n");
            return 1;
        }
        ActionType action = action_from_char(upperLine[0]);
        if (action == ACTION_INVALID)
        {
            safe_print("Acao invalida, use A S D ou H.\n");
            return 1;
        }
        if (action == ACTION_HEAL && state->classe != CLASS_MAGO)
        {
            safe_print("Sua classe nao possui cura. Escolha outra acao.\n");
            return 1;
        }
        char actionChar = 'A';
        switch (action)
        {
        case ACTION_ATTACK:
            actionChar = 'A';
            break;
        case ACTION_SKILL:
            actionChar = 'S';
            break;
        case ACTION_DEFEND:
            actionChar = 'D';
            break;
        case ACTION_HEAL:
            actionChar = 'H';
            break;
        default:
            actionChar = 'A';
            break;
        }
        char actionMsg[16];
        if (action == ACTION_SKILL || action == ACTION_HEAL)
            snprintf(actionMsg, sizeof(actionMsg), "M%d%d%c0", state->currentTurnFlag, state->playerId, actionChar);
        else
            snprintf(actionMsg, sizeof(actionMsg), "M%d%d%c", state->currentTurnFlag, state->playerId, actionChar);
        if (net_send_line(state->socket, actionMsg))
            safe_print("Acao enviada: %c\n", upperLine[0]);
        state->awaitingAction = 0;
        return 1;
    }

    safe_print("Aguardando proximo evento do servidor...\n");
    return 1;
}

int main(int argc, char **argv)
{
    const char *host = "127.0.0.1";
    if (argc > 1)
        host = argv[1];

    if (!net_initialize())
    {
        fprintf(stderr, "Falha ao inicializar Winsock\n");
        return EXIT_FAILURE;
    }

    SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET)
    {
        fprintf(stderr, "Falha ao criar socket (%ld)\n", WSAGetLastError());
        net_cleanup();
        return EXIT_FAILURE;
    }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(SERVER_PORT);
    inet_pton(AF_INET, host, &addr.sin_addr);

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) == SOCKET_ERROR)
    {
        fprintf(stderr, "Nao foi possivel conectar ao servidor (%ld)\n", WSAGetLastError());
        closesocket(sock);
        net_cleanup();
        return EXIT_FAILURE;
    }

    u_long mode = 1;
    ioctlsocket(sock, FIONBIO, &mode);

    ClientState state;
    memset(&state, 0, sizeof(state));
    state.socket = sock;
    state.running = 1;

    fd_set readfds;
    struct timeval timeout;
    char line[64];

    while (state.running)
    {
        FD_ZERO(&readfds);
        FD_SET(sock, &readfds);

        timeout.tv_sec = 0;
        timeout.tv_usec = 100000;

        int activity = select(0, &readfds, NULL, NULL, &timeout);

        if (activity == SOCKET_ERROR)
        {
            int err = WSAGetLastError();
            if (err != WSAEINTR && err != WSAEWOULDBLOCK)
            {
                safe_print("Erro no select: %d\n", err);
                break;
            }
        }

        if (activity > 0 && FD_ISSET(sock, &readfds))
        {
            char buffer[MAX_MESSAGE_SIZE];
            int result = net_recv_line(sock, buffer, sizeof(buffer));
            if (result == 1)
            {
                handle_server_message(&state, buffer);
            }
            else if (result < 0)
            {
                safe_print("Conexao encerrada pelo servidor.\n");
                state.running = 0;
                break;
            }
        }

        if (_kbhit())
        {
            if (fgets(line, sizeof(line), stdin))
            {
                if (!process_input(&state, line))
                    break;
            }
            else
            {
                state.running = 0;
                break;
            }
        }
    }

    net_close_socket(sock);
    net_cleanup();

    return EXIT_SUCCESS;
}
