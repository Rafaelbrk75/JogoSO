# COMUNICAÇÃO ENTRE PROCESSOS - DUEL RPG

## RESUMO

O sistema utiliza **dois mecanismos principais** para troca de informações entre processos:

1. **Sockets TCP/IP (Winsock)** - Comunicação cliente-servidor
2. **Memória Compartilhada (Windows File Mapping)** - Compartilhamento de estado global entre instâncias do servidor

---

## 1. COMUNICAÇÃO VIA SOCKETS (TCP/IP)

### 1.1. Configuração da Comunicação

- **Porta do Servidor**: `5050` (definida em `protocol.h`)
- **Protocolo**: TCP/IP (SOCK_STREAM)
- **Biblioteca**: Winsock2 (Windows Sockets API)

### 1.2. Mensagens do Cliente para o Servidor

#### **J - Mensagem de Join (Entrada no Jogo)**
```
Formato: J<playerId><classe>
Exemplo: J1G (Jogador 1 escolheu Guerreiro)
```
- **Campos**:
  - `playerId`: ID do jogador (1 ou 2)
  - `classe`: Classe escolhida (G=Guerreiro, M=Mago, R=Arqueiro)

#### **M - Mensagem de Ação (Turno)**
```
Formato: M<turnFlag><playerId><action>[<skillIndex>]
Exemplo: M011A (Turno 0, Jogador 1, Ataque)
Exemplo: M112S0 (Turno 1, Jogador 1, Skill índice 0)
```
- **Campos**:
  - `turnFlag`: Número do turno atual (0, 1, 2, ...)
  - `playerId`: ID do jogador (1 ou 2)
  - `action`: Tipo de ação (A=Ataque, S=Skill, D=Defesa, H=Cura)
  - `skillIndex`: Índice da skill (opcional, apenas para S e H)

#### **Q - Mensagem de Quit (Sair)**
```
Formato: Q[<playerId>]
Exemplo: Q1 (Jogador 1 saiu)
```

#### **P - Mensagem de Ping (Keep-Alive)**
```
Formato: P
```
- Usado para verificar se a conexão está ativa
- Servidor responde com "O" (Pong)

#### **G - Solicitação de Placar (Scoreboard)**
```
Formato: G
```
- Solicita estatísticas globais do jogo

### 1.3. Mensagens do Servidor para o Cliente

#### **W - Welcome (Bem-vindo)**
```
Formato: W<playerId>
Exemplo: W1 (Você é o Jogador 1)
```

#### **S - Seleção de Classe**
```
Formato: S<targetPlayerId>
Exemplo: S1 (Jogador 1 deve escolher classe)
```

#### **T - Turno Sinalizado**
```
Formato: T<turnFlag>
Exemplo: T0 (Início do turno 0)
```

#### **R - Resultado do Turno (Snapshot)**
```
Formato: R<turnFlag>|H1:<hp1>/<mp1>|H2:<hp2>/<mp2>|C1:<cdS1>/<cdH1>|C2:<cdS2>/<cdH2>|L:<logLine>
Exemplo: R0|H1:100/50|H2:100/50|C1:0/0|C2:0/0|L:Jogador 1 atacou Jogador 2
```
- **Campos**:
  - `turnFlag`: Número do turno
  - `H1`, `H2`: HP e MP dos jogadores 1 e 2
  - `C1`, `C2`: Cooldowns de Skill e Cura dos jogadores 1 e 2
  - `L`: Log textual do que aconteceu no turno

#### **E - Fim de Jogo**
```
Formato: E<winnerId>|<reason>
Exemplo: E1|Jogador 1 venceu!
```

#### **C - Anúncio de Classe**
```
Formato: C<playerId><classe>
Exemplo: C1G (Jogador 1 escolheu Guerreiro)
```

#### **B - Placar Global (Scoreboard)**
```
Formato: B{"G":<vitoriasGuerreiro>,"M":<vitoriasMago>,"R":<vitoriasArqueiro>,"Ativa":<partidasAtivas>,"Ultimo":"<ultimoLog>"}
Exemplo: B{"G":5,"M":3,"R":2,"Ativa":1,"Ultimo":"Jogador 1 venceu"}
```

#### **O - Pong (Resposta ao Ping)**
```
Formato: O
```

#### **X - Erro**
```
Formato: X<errorCode>|<mensagem>
Exemplo: X01|Erro desconhecido
Exemplo: X99|Mensagem muito longa
```

### 1.4. Fluxo de Comunicação

```
CLIENTE                          SERVIDOR
  |                                |
  |-------- Conecta TCP ---------->|
  |                                |
  |<-------- W<id> ----------------|
  |                                |
  |<-------- S<id> ----------------|
  |                                |
  |-------- J<id><classe> -------->|
  |                                |
  |<-------- C<id><classe> --------|
  |                                |
  |<-------- T<turnFlag> ----------|
  |                                |
  |-------- M<turn><id><action> -->|
  |                                |
  |<-------- R<snapshot> ----------|
  |                                |
  |<-------- T<turnFlag> ----------|
  |                                |
  |-------- M<turn><id><action> -->|
  |                                |
  |<-------- R<snapshot> ----------|
  |                                |
  |         ... (repetir)          |
  |                                |
  |<-------- E<winner>|<reason> ---|
  |                                |
```

---

## 2. MEMÓRIA COMPARTILHADA (SHARED MEMORY)

### 2.1. Objetivo

Permitir que múltiplas instâncias do servidor compartilhem estatísticas globais do jogo, como:
- Número de partidas ativas
- Vitórias por classe
- Último log de jogo

### 2.2. Implementação (Windows C)

- **Nome do Mapeamento**: `DUELRPG_MM`
- **Nome do Mutex**: `DUELRPG_MM_MUTEX`
- **API**: `CreateFileMappingA`, `MapViewOfFile`
- **Sincronização**: Mutex do Windows (`CreateMutexA`, `WaitForSingleObject`, `ReleaseMutex`)

### 2.3. Estrutura de Dados Compartilhada

```c
typedef struct {
    int partidasAtivas;           // 0 = nenhuma, 1 = partida em andamento
    int vitoriasClasse[3];        // [0]=Guerreiro, [1]=Mago, [2]=Arqueiro
    char ultimoLog[MAX_LOG_SIZE]; // Última linha de log (até 256 chars)
} SharedState;
```

### 2.4. Operações na Memória Compartilhada

#### **Atualizar Log do Turno**
```c
sharedmem_update_turn(mem, "Jogador 1 atacou Jogador 2 causando 15 de dano");
```
- Define `partidasAtivas = 1`
- Atualiza `ultimoLog` com a descrição do turno

#### **Registrar Resultado da Partida**
```c
sharedmem_register_result(mem, CLASS_GUERREIRO);
```
- Define `partidasAtivas = 0`
- Incrementa contador de vitórias da classe vencedora

#### **Ler Estado (Scoreboard)**
- Lê todos os campos de forma atômica (com mutex)
- Retorna snapshot do estado atual

### 2.5. Sincronização

- **Mutex**: Garante acesso exclusivo durante leitura/escrita
- **Timeout**: 5 segundos para adquirir o mutex (`WaitForSingleObject` com timeout de 5000ms)
- **Thread-Safety**: Acesso protegido por mutex antes de qualquer operação de leitura/escrita

---

## 3. FILAS DE MENSAGENS (INTERNA DO SERVIDOR)

### 3.1. Objetivo

Cada cliente possui uma fila de mensagens (`MessageQueue`) para:
- Desacoplar recebimento de mensagens da rede do processamento do jogo
- Permitir processamento assíncrono
- Evitar bloqueios durante espera de ações

### 3.2. Estrutura

```c
typedef struct {
    char messages[QUEUE_MAX_MESSAGES][QUEUE_MESSAGE_SIZE];
    int head;
    int tail;
    int count;
    HANDLE mutex;
} MessageQueue;
```

- **Tamanho da Fila**: `QUEUE_MAX_MESSAGES = 64` mensagens
- **Tamanho de Mensagem**: `QUEUE_MESSAGE_SIZE = 256` caracteres
- **Mutex**: Cada fila tem seu próprio mutex para thread-safety

### 3.3. Operações

- **`queue_init()`**: Inicializa a fila e cria o mutex
- **`queue_push()`**: Adiciona mensagem na fila (bloqueia se cheia)
- **`queue_pop()`**: Remove e retorna mensagem da fila (com timeout opcional)
- **`queue_clear()`**: Limpa todas as mensagens da fila
- **`queue_destroy()`**: Libera recursos (fecha mutex)
- **Timeout**: 20 segundos para receber ação do jogador (usado em `queue_pop()`)

---

## 4. RESUMO DAS INFORMAÇÕES TROCADAS

### 4.1. Via Sockets (Cliente ↔ Servidor)

| Tipo | Direção | Informação |
|------|---------|------------|
| Join | C→S | ID do jogador, classe escolhida |
| Action | C→S | Turno, ID do jogador, ação, índice de skill |
| Quit | C→S | ID do jogador (opcional) |
| Ping | C→S | Keep-alive |
| Scoreboard Request | C→S | Solicitação de estatísticas |
| Welcome | S→C | ID atribuído ao jogador |
| Class Selection | S→C | Solicitação para escolher classe |
| Turn Signal | S→C | Número do turno atual |
| Turn Snapshot | S→C | HP/MP, cooldowns, log do turno |
| End Game | S→C | Vencedor, motivo |
| Class Announcement | S→C | Classe escolhida por jogador |
| Scoreboard | S→C | Estatísticas globais (JSON) |
| Pong | S→C | Resposta ao ping |
| Error | S→C | Código e mensagem de erro |

### 4.2. Via Memória Compartilhada (Servidor ↔ Servidor)

| Informação | Tipo | Descrição |
|------------|------|-----------|
| `partidasAtivas` | int | 0 = nenhuma partida, 1 = partida em andamento |
| `vitoriasClasse[0]` | int | Vitórias da classe Guerreiro |
| `vitoriasClasse[1]` | int | Vitórias da classe Mago |
| `vitoriasClasse[2]` | int | Vitórias da classe Arqueiro |
| `ultimoLog` | string | Última linha de log do jogo (até 256 chars) |

---

## 5. EXEMPLO PRÁTICO DE FLUXO COMPLETO

### Cenário: Dois jogadores iniciam uma partida

1. **Cliente 1 conecta**
   - Servidor envia: `W1`
   - Cliente 1 recebe seu ID

2. **Cliente 2 conecta**
   - Servidor envia: `W2`
   - Cliente 2 recebe seu ID

3. **Seleção de Classes**
   - Servidor envia: `S1` → Cliente 1
   - Servidor envia: `S2` → Cliente 2
   - Cliente 1 envia: `J1G` (escolhe Guerreiro)
   - Cliente 2 envia: `J2M` (escolhe Mago)
   - Servidor envia: `C1G` e `C2M` (anuncia classes)

4. **Início do Turno 0**
   - Servidor envia: `T0` → Ambos os clientes
   - Servidor atualiza memória compartilhada: `partidasAtivas = 1`

5. **Ações dos Jogadores**
   - Cliente 1 envia: `M011A` (Ataque)
   - Cliente 2 envia: `M012D` (Defesa)

6. **Resultado do Turno**
   - Servidor processa ações
   - Servidor envia: `R0|H1:100/50|H2:85/50|C1:0/0|C2:0/0|L:Jogador 1 atacou, Jogador 2 defendeu`
   - Servidor atualiza memória compartilhada: `ultimoLog = "Jogador 1 atacou..."`

7. **Fim da Partida**
   - Servidor envia: `E1|Jogador 1 venceu!`
   - Servidor atualiza memória compartilhada:
     - `partidasAtivas = 0`
     - `vitoriasClasse[CLASS_GUERREIRO]++`

8. **Consulta de Scoreboard (durante ou após partida)**
   - Cliente envia: `G`
   - Servidor lê memória compartilhada
   - Servidor envia: `B{"G":1,"M":0,"R":0,"Ativa":0,"Ultimo":"Jogador 1 venceu"}`

---

## 6. ARQUIVOS RELEVANTES (IMPLEMENTAÇÃO C)

- **`protocol.h` / `protocol.c`** - Definição e parsing de mensagens do protocolo
- **`net.h` / `net.c`** - Funções de rede (Winsock2) para comunicação TCP/IP
- **`sharedmem.h` / `sharedmem.c`** - Memória compartilhada usando Windows File Mapping
- **`queue.h` / `queue.c`** - Fila de mensagens para desacoplar recebimento de rede do processamento
- **`server.c`** - Lógica principal do servidor (gerencia clientes, partidas, turnos)
- **`client.c`** - Lógica do cliente (interface com usuário, comunicação com servidor)
- **`game.h` / `game.c`** - Regras do jogo (cálculo de dano, habilidades, etc.)

---

## 7. PROCESSAMENTO INTERNO DO SERVIDOR (C)

### 7.1. Arquitetura de Processamento

O servidor em C utiliza um **modelo de loop principal** com `select()` para processar múltiplos sockets sem bloqueio:

1. **Loop Principal**: O servidor roda em um loop que:
   - Usa `select()` para verificar se há dados disponíveis nos sockets dos clientes
   - Processa mensagens recebidas e as coloca nas filas dos clientes
   - Processa mensagens das filas quando necessário (espera de classe, espera de ação)

2. **Processamento de Mensagens**:
   ```c
   // Função process_client_message() em server.c
   - Recebe mensagem do socket usando net_recv_line()
   - Verifica tipo de mensagem (P, G, J, M, Q)
   - Ping (P) → Responde imediatamente com "O"
   - Scoreboard (G) → Lê memória compartilhada e envia JSON
   - Join/Action (J/M) → Coloca na fila do cliente
   - Quit (Q) → Marca cliente como desconectado
   ```

3. **Espera de Ações**:
   ```c
   // Função fetch_turn_command() em server.c
   - Loop com timeout de 20 segundos
   - A cada iteração:
     * Usa select() para verificar socket (timeout 100ms)
     * Processa mensagens recebidas
     * Verifica fila do cliente (queue_pop com timeout 0)
     * Se encontrar mensagem M válida → retorna comando
   - Se timeout → retorna defesa padrão
   ```

### 7.2. Fluxo de Processamento de Turno

```
1. Servidor envia T<turnFlag> para ambos clientes
2. Para cada cliente:
   a. Loop de espera (até 20s):
      - select() verifica socket (não bloqueia)
      - process_client_message() recebe e coloca na fila
      - queue_pop() verifica fila
      - Se mensagem M válida → processa
   b. Se timeout → usa defesa padrão
3. Processa ações com game_process_turn()
4. Envia R<snapshot> para ambos clientes
5. Atualiza memória compartilhada
6. Verifica fim de jogo
```

### 7.3. Sincronização

- **Não usa threads**: O servidor é single-threaded
- **select()**: Permite processar múltiplos sockets sem bloqueio
- **Filas com Mutex**: Cada fila tem mutex para thread-safety (caso futuras threads sejam adicionadas)
- **Memória Compartilhada**: Protegida por mutex global (`DUELRPG_MM_MUTEX`)

---

## 8. CARACTERÍSTICAS IMPORTANTES

### 8.1. Thread-Safety
- **Sockets**: O servidor processa mensagens de forma sequencial usando `select()` para não bloquear
- **Memória Compartilhada**: Protegida por mutex do Windows (`DUELRPG_MM_MUTEX`)
- **Filas**: Cada cliente tem sua própria fila (`MessageQueue`) acessada pelo servidor principal

### 8.2. Timeouts
- **Seleção de Classe**: 60 segundos
- **Ação no Turno**: 20 segundos
- **Mutex**: 5 segundos para adquirir lock

### 8.3. Tamanhos Máximos
- **Mensagem**: `MAX_MESSAGE_SIZE = 256` caracteres (definido em `protocol.h`)
- **Log**: `MAX_LOG_SIZE = 256` caracteres (definido em `protocol.h`)
- **Fila**: `QUEUE_MAX_MESSAGES = 64` mensagens, cada uma com `QUEUE_MESSAGE_SIZE = 256` caracteres (definido em `queue.h`)

---

## CONCLUSÃO

O sistema utiliza uma arquitetura **cliente-servidor** com comunicação **bidirecional via TCP/IP** para o jogo em tempo real, e **memória compartilhada** para estatísticas globais persistentes entre múltiplas instâncias do servidor. A comunicação é **textual** (strings formatadas) e **síncrona** (request-response), com suporte a **keep-alive** (ping/pong) e **tratamento de erros**.

