# GERENCIAMENTO DE EXCLUSÃO MÚTUA - DUEL RPG

## RESUMO

O sistema utiliza **Mutex do Windows** para garantir exclusão mútua em três contextos diferentes:

1. **Memória Compartilhada** - Mutex nomeado compartilhado entre processos
2. **Filas de Mensagens** - Mutex local por fila (um por cliente)
3. **Arquivo de Log** - Mutex local para sincronizar escrita de logs

---

## 1. MUTEX PARA MEMÓRIA COMPARTILHADA

### 1.1. Características

- **Tipo**: Mutex nomeado do Windows
- **Nome**: `DUELRPG_MM_MUTEX`
- **Escopo**: Compartilhado entre múltiplos processos
- **Objetivo**: Proteger acesso à memória compartilhada (`SharedState`)

### 1.2. Criação e Inicialização

```c
// sharedmem.c - sharedmem_init()
mem->mutex = CreateMutexA(NULL, FALSE, SHARED_MUTEX_NAME);
```

- **Parâmetros**:
  - `NULL`: Segurança padrão (herda do processo)
  - `FALSE`: Não assume posse inicial (não bloqueia o criador)
  - `SHARED_MUTEX_NAME`: Nome do mutex (`"DUELRPG_MM_MUTEX"`)

- **Comportamento**:
  - Se o mutex não existe, é criado
  - Se já existe, abre o mutex existente
  - Múltiplos processos podem acessar o mesmo mutex pelo nome

### 1.3. Aquisição do Lock (Lock)

```c
// sharedmem.c - sharedmem_lock()
int sharedmem_lock(SharedMemory *mem)
{
    if (!mem || !mem->mutex)
        return 0;
    DWORD res = WaitForSingleObject(mem->mutex, 5000);
    return res == WAIT_OBJECT_0;
}
```

- **Função**: `WaitForSingleObject()`
- **Timeout**: 5000ms (5 segundos)
- **Retorno**: 
  - `1` se conseguiu adquirir o lock (`WAIT_OBJECT_0`)
  - `0` se timeout ou erro
- **Comportamento**: Bloqueia até adquirir o lock ou timeout

### 1.4. Liberação do Lock (Unlock)

```c
// sharedmem.c - sharedmem_unlock()
void sharedmem_unlock(SharedMemory *mem)
{
    if (!mem || !mem->mutex)
        return;
    ReleaseMutex(mem->mutex);
}
```

- **Função**: `ReleaseMutex()`
- **Comportamento**: Libera o lock, permitindo que outros processos/procurem adquiri-lo

### 1.5. Uso na Prática

#### **Atualizar Log do Turno**
```c
void sharedmem_update_turn(SharedMemory *mem, const char *logLine)
{
    if (!mem || !mem->state)
        return;
    if (!sharedmem_lock(mem))  // Adquire lock
        return;
    mem->state->partidasAtivas = 1;
    if (logLine) {
        strncpy(mem->state->ultimoLog, logLine, sizeof(mem->state->ultimoLog) - 1);
        mem->state->ultimoLog[sizeof(mem->state->ultimoLog) - 1] = '\0';
    }
    sharedmem_unlock(mem);  // Libera lock
}
```

#### **Registrar Resultado da Partida**
```c
void sharedmem_register_result(SharedMemory *mem, PlayerClass winnerClasse)
{
    if (!mem || !mem->state)
        return;
    if (!sharedmem_lock(mem))  // Adquire lock
        return;
    mem->state->partidasAtivas = 0;
    if (winnerClasse >= CLASS_GUERREIRO && winnerClasse <= CLASS_ARQUEIRO) {
        mem->state->vitoriasClasse[winnerClasse]++;
    }
    sharedmem_unlock(mem);  // Libera lock
}
```

#### **Ler Estado (Scoreboard)**
```c
// server.c - send_scoreboard()
if (!sharedmem_lock(&server->shared))  // Adquire lock
    return;
SharedState snapshot = *server->shared.state;  // Cópia dos dados
sharedmem_unlock(&server->shared);  // Libera lock
// Usa snapshot (cópia local) sem lock
```

### 1.6. Características Importantes

- **Timeout de 5 segundos**: Se não conseguir adquirir o lock em 5 segundos, retorna erro
- **Cópia de Dados**: Ao ler, faz uma cópia (`snapshot`) para minimizar tempo com lock
- **Proteção de Escrita**: Todas as operações de escrita são protegidas por lock
- **Entre Processos**: Funciona entre diferentes instâncias do servidor

---

## 2. MUTEX PARA FILAS DE MENSAGENS

### 2.1. Características

- **Tipo**: Mutex local do Windows (não nomeado)
- **Escopo**: Local ao processo (um mutex por fila)
- **Objetivo**: Proteger acesso às filas de mensagens de cada cliente

### 2.2. Criação e Inicialização

```c
// queue.c - queue_init()
void queue_init(MessageQueue *queue)
{
    memset(queue, 0, sizeof(MessageQueue));
    queue->mutex = CreateMutexA(NULL, FALSE, NULL);
}
```

- **Parâmetros**:
  - `NULL`: Segurança padrão
  - `FALSE`: Não assume posse inicial
  - `NULL`: Mutex não nomeado (local ao processo)

### 2.3. Operações Protegidas

#### **Adicionar Mensagem (Push)**
```c
int queue_push(MessageQueue *queue, const char *message)
{
    if (!queue || !message)
        return 0;

    WaitForSingleObject(queue->mutex, INFINITE);  // Lock (bloqueia indefinidamente)
    if (queue->count == QUEUE_MAX_MESSAGES) {
        ReleaseMutex(queue->mutex);
        return 0;  // Fila cheia
    }

    strncpy(queue->messages[queue->tail], message, QUEUE_MESSAGE_SIZE - 1);
    queue->messages[queue->tail][QUEUE_MESSAGE_SIZE - 1] = '\0';
    queue->tail = (queue->tail + 1) % QUEUE_MAX_MESSAGES;
    queue->count++;
    ReleaseMutex(queue->mutex);  // Unlock
    return 1;
}
```

- **Lock**: `WaitForSingleObject()` com `INFINITE` (bloqueia até adquirir)
- **Proteção**: Toda a região crítica (verificação, escrita, atualização de índices)
- **Unlock**: Sempre libera o lock, mesmo em caso de erro

#### **Remover Mensagem (Pop)**
```c
int queue_pop(MessageQueue *queue, char *out, int outSize, int timeoutSeconds)
{
    if (!queue || !out || outSize <= 0)
        return 0;

    DWORD timeout = timeoutSeconds > 0 ? timeoutSeconds * 1000 : 0;
    DWORD startTime = GetTickCount();

    while (1) {
        WaitForSingleObject(queue->mutex, INFINITE);  // Lock
        
        if (queue->count > 0) {
            strncpy(out, queue->messages[queue->head], outSize - 1);
            out[outSize - 1] = '\0';
            queue->head = (queue->head + 1) % QUEUE_MAX_MESSAGES;
            queue->count--;
            ReleaseMutex(queue->mutex);  // Unlock
            return 1;  // Mensagem encontrada
        }
        
        ReleaseMutex(queue->mutex);  // Unlock (fila vazia)

        if (timeoutSeconds <= 0)
            return 0;  // Sem timeout, retorna imediatamente

        DWORD elapsed = GetTickCount() - startTime;
        if (elapsed >= timeout)
            return 0;  // Timeout

        Sleep(10);  // Espera 10ms antes de tentar novamente
    }
}
```

- **Padrão**: Lock → Verificação → Unlock → Sleep → Repetir
- **Evita Bloqueio Permanente**: Não mantém o lock durante o sleep
- **Timeout**: Controlado externamente (não pelo mutex)

#### **Limpar Fila**
```c
void queue_clear(MessageQueue *queue)
{
    if (!queue)
        return;
    WaitForSingleObject(queue->mutex, INFINITE);  // Lock
    queue->head = queue->tail = queue->count = 0;
    ReleaseMutex(queue->mutex);  // Unlock
}
```

### 2.4. Características Importantes

- **Mutex Local**: Não é compartilhado entre processos (não nomeado)
- **Lock Infinito**: `WaitForSingleObject()` com `INFINITE` (não há timeout)
- **Padrão Lock-Unlock**: Sempre libera o lock após uso
- **Proteção de Índices**: Protege `head`, `tail` e `count` simultaneamente

---

## 3. MUTEX PARA ARQUIVO DE LOG

### 3.1. Características

- **Tipo**: Mutex local do Windows (não nomeado)
- **Escopo**: Local ao processo do servidor
- **Objetivo**: Proteger escrita no arquivo de log (`server.log`)

### 3.2. Criação e Inicialização

```c
// server.c - main()
server.logMutex = CreateMutexA(NULL, FALSE, NULL);
server.logFile = fopen("server.log", "a");
```

- **Criação**: No início do servidor
- **Não nomeado**: Mutex local (apenas para o processo atual)
- **Propósito**: Garantir que apenas uma thread/procure escreva no log por vez

### 3.3. Uso na Prática

```c
static void append_log(ServerContext *server, const char *fmt, ...)
{
    if (!server || !server->logFile)
        return;

    WaitForSingleObject(server->logMutex, INFINITE);  // Lock
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
    fflush(server->logFile);  // Força escrita imediata

    va_end(args);
    ReleaseMutex(server->logMutex);  // Unlock
}
```

- **Lock Infinito**: `WaitForSingleObject()` com `INFINITE`
- **Região Crítica**: Toda a escrita no arquivo (timestamp, mensagem, flush)
- **Flush Imediato**: `fflush()` garante que os dados sejam escritos no disco

### 3.4. Limpeza

```c
// server.c - main() (finalização)
if (server.logFile)
    fclose(server.logFile);

if (server.logMutex)
    CloseHandle(server.logMutex);
```

---

## 4. COMPARAÇÃO DOS MUTEX

| Característica | Memória Compartilhada | Filas | Log |
|----------------|----------------------|-------|-----|
| **Tipo** | Nomeado | Local | Local |
| **Nome** | `DUELRPG_MM_MUTEX` | `NULL` | `NULL` |
| **Escopo** | Entre processos | Processo | Processo |
| **Timeout** | 5 segundos | Infinito | Infinito |
| **API de Lock** | `WaitForSingleObject()` | `WaitForSingleObject()` | `WaitForSingleObject()` |
| **API de Unlock** | `ReleaseMutex()` | `ReleaseMutex()` | `ReleaseMutex()` |
| **Criação** | `CreateMutexA(name)` | `CreateMutexA(NULL)` | `CreateMutexA(NULL)` |

---

## 5. PADRÕES DE USO

### 5.1. Padrão Básico (Lock-Unlock)

```c
WaitForSingleObject(mutex, INFINITE);  // Adquire lock
// ... região crítica ...
ReleaseMutex(mutex);  // Libera lock
```

### 5.2. Padrão com Timeout

```c
DWORD res = WaitForSingleObject(mutex, timeout_ms);
if (res == WAIT_OBJECT_0) {
    // ... região crítica ...
    ReleaseMutex(mutex);
} else {
    // Timeout ou erro
}
```

### 5.3. Padrão com Verificação de Erro

```c
if (!sharedmem_lock(mem))  // Retorna 0 em caso de timeout
    return;  // Erro ao adquirir lock
// ... região crítica ...
sharedmem_unlock(mem);
```

### 5.4. Padrão com Cópia de Dados (Minimizar Tempo de Lock)

```c
WaitForSingleObject(mutex, INFINITE);
// Cópia rápida dos dados
SharedState snapshot = *sharedState;
ReleaseMutex(mutex);
// Processa snapshot sem lock (pode levar tempo)
```

---

## 6. GARANTIAS DE SEGURANÇA

### 6.1. Exclusão Mútua

- ✅ **Memória Compartilhada**: Protegida por mutex nomeado (entre processos)
- ✅ **Filas**: Cada fila tem seu próprio mutex (dentro do processo)
- ✅ **Log**: Protegido por mutex local

### 6.2. Prevenção de Deadlock

- ✅ **Lock Infinito**: Apenas em operações rápidas (filas, log)
- ✅ **Timeout**: Apenas em memória compartilhada (pode haver contenção entre processos)
- ✅ **Padrão Consistente**: Sempre Lock → Operação → Unlock
- ✅ **Sem Lock Aninhado**: Não há locks aninhados no código

### 6.3. Prevenção de Race Conditions

- ✅ **Região Crítica Protegida**: Todas as operações críticas estão dentro do lock
- ✅ **Cópia de Dados**: Ao ler memória compartilhada, faz cópia antes de liberar lock
- ✅ **Atualização Atômica**: Operações de escrita são atômicas (dentro do lock)

---

## 7. FUNÇÕES DA API DO WINDOWS

### 7.1. CreateMutexA()

```c
HANDLE CreateMutexA(
    LPSECURITY_ATTRIBUTES lpMutexAttributes,  // NULL = padrão
    BOOL bInitialOwner,                        // FALSE = não assume posse
    LPCSTR lpName                              // Nome (NULL = não nomeado)
);
```

- **Retorno**: Handle do mutex ou `NULL` em caso de erro
- **Nomeado**: Se `lpName` não é `NULL`, mutex é compartilhado entre processos
- **Não nomeado**: Se `lpName` é `NULL`, mutex é local ao processo

### 7.2. WaitForSingleObject()

```c
DWORD WaitForSingleObject(
    HANDLE hHandle,     // Handle do mutex
    DWORD dwMilliseconds // Timeout em milissegundos (INFINITE = bloqueia indefinidamente)
);
```

- **Retorno**:
  - `WAIT_OBJECT_0`: Lock adquirido com sucesso
  - `WAIT_TIMEOUT`: Timeout (se `dwMilliseconds != INFINITE`)
  - `WAIT_FAILED`: Erro
- **Comportamento**: Bloqueia até adquirir o lock ou timeout

### 7.3. ReleaseMutex()

```c
BOOL ReleaseMutex(HANDLE hMutex);
```

- **Retorno**: `TRUE` se sucesso, `FALSE` se erro
- **Comportamento**: Libera o lock, permitindo que outros processos/procurem adquiri-lo
- **Importante**: Apenas o processo que adquiriu o lock pode liberá-lo

### 7.4. CloseHandle()

```c
BOOL CloseHandle(HANDLE hObject);
```

- **Uso**: Fechar handle do mutex quando não for mais necessário
- **Importante**: Não fecha o mutex em si, apenas o handle do processo

---

## 8. EXEMPLOS PRÁTICOS

### 8.1. Exemplo: Atualizar Memória Compartilhada

```c
// Servidor 1 (Processo A)
sharedmem_update_turn(&mem, "Jogador 1 atacou");
// 1. Adquire lock (DUELRPG_MM_MUTEX)
// 2. Atualiza partidasAtivas = 1
// 3. Copia "Jogador 1 atacou" para ultimoLog
// 4. Libera lock

// Servidor 2 (Processo B) - simultaneamente
send_scoreboard(server, client);
// 1. Tenta adquirir lock (DUELRPG_MM_MUTEX)
// 2. Se Servidor 1 ainda tem o lock → espera (até 5s)
// 3. Adquire lock quando Servidor 1 libera
// 4. Copia SharedState para snapshot
// 5. Libera lock
// 6. Usa snapshot (sem lock)
```

### 8.2. Exemplo: Adicionar Mensagem na Fila

```c
// Thread/Processo principal
process_client_message(server, client);
// 1. Recebe mensagem "M011A" do socket
// 2. Adquire lock da fila do cliente
// 3. Verifica se fila não está cheia
// 4. Copia mensagem para fila[tail]
// 5. Atualiza tail e count
// 6. Libera lock

// Loop de processamento (mesmo processo)
fetch_turn_command(server, client, turnFlag);
// 1. Tenta adquirir lock da fila
// 2. Se fila vazia → libera lock → Sleep(10ms) → repete
// 3. Se fila não vazia → remove mensagem → libera lock → retorna
```

### 8.3. Exemplo: Escrever no Log

```c
// Múltiplas chamadas simultâneas
append_log(server, "RX[1]: J1G");
append_log(server, "RX[2]: J2M");
// 1. Primeira chamada adquire lock
// 2. Escreve "[2024-01-01 12:00:00] RX[1]: J1G\n"
// 3. Flush para disco
// 4. Libera lock
// 5. Segunda chamada adquire lock
// 6. Escreve "[2024-01-01 12:00:01] RX[2]: J2M\n"
// 7. Flush para disco
// 8. Libera lock
```

---

## 9. DIAGRAMAS DE FLUXO

### 9.1. Fluxo de Exclusão Mútua na Memória Compartilhada

```
PROCESSO A                    MUTEX                    PROCESSO B
    |                          |                          |
    |--- WaitForSingleObject() --->|                      |
    |                          | (LOCKED)                 |
    |                          |                          |
    |<--- WAIT_OBJECT_0 -------|                          |
    | (LOCK ACQUIRED)          |                          |
    |                          |                          |
    |--- Ler/Escrever ----->|  |                          |
    |   SharedState           |  |                          |
    |                          |                          |
    |                          |--- WaitForSingleObject() --->|
    |                          | (LOCKED)    | (BLOQUEADO) |
    |                          |             | (esperando) |
    |--- ReleaseMutex() ----->|              |             |
    |                          | (UNLOCKED)  |             |
    |                          |<--- WAIT_OBJECT_0 ----------|
    |                          |             | (LOCK ACQUIRED) |
    |                          |             |             |
    |                          |<--- Ler/Escrever ----------|
    |                          |   SharedState             |
    |                          |             |             |
    |                          |<--- ReleaseMutex() -------|
    |                          | (UNLOCKED)  |             |
```

### 9.2. Fluxo de Exclusão Mútua nas Filas

```
THREAD A                      MUTEX                    THREAD B
    |                          |                          |
    |--- WaitForSingleObject() --->|                      |
    |                          | (LOCKED)                 |
    |                          |                          |
    |<--- WAIT_OBJECT_0 -------|                          |
    | (LOCK ACQUIRED)          |                          |
    |                          |                          |
    |--- queue_push() ----->|  |                          |
    |   mensagem[tail]         |  |                          |
    |   tail++                 |  |                          |
    |   count++                |  |                          |
    |                          |                          |
    |--- ReleaseMutex() ----->|                          |
    |                          | (UNLOCKED)                |
    |                          |                          |
    |                          |--- WaitForSingleObject() --->|
    |                          | (LOCKED)                  |
    |                          |                          |
    |                          |<--- WAIT_OBJECT_0 -------|
    |                          | (LOCK ACQUIRED)          |
    |                          |                          |
    |                          |--- queue_pop() ----->|   |
    |                          |   mensagem[head]         |
    |                          |   head++                 |
    |                          |   count--                |
    |                          |                          |
    |                          |<--- ReleaseMutex() -------|
    |                          | (UNLOCKED)                |
```

### 9.3. Fluxo com Timeout (Memória Compartilhada)

```
PROCESSO A                    MUTEX                    PROCESSO B
    |                          |                          |
    |--- WaitForSingleObject() --->|                      |
    |   (timeout: 5000ms)      | (LOCKED)                 |
    |                          |                          |
    |<--- WAIT_OBJECT_0 -------|                          |
    | (LOCK ACQUIRED)          |                          |
    |                          |                          |
    |--- Operação Lenta --->|  |                          |
    |   (6 segundos)            |                          |
    |                          |                          |
    |                          |--- WaitForSingleObject() --->|
    |                          |   (timeout: 5000ms)      |
    |                          |   (BLOQUEADO)            |
    |                          |   ... (espera 5s) ...    |
    |                          |<--- WAIT_TIMEOUT ----------|
    |                          |   (TIMEOUT!)              |
    |                          |   (retorna erro)          |
    |                          |                          |
    |--- ReleaseMutex() ----->|                          |
    |                          | (UNLOCKED)                |
```

### 9.4. Padrão Lock-Unlock-Sleep (Filas)

```
THREAD                        MUTEX                    FILA
    |                          |                          |
    |--- WaitForSingleObject() --->|                      |
    |                          | (LOCKED)                 |
    |                          |                          |
    |<--- WAIT_OBJECT_0 -------|                          |
    | (LOCK ACQUIRED)          |                          |
    |                          |                          |
    |--- Verifica count ----->|                          |
    |   if (count == 0)        |  | (VAZIA)                |
    |                          |                          |
    |--- ReleaseMutex() ----->|                          |
    |                          | (UNLOCKED)                |
    |                          |                          |
    |--- Sleep(10ms) -------->|                          |
    |   (sem lock!)            |                          |
    |                          |                          |
    |--- WaitForSingleObject() --->|                      |
    |                          | (LOCKED)                 |
    |                          |                          |
    |<--- WAIT_OBJECT_0 -------|                          |
    | (LOCK ACQUIRED)          |                          |
    |                          |                          |
    |--- Verifica count ----->|                          |
    |   if (count > 0)         |  | (TEM MENSAGEM)         |
    |   queue_pop()            |  |                        |
    |                          |                          |
    |--- ReleaseMutex() ----->|                          |
    |                          | (UNLOCKED)                |
```

**Importante**: O padrão **Lock → Verificar → Unlock → Sleep → Repetir** evita manter o lock durante o sleep, permitindo que outras threads/procurem acessem a fila durante a espera.

---

## 10. ARQUIVOS RELEVANTES

- **`sharedmem.h` / `sharedmem.c`**: Implementação do mutex para memória compartilhada
- **`queue.h` / `queue.c`**: Implementação do mutex para filas de mensagens
- **`server.c`**: Uso do mutex para log e chamadas às funções de memória compartilhada

---

## 11. CONCLUSÃO

O sistema utiliza **três tipos de mutex** para garantir exclusão mútua:

1. **Mutex Nomeado** (`DUELRPG_MM_MUTEX`): Para memória compartilhada entre processos
2. **Mutex Local**: Para filas de mensagens (um por cliente)
3. **Mutex Local**: Para arquivo de log

Todos os mutex são implementados usando a **API do Windows** (`CreateMutexA`, `WaitForSingleObject`, `ReleaseMutex`), garantindo:

- ✅ **Exclusão Mútua**: Apenas um processo/thread acessa a região crítica por vez
- ✅ **Prevenção de Race Conditions**: Operações críticas são atômicas
- ✅ **Sincronização Entre Processos**: Memória compartilhada protegida entre múltiplas instâncias do servidor
- ✅ **Thread-Safety**: Filas e logs protegidos dentro do processo

O padrão de uso é consistente: **Lock → Operação → Unlock**, sempre garantindo que o lock seja liberado, mesmo em caso de erro.

