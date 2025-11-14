# Mudanças Realizadas - Remoção de pthreads

## Resumo

O jogo foi completamente refatorado para **remover todas as dependências de pthreads**, mantendo apenas:
- **Winsock2** para comunicação via sockets
- **Memória compartilhada do Windows** (CreateFileMapping, Mutex)

## Arquivos Modificados

### 1. `queue.h` e `queue.c`
- **Antes**: Usava `pthread_mutex_t` e `pthread_cond_t`
- **Agora**: Usa `HANDLE` (mutex do Windows) via `CreateMutex()`
- **Mudança**: `queue_pop()` agora usa polling com `Sleep(10)` em vez de `pthread_cond_timedwait()`

### 2. `server.c`
- **Antes**: Usava threads (`pthread_t`) para:
  - Cada cliente tinha uma thread de leitura
  - Thread separada para gerenciar a partida
  - Mutex do pthread para logs
- **Agora**: 
  - Loop principal único usando `select()` para gerenciar múltiplos sockets
  - Sockets configurados como não-bloqueantes (`FIONBIO`)
  - Mutex do Windows (`CreateMutex()`) para logs
  - Função `run_match()` executa no loop principal quando ambos clientes estão conectados

### 3. `client.c`
- **Antes**: Thread separada para ler do socket
- **Agora**: 
  - Loop principal único usando `select()` para ler do socket
  - `_kbhit()` para verificar entrada do teclado sem bloquear
  - Socket configurado como não-bloqueante

### 4. `build.bat`
- **Antes**: Linkava com `pthreadVC2.lib` ou `-lpthread`
- **Agora**: Apenas `ws2_32.lib` ou `-lws2_32`
- **Nota**: Não precisa mais de pthreads instalado!

## Como Funciona Agora

### Servidor
1. Aceita 2 clientes
2. Loop principal com `select()` verifica atividade nos sockets
3. Quando ambos conectados, executa `run_match()` no loop principal
4. Durante a partida, continua processando mensagens dos clientes via `select()`

### Cliente
1. Conecta ao servidor
2. Loop principal:
   - `select()` verifica se há dados no socket (não-bloqueante)
   - `_kbhit()` verifica se há entrada do teclado
   - Processa mensagens e comandos conforme chegam

### Fila de Mensagens
- Usa mutex do Windows para thread-safety
- `queue_pop()` faz polling com timeout em vez de usar condition variables
- Funciona corretamente mesmo sem threads (proteção contra acesso concorrente)

## Vantagens

1. ✅ **Sem dependências externas**: Não precisa instalar pthreads
2. ✅ **Mais simples**: Código mais direto, sem gerenciamento de threads
3. ✅ **Compatível**: Funciona apenas com Winsock2 (já incluído no Windows)
4. ✅ **Mesma funcionalidade**: Jogo funciona exatamente igual

## Requisitos Atendidos

✅ Comunicação via sockets (Winsock2)  
✅ Memória compartilhada (CreateFileMapping)  
✅ Jogo para 2 jogadores  
✅ Troca de mensagens entre processos  
✅ Controle de turnos via mensagens  
✅ Notação própria de mensagens (protocolo J, M, C, T, R, E, B, etc.)

## Compilação

```batch
build.bat
```

Agora compila sem precisar de pthreads! Funciona com MSVC ou GCC.

