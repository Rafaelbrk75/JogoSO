#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <conio.h>

#include <winsock2.h>
#include <windows.h>
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
    int lastHp1, lastHp2, lastMp1, lastMp2;
    int needsRefresh;
} ClientState;

static HANDLE hConsole;

#define COLOR_YELLOW (FOREGROUND_RED | FOREGROUND_GREEN | FOREGROUND_INTENSITY)
#define COLOR_CYAN (FOREGROUND_GREEN | FOREGROUND_BLUE | FOREGROUND_INTENSITY)
#define COLOR_MAGENTA (FOREGROUND_RED | FOREGROUND_BLUE | FOREGROUND_INTENSITY)

static void init_console(void)
{
    hConsole = GetStdHandle(STD_OUTPUT_HANDLE);
    SetConsoleOutputCP(65001);
}

static void set_color(WORD color)
{
    SetConsoleTextAttribute(hConsole, color);
}

static void reset_color(void)
{
    SetConsoleTextAttribute(hConsole, FOREGROUND_RED | FOREGROUND_GREEN | FOREGROUND_BLUE);
}

static void safe_print(const char *fmt, ...)
{
    va_list args;
    va_start(args, fmt);
    vprintf(fmt, args);
    va_end(args);
    fflush(stdout);
}

static void print_header(void)
{
    set_color(FOREGROUND_BLUE | FOREGROUND_INTENSITY);
    safe_print("\n");
    safe_print("╔════════════════════════════════════════════════════════════╗\n");
    safe_print("║           ⚔️  DUEL RPG ONLINE  ⚔️                          ║\n");
    safe_print("╚════════════════════════════════════════════════════════════╝\n");
    reset_color();
}

static void print_separator(void)
{
    set_color(FOREGROUND_BLUE | FOREGROUND_INTENSITY);
    safe_print("──────────────────────────────────────────────────────────────\n");
    reset_color();
}

static void print_bar(int value, int max, int width, WORD color)
{
    set_color(color);
    int filled = (value * width) / max;
    if (filled > width) filled = width;
    for (int i = 0; i < filled; i++)
        safe_print("█");
    for (int i = filled; i < width; i++)
        safe_print("░");
    reset_color();
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

    int needsClear = (app->lastHp1 != hp1 || app->lastHp2 != hp2 || 
                      app->lastMp1 != mp1 || app->lastMp2 != mp2 || app->needsRefresh);
    
    if (needsClear)
    {
        system("cls");
        app->needsRefresh = 0;
        print_header();
    }
    
    app->lastHp1 = hp1;
    app->lastHp2 = hp2;
    app->lastMp1 = mp1;
    app->lastMp2 = mp2;
    
    set_color(COLOR_YELLOW);
    safe_print("                    TURNO: %d\n", turnFlag);
    reset_color();
    print_separator();
    
    safe_print("\n");
    set_color(COLOR_CYAN);
    safe_print("  ┌─ JOGADOR 1 ─────────────────────────────────────────────┐\n");
    reset_color();
    safe_print("  │ HP: ");
    print_bar(hp1, 100, 40, FOREGROUND_RED | FOREGROUND_INTENSITY);
    safe_print(" %3d/100 │\n", hp1);
    safe_print("  │ MP: ");
    print_bar(mp1, 50, 40, FOREGROUND_BLUE | FOREGROUND_INTENSITY);
    safe_print(" %3d/50  │\n", mp1);
    safe_print("  │ CD Skill: %d │ CD Cura: %d", cdS1, cdH1);
    if (cdS1 > 0)
    {
        set_color(FOREGROUND_RED);
        safe_print(" ⚠");
        reset_color();
    }
    if (cdH1 > 0)
    {
        set_color(FOREGROUND_RED);
        safe_print(" ⚠");
        reset_color();
    }
    safe_print("                    │\n");
    set_color(COLOR_CYAN);
    safe_print("  └────────────────────────────────────────────────────────┘\n");
    reset_color();
    
    safe_print("\n");
    set_color(COLOR_MAGENTA);
    safe_print("  ┌─ JOGADOR 2 ─────────────────────────────────────────────┐\n");
    reset_color();
    safe_print("  │ HP: ");
    print_bar(hp2, 100, 40, FOREGROUND_RED | FOREGROUND_INTENSITY);
    safe_print(" %3d/100 │\n", hp2);
    safe_print("  │ MP: ");
    print_bar(mp2, 50, 40, FOREGROUND_BLUE | FOREGROUND_INTENSITY);
    safe_print(" %3d/50  │\n", mp2);
    safe_print("  │ CD Skill: %d │ CD Cura: %d", cdS2, cdH2);
    if (cdS2 > 0)
    {
        set_color(FOREGROUND_RED);
        safe_print(" ⚠");
        reset_color();
    }
    if (cdH2 > 0)
    {
        set_color(FOREGROUND_RED);
        safe_print(" ⚠");
        reset_color();
    }
    safe_print("                    │\n");
    set_color(COLOR_MAGENTA);
    safe_print("  └────────────────────────────────────────────────────────┘\n");
    reset_color();
    
    print_separator();
    set_color(FOREGROUND_GREEN | FOREGROUND_INTENSITY);
    safe_print("  📋 Resumo: %s\n", logLine);
    reset_color();
    print_separator();
    safe_print("\n");

    app->awaitingAction = 0;
}

static void handle_end_message(ClientState *app, const char *msg)
{
    int winner = 0;
    char reason[128] = "";
    sscanf(msg, "E%d|reason:%127[^\n]", &winner, reason);
    
    system("cls");
    print_header();
    safe_print("\n");
    
    set_color(COLOR_YELLOW);
    safe_print("╔════════════════════════════════════════════════════════════╗\n");
    safe_print("║                    FIM DE JOGO                             ║\n");
    safe_print("╚════════════════════════════════════════════════════════════╝\n");
    reset_color();
    safe_print("\n");
    
    int pid = app->playerId;
    if (winner == 0)
    {
        set_color(COLOR_YELLOW);
        safe_print("  🎯 EMPATE!\n");
        reset_color();
    }
    else if (winner == pid)
    {
        set_color(FOREGROUND_GREEN | FOREGROUND_INTENSITY);
        safe_print("  🏆 VITÓRIA SUA!\n");
        reset_color();
    }
    else
    {
        set_color(FOREGROUND_RED | FOREGROUND_INTENSITY);
        safe_print("  💀 DERROTA\n");
        reset_color();
    }
    
    safe_print("  Motivo: %s\n\n", reason);
    app->running = 0;
}

static void handle_server_message(ClientState *app, const char *msg)
{
    if (msg[0] == 'W')
    {
        int id = msg[1] - '0';
        app->playerId = id;
        app->needsRefresh = 1;
        system("cls");
        print_header();
        set_color(FOREGROUND_GREEN | FOREGROUND_INTENSITY);
        safe_print("  ✅ Conectado como Jogador %d\n", id);
        reset_color();
        safe_print("  ⏳ Aguardando oponente...\n\n");
    }
    else if (msg[0] == 'S')
    {
        int id = msg[1] - '0';
        if (id == app->playerId)
        {
            app->awaitingClass = 1;
            set_color(COLOR_YELLOW);
            safe_print("\n  ⚔️  Selecione sua classe:\n");
            safe_print("  [G] Guerreiro  [M] Mago  [R] Arqueiro\n");
            reset_color();
            return;
        }
    }
    else if (msg[0] == 'C')
    {
        int id = msg[1] - '0';
        PlayerClass clazz = class_from_char(msg[2]);
        set_color(COLOR_CYAN);
        safe_print("  👤 Jogador %d escolheu: ", id);
        safe_print("%s\n", class_to_string(clazz));
        reset_color();
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
        set_color(COLOR_YELLOW);
        safe_print("\n  ⚡ SEU TURNO (Turno %d)!\n", flag);
        reset_color();
        safe_print("  Ações: [A] Ataque  [S] Skill  [D] Defesa  [H] Cura\n");
        safe_print("  [F5/G] Placar  [help] Ajuda  [Q] Sair\n");
        safe_print("  > ");
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
        set_color(COLOR_CYAN);
        safe_print("\n  📊 Placar Global: %s\n", msg + 1);
        reset_color();
    }
    else if (msg[0] == 'X')
    {
        set_color(FOREGROUND_RED | FOREGROUND_INTENSITY);
        safe_print("  ❌ Erro do servidor: %s\n", msg + 2);
        reset_color();
    }
}

static void show_help(void)
{
    set_color(COLOR_CYAN);
    safe_print("\n  📖 COMANDOS DISPONÍVEIS:\n");
    reset_color();
    safe_print("  [A] - Ataque básico\n");
    safe_print("  [S] - Skill da classe\n");
    safe_print("  [D] - Defesa\n");
    safe_print("  [H] - Cura (apenas Mago)\n");
    safe_print("  [F5] ou [G] - Solicita placar global\n");
    safe_print("  [help] - Mostra esta ajuda\n");
    safe_print("  [Q] - Sair da partida\n\n");
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

    if (state->awaitingClass)
    {
        if (len != 1)
        {
            set_color(FOREGROUND_RED);
            safe_print("  ❌ Entrada inválida, use G, M ou R.\n");
            reset_color();
            return 1;
        }
        PlayerClass clazz = class_from_char(upperLine[0]);
        if (clazz == CLASS_UNKNOWN)
        {
            set_color(FOREGROUND_RED);
            safe_print("  ❌ Classe inválida. Use G (Guerreiro), M (Mago) ou R (Arqueiro).\n");
            reset_color();
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
        state->awaitingClass = 0; // Desativa antes de enviar para evitar envios duplicados
        if (net_send_line(state->socket, joinMsg))
        {
            set_color(FOREGROUND_GREEN);
            safe_print("  ✅ Classe %s enviada. Aguarde início...\n", class_to_string(clazz));
            reset_color();
        }
        else
        {
            set_color(FOREGROUND_RED);
            safe_print("  ❌ Erro ao enviar classe. Tente novamente.\n");
            reset_color();
            state->awaitingClass = 1; // Reativa se falhou
        }
        return 1;
    }

    if (strcmp(upperLine, "F5") == 0 || strcmp(upperLine, "G") == 0)
    {
        net_send_line(state->socket, "G");
        return 1;
    }

    if (state->awaitingAction)
    {
        if (len != 1)
        {
            set_color(FOREGROUND_RED);
            safe_print("  ❌ Ação inválida, use A S D ou H.\n");
            reset_color();
            return 1;
        }
        ActionType action = action_from_char(upperLine[0]);
        if (action == ACTION_INVALID)
        {
            set_color(FOREGROUND_RED);
            safe_print("  ❌ Ação inválida, use A S D ou H.\n");
            reset_color();
            return 1;
        }
        if (action == ACTION_HEAL && state->classe != CLASS_MAGO)
        {
            set_color(FOREGROUND_RED);
            safe_print("  ❌ Sua classe não possui cura. Escolha outra ação.\n");
            reset_color();
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
        {
            set_color(FOREGROUND_GREEN);
            safe_print("  ✅ Ação enviada: %c\n", upperLine[0]);
            reset_color();
        }
        state->awaitingAction = 0;
        return 1;
    }

    set_color(COLOR_YELLOW);
    safe_print("  ⏳ Aguardando próximo evento do servidor...\n");
    reset_color();
    return 1;
}

int main(int argc, char **argv)
{
    const char *host = "127.0.0.1";
    if (argc > 1)
        host = argv[1];

    init_console();

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
    state.lastHp1 = -1;
    state.lastHp2 = -1;
    state.lastMp1 = -1;
    state.lastMp2 = -1;
    state.needsRefresh = 1;

    fd_set readfds;
    struct timeval timeout;
    char line[64];

    while (state.running)
    {
        FD_ZERO(&readfds);
        FD_SET(sock, &readfds);

        timeout.tv_sec = 0;
        timeout.tv_usec = 500000;

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
                set_color(FOREGROUND_RED | FOREGROUND_INTENSITY);
                safe_print("  ❌ Conexão encerrada pelo servidor.\n");
                reset_color();
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
        else
        {
            Sleep(50);
        }
    }

    net_close_socket(sock);
    net_cleanup();

    return EXIT_SUCCESS;
}
