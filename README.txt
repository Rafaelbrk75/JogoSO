Duel RPG Online (C)
===================

Resumo
------
Implementação em **C** de um RPG em turnos para dois jogadores.  
O servidor autoritativo controla o fluxo da partida, resolve as ações simultâneas, registra logs e mantém um placar global em memória compartilhada (`DUELRPG_MM.dat`).  
Os clientes são aplicações de terminal que trocam mensagens com o servidor por meio de sockets TCP (Winsock).

Requisitos
----------
- Windows 10 ou superior.
- Compilador C com suporte a:
  - **winsock2.h** (disponível no Windows SDK / Visual Studio).
  - **WinAPI** (CreateFileMapping/MapViewOfFile/Mutexes) – já presente no Windows SDK.

**Nota**: Este jogo **não usa pthreads**. Utiliza apenas Winsock2 e Windows API.

Estrutura dos arquivos
----------------------
```
server.c        # Servidor autoritativo (Winsock + memória compartilhada)
client.c        # Cliente de terminal (Winsock)
game.c/.h       # Regras de combate, cooldowns, logs
protocol.c/.h   # Notação compacta das mensagens (J, M, C, T, R, E, B, etc.)
net.c/.h        # Funções auxiliares de socket (linha a linha)
queue.c/.h      # Fila thread-safe (mutex do Windows)
sharedmem.c/.h  # Gerenciamento do arquivo mapeado e mutex nomeado
build.bat       # Script de compilação (cl ou gcc - MinGW)
server.log      # Log textual (gerado em runtime)
DUELRPG_MM.dat  # Arquivo mapeado com placar global
```

Compilação
----------
1. Abra um **Developer Command Prompt for VS** (MSVC) ou um shell onde `cl.exe` esteja disponível.
2. Execute:
   ```
   build.bat
   ```
   Isso gera `server.exe` e `client.exe`.

   Se preferir **MinGW (gcc)**:
   ```
   build.bat
   ```
   (O script detecta automaticamente e usa GCC se disponível, linkando apenas `-lws2_32`.)

Execução
--------
1. **Servidor**
   ```
   server.exe
   ```
   - Porta fixa `5050`.
   - Gera `server.log` com RX/TX.
   - Atualiza `DUELRPG_MM.dat` a cada turno/fim de jogo.

2. **Dois clientes** (cada um em um terminal)
   ```
   client.exe              (localhost)
   client.exe 192.168.x.y  (outra máquina)
   ```

   Comandos no cliente:
   - Ao receber `S<id>` → digite `G`, `M` ou `R` para escolher classe.
   - Durante o turno (`T1`/`T2`) → `A`, `S`, `D` ou `H`.
   - `F5` ou `G` → solicita placar global.
   - `help` → lista de comandos.
   - `Q` → encerra a sessão.

Protocolo de mensagens
----------------------
- Cliente → Servidor:
  - `J<id><classe>` – seleção de classe (`G`, `M`, `R`)
  - `M<vez><id><acao>[0]` – ação de turno (`A`, `S0`, `D`, `H0`)
  - `G` – consulta placar global
  - `Q<id>` – desconectar
- Servidor → Cliente:
  - `W<id>` – boas-vindas
  - `S<id>` – solicitar classe do jogador
  - `C<id><classe>` – anunciar classe escolhida
  - `T<vez>` – início de turno (1 ou 2)
  - `R...` – snapshot completo (`H1`, `H2`, `C1`, `C2`, `L`)
  - `E<winner>|reason:<motivo>` – fim de jogo (0 = empate)
  - `B<json>` – placar (vitórias por classe, partida ativa, último log)
  - `X<code>|<msg>` – erro (ex.: formato inválido)
  - `O` – resposta a ping `P`

Regras principais
-----------------
- HP base 100, MP base 50.
- Cooldown e custo de MP conforme a classe.
- Sem MP ou cooldown → fallback automático para ataque básico.
- Timeout de 20s → defesa automática.
- Desconexão → vitória do oponente (ou empate se ambos caem).
- Placar global armazenado em memória compartilhada (arquivo mapeado + mutex).

Matriz de habilidades
---------------------
```
Classe    | Ataque | Skill (dano / MP / CD)   | Cura (valor / MP / CD) | Defesa
--------- | ------ | ------------------------ | ---------------------- | -------
Guerreiro | 12     | 25 / 10 / 2 (Golpe)      | -                      | 50 %
Mago      | 8      | 30 / 15 / 2 (Bola Fogo)  | 18 / 12 / 2 (Cura)     | 40 %
Arqueiro  | 10     | 2 x 8 / 10 / 2 (Flecha)  | -                      | 30 %
```

Teste rápido
------------
1. Inicie o servidor (`server.exe`).
2. Em dois terminais:
   - Cliente 1 → escolha `G` (Guerreiro).
   - Cliente 2 → escolha `M` (Mago).
3. Durante os turnos, envie combinações de `A`, `S`, `D` ou `H`.  
   Observe o snapshot (`R...`), logs e atualização do placar (`B{...}`).

Memória compartilhada
---------------------
- Arquivo mapeado: `DUELRPG_MM`.
- Mutex nomeado: `DUELRPG_MM_MUTEX`.
- Estrutura `SharedState`: flag de partida ativa, vitórias por classe e último log.
- Clientes podem solicitar o placar a qualquer momento (`F5` / comando `G`).

Logs
----
- `server.log` → histórico textual com timestamps, RX/TX, timeouts e desconexões.
- `DUELRPG_MM.dat` → pode ser lido por ferramentas externas (ex.: outro processo em C/PowerShell) para monitorar o placar enquanto o servidor roda.

Observação
----------
**O requisito da disciplina é atendido exclusivamente pela solução em C descrita acima** (Winsock + memória compartilhada com Windows API).

Contato
-------
Projeto acadêmico para a disciplina de Sistemas Operacionais — demonstra sockets, sincronização com mutex do Windows e uso de memória compartilhada no Windows.

