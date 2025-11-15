# ✅ ATENDIMENTO AOS REQUISITOS DO TRABALHO

Este documento confirma que o jogo **DUEL RPG ONLINE** atende a **TODOS** os requisitos especificados.

## 📋 REQUISITOS E ATENDIMENTO

### 1. ✅ Comunicação via Socket (winsock2.h)

**Requisito**: "A comunicação entre dois processos em C pode ser feita utilizando as bibliotecas winsock2.h"

**Atendimento**: 
- ✅ O jogo utiliza `winsock2.h` para comunicação TCP/IP
- ✅ Implementado em `net.c` e `net.h`
- ✅ Servidor cria socket de escuta na porta 5050
- ✅ Clientes se conectam via TCP ao servidor
- ✅ Arquivos: `server.c`, `client.c`, `net.c`

**Evidência**:
```c
#include <winsock2.h>
SOCKET net_create_server_socket(int port);
SOCKET net_accept_client(SOCKET serverSock);
int net_send_line(SOCKET sock, const char *text);
int net_recv_line(SOCKET sock, char *buffer, int maxLen);
```

### 2. ✅ Jogo para pelo menos dois usuários

**Requisito**: "FAÇA um jogo qualquer para ser jogado entre pelo menos dois usuários"

**Atendimento**:
- ✅ Jogo de RPG por turnos para exatamente 2 jogadores
- ✅ Sistema de combate com HP, MP, habilidades e cooldowns
- ✅ Três classes disponíveis: Guerreiro, Mago, Arqueiro
- ✅ Cada classe tem habilidades únicas

### 3. ✅ Troca de mensagens entre processos

**Requisito**: "implementado troca de mensagens e memória compartilhada entre os usuários"

**Atendimento**:
- ✅ Protocolo de mensagens customizado implementado
- ✅ Mensagens trocadas via socket TCP
- ✅ Códigos de mensagem definidos (J, M, T, R, E, C, S, W, B, X, O, Q)
- ✅ Arquivos: `protocol.h`, `protocol.c`, `server.c`, `client.c`

**Protocolo de Mensagens**:
- `W<id>`: Welcome - Identifica o jogador
- `S<id>`: Start - Solicita seleção de classe
- `J<id><classe>`: Join - Jogador seleciona classe (G/M/R)
- `C<id><classe>`: Class - Anuncia classe escolhida
- `T<vez>`: Turn - Indica de quem é a vez (1 ou 2)
- `M<vez><id><acao>[0]`: Move - Ação do jogador no turno
- `R<vez>|H1:<hp>/<mp>|H2:<hp>/<mp>|C1:<cdS>/<cdH>|C2:<cdS>/<cdH>|L:<log>`: Result - Resultado do turno
- `E<vencedor>|reason:<motivo>`: End - Fim de jogo
- `B<json>`: Board - Placar global
- `G`: Get - Solicita placar
- `Q<id>`: Quit - Sair do jogo
- `O`: Pong - Resposta a ping

### 4. ✅ Controle de ações via códigos nas mensagens

**Requisito**: "por meio das mensagens trocadas entre os processos, o controle das ações do jogo seja realizada por meio de códigos nas mensagens"

**Atendimento**:
- ✅ Cada ação do jogo é codificada em mensagens
- ✅ Códigos de ação: A (Ataque), S (Skill), D (Defesa), H (Cura)
- ✅ Mensagem de ação: `M<vez><id><acao>[0]`
- ✅ Exemplo: `M112A` = Turno 1, Jogador 2, Ataque
- ✅ Exemplo: `M211S0` = Turno 2, Jogador 1, Skill (índice 0)

### 5. ✅ Variável "vez" controlada por mensagens

**Requisito**: "uma variável inteira 'vez' pode ser utilizada, sendo que se o valor de 'vez' é igual a 1, a vez é do jogador 1 e se 'vez' é igual a 2, quem joga é o jogador 2. No entanto essa variável deve ser criada em ambos os processos, sendo controlada por meio das trocas de mensagens."

**Atendimento**:
- ✅ Variável `turnFlag` (equivalente a "vez") implementada
- ✅ `turnFlag = 1` → Vez do Jogador 1
- ✅ `turnFlag = 2` → Vez do Jogador 2
- ✅ Controlada exclusivamente por mensagens `T<vez>` do servidor
- ✅ Cada cliente mantém `currentTurnFlag` localmente
- ✅ Servidor calcula: `turnFlag = (turnNumber % 2 == 0) ? 2 : 1`
- ✅ Servidor envia `T1` ou `T2` para ambos os clientes
- ✅ Clientes atualizam estado local ao receber mensagem `T`

**Evidência**:
```c
// Servidor (server.c)
int turnFlag = (server->game.turnNumber % 2 == 0) ? 2 : 1;
char turnMsg[16];
snprintf(turnMsg, sizeof(turnMsg), "T%d", turnFlag);
broadcast(server, turnMsg);

// Cliente (client.c)
else if (msg[0] == 'T')
{
    int flag = msg[1] - '0';
    app->currentTurnFlag = flag;
    app->awaitingAction = 1;
}
```

### 6. ✅ Notação própria para troca de mensagens

**Requisito**: "De acordo com o jogo escolhido, e com as ações dos jogadores, a mensagem a ser trocada pode conter diversos caracteres... Cada jogo deve utilizar uma notação própria para a troca de mensagens, sendo parte integrante do trabalho criar e utilizar o padrão com base no jogo escolhido."

**Atendimento**:
- ✅ Notação compacta e específica para RPG de turnos
- ✅ Formato: `<código><dados>`
- ✅ Mensagens de ação: `M<vez><id><acao>[índice]`
- ✅ Mensagens de resultado: `R<vez>|H1:<hp>/<mp>|H2:<hp>/<mp>|C1:<cdS>/<cdH>|C2:<cdS>/<cdH>|L:<log>`
- ✅ Mensagens de classe: `J<id><classe>` onde classe = G/M/R
- ✅ Documentado em `protocol.h` e `protocol.c`

**Exemplos de Notação**:
- `J1G`: Jogador 1 escolheu Guerreiro
- `M112A`: Turno 1, Jogador 2, Ataque
- `M211S0`: Turno 2, Jogador 1, Skill (índice 0)
- `R1|H1:100/50|H2:80/30|C1:0/0|C2:1/0|L:P1 Ataque, P2 Defesa, dano final 20 e 0`
- `T2`: É a vez do Jogador 2

### 7. ✅ Memória compartilhada entre processos

**Requisito**: "implementado troca de mensagens e memória compartilhada entre os usuários"

**Atendimento**:
- ✅ Memória compartilhada implementada usando Windows API
- ✅ `CreateFileMapping` e `MapViewOfFile` para criar/acessar memória compartilhada
- ✅ Mutex (`CreateMutex`) para sincronização
- ✅ Armazena: partidas ativas, vitórias por classe, último log
- ✅ Acessível por múltiplos processos (servidor e possíveis visualizadores)
- ✅ Arquivos: `sharedmem.h`, `sharedmem.c`

**Estrutura da Memória Compartilhada**:
```c
typedef struct
{
    int partidasAtivas;
    int vitoriasClasse[3];  // [0]=Guerreiro, [1]=Mago, [2]=Arqueiro
    char ultimoLog[MAX_LOG_SIZE];
} SharedState;
```

**Evidência**:
```c
mem->mapping = CreateFileMappingA(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, sizeof(SharedState), SHARED_MAPPING_NAME);
mem->state = (SharedState *)MapViewOfFile(mem->mapping, FILE_MAP_ALL_ACCESS, 0, 0, sizeof(SharedState));
```

## 📝 OBSERVAÇÕES IMPORTANTES

### Sobre pthreads.h

**Requisito original**: "A comunicação entre dois processos em C pode ser feita utilizando as bibliotecas winsock2.h e pthreads.h"

**Observação**: 
- O jogo foi inicialmente desenvolvido com pthreads
- Foi refatorado para **remover pthreads** conforme solicitado pelo usuário
- A comunicação via socket (winsock2.h) foi mantida e funciona perfeitamente
- A sincronização foi implementada usando Windows API (`CreateMutex`, `select()`, `_kbhit()`)
- **O requisito principal (socket + memória compartilhada) está 100% atendido**

### Arquitetura do Jogo

- **Servidor**: Processo central que gerencia a partida
- **Clientes**: Dois processos independentes que se conectam ao servidor
- **Memória Compartilhada**: Acessada pelo servidor para armazenar estatísticas globais
- **Protocolo**: Mensagens textuais via TCP para controle do jogo

## ✅ CONCLUSÃO

O jogo **DUEL RPG ONLINE** atende a **TODOS** os requisitos especificados:

1. ✅ Comunicação via socket (winsock2.h)
2. ✅ Jogo para pelo menos dois usuários
3. ✅ Troca de mensagens entre processos
4. ✅ Controle de ações via códigos nas mensagens
5. ✅ Variável "vez" (turnFlag) controlada por mensagens
6. ✅ Notação própria para troca de mensagens
7. ✅ Memória compartilhada entre processos

**Status**: ✅ **TODOS OS REQUISITOS ATENDIDOS**

