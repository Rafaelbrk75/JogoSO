# Guia de Testes - Duel RPG Online

## Pré-requisitos

1. **Compilador C**: Visual Studio (MSVC) ou MinGW com GCC
2. **Bibliotecas**:
   - Winsock2 (já incluído no Windows SDK)
   - Windows API (CreateFileMapping, Mutex) - já incluído no Windows SDK
3. **Sistema**: Windows 10 ou superior

**Nota**: Este jogo **não usa pthreads**. Utiliza apenas Winsock2 e Windows API para comunicação e sincronização.

## Passo 1: Compilar o Projeto

### Opção A: Usando MSVC (Visual Studio)
```batch
build.bat
```

### Opção B: Usando GCC (MinGW)
```batch
build.bat gcc
```

Isso gerará:
- `server.exe` - Servidor do jogo
- `client.exe` - Cliente do jogo

## Passo 2: Testar o Servidor

### 2.1 Iniciar o Servidor
Abra um terminal e execute:
```batch
server.exe
```

**O que verificar:**
- ✅ Servidor inicia sem erros
- ✅ Mensagem: "Servidor iniciado na porta 5050"
- ✅ Arquivo `server.log` é criado
- ✅ Arquivo `DUELRPG_MM.dat` é criado (memória compartilhada)

### 2.2 Verificar Logs
O servidor deve criar/atualizar `server.log` com:
- Timestamps
- Mensagens RX/TX
- Eventos de conexão/desconexão

## Passo 3: Testar Conexão de Clientes

### 3.1 Teste Básico de Conexão
1. **Terminal 1**: Execute `server.exe`
2. **Terminal 2**: Execute `client.exe` (ou `client.exe 127.0.0.1`)
3. **Terminal 3**: Execute `client.exe` (ou `client.exe 127.0.0.1`)

**O que verificar:**
- ✅ Cliente 1 recebe: "Conectado como jogador 1. Aguarde oponente..."
- ✅ Cliente 2 recebe: "Conectado como jogador 2. Aguarde oponente..."
- ✅ Servidor aceita ambas as conexões

### 3.2 Teste de Seleção de Classe
Após conectar dois clientes:

**Cliente 1:**
- Recebe: "Selecione sua classe (G=Mago, R=Arqueiro, G=Guerreiro):"
- Digite: `G` (Guerreiro), `M` (Mago) ou `R` (Arqueiro)

**Cliente 2:**
- Recebe: "Selecione sua classe..."
- Digite uma classe diferente

**O que verificar:**
- ✅ Mensagem de confirmação: "Classe [Nome] enviada. Aguarde início."
- ✅ Ambos os clientes veem: "Jogador X escolheu [Classe]"
- ✅ Partida inicia automaticamente após ambas as seleções

## Passo 4: Testar Mecânicas de Jogo

### 4.1 Teste de Turnos
Durante o jogo:

**O que verificar:**
- ✅ Mensagem de turno: "Seu turno sinalizado (1 ou 2)"
- ✅ Comandos disponíveis:
  - `A` - Ataque básico
  - `S` - Skill da classe
  - `D` - Defesa
  - `H` - Cura (apenas Mago)
- ✅ Após cada turno, ambos veem o snapshot:
  ```
  === Duel RPG Online ===
  Turno sinalizado: X
  Jogador 1: HP XXX | MP XXX | CD Skill X | CD Cura X
  Jogador 2: HP XXX | MP XXX | CD Skill X | CD Cura X
  Resumo: [descrição do turno]
  ```

### 4.2 Teste de Ações
Teste cada ação:

**Ataque (A):**
- ✅ Causa dano básico (Guerreiro: 12, Mago: 8, Arqueiro: 10)

**Skill (S):**
- ✅ Guerreiro: 25 de dano, custa 10 MP, cooldown 2 turnos
- ✅ Mago: 30 de dano, custa 15 MP, cooldown 2 turnos
- ✅ Arqueiro: 2x8 = 16 de dano, custa 10 MP, cooldown 2 turnos
- ✅ Se sem MP ou em cooldown → fallback para ataque básico

**Defesa (D):**
- ✅ Guerreiro: 50% de redução
- ✅ Mago: 40% de redução
- ✅ Arqueiro: 30% de redução

**Cura (H):**
- ✅ Apenas Mago pode usar
- ✅ Cura 18 HP, custa 12 MP, cooldown 2 turnos
- ✅ Outras classes recebem erro ao tentar usar

### 4.3 Teste de Cooldowns
1. Use uma skill (`S`)
2. No próximo turno, tente usar skill novamente
3. **Verificar:** Deve fazer fallback para ataque básico

### 4.4 Teste de Timeout
1. Quando for seu turno, **não digite nada** por 20 segundos
2. **Verificar:** Servidor aplica defesa automática
3. Log do servidor deve mostrar: "Timeout para jogador X, defesa aplicada"

## Passo 5: Testar Fim de Jogo

### 5.1 Vitória por HP
Continue jogando até um jogador chegar a 0 HP:
- ✅ Mensagem: "=== Fim de jogo ==="
- ✅ Vencedor vê: "Vitória sua! Motivo: [motivo]"
- ✅ Perdedor vê: "Derrota. Motivo: [motivo]"
- ✅ Placar global é atualizado

### 5.2 Empate
Se ambos caírem no mesmo turno:
- ✅ Mensagem de empate
- ✅ Motivo: "Ambos caíram"

### 5.3 Desconexão
1. Durante o jogo, feche um cliente (Ctrl+C ou `Q`)
2. **Verificar:**
   - ✅ Cliente restante recebe mensagem de fim de jogo
   - ✅ Servidor registra desconexão no log
   - ✅ Vitória é atribuída ao jogador conectado

## Passo 6: Testar Placar Global

### 6.1 Solicitar Placar
Durante o jogo ou na espera:
- Digite: `F5` ou `G`
- **Verificar:** Recebe JSON com:
  ```json
  {"G":X,"M":Y,"R":Z,"Ativa":1,"Ultimo":"[último log]"}
  ```
  - `G`: Vitórias do Guerreiro
  - `M`: Vitórias do Mago
  - `R`: Vitórias do Arqueiro
  - `Ativa`: 1 se há partida ativa, 0 caso contrário
  - `Ultimo`: Último log de turno

### 6.2 Verificar Persistência
1. Jogue uma partida completa
2. Feche o servidor
3. Inicie o servidor novamente
4. **Verificar:** Placar deve persistir (memória compartilhada)

## Passo 7: Testar Comandos Especiais

### 7.1 Help
Digite: `help`
- ✅ Mostra lista de comandos disponíveis

### 7.2 Sair
Digite: `Q`
- ✅ Cliente desconecta graciosamente
- ✅ Servidor registra saída no log

## Passo 8: Testes de Estresse

### 8.1 Múltiplas Partidas
1. Jogue uma partida completa
2. Inicie outra partida (conecte 2 clientes novamente)
3. **Verificar:** Servidor deve aceitar novas conexões

### 8.2 Mensagens Inválidas
Teste entradas inválidas:
- ✅ Classe inválida (ex: `X`)
- ✅ Ação inválida (ex: `Z`)
- ✅ Mago tentando curar sem MP
- ✅ **Verificar:** Mensagens de erro apropriadas

## Passo 9: Verificar Logs e Memória Compartilhada

### 9.1 Log do Servidor
Abra `server.log` e verifique:
- ✅ Timestamps corretos
- ✅ Todas as mensagens RX/TX
- ✅ Eventos de conexão/desconexão
- ✅ Timeouts registrados
- ✅ Fim de partidas registrado

### 9.2 Memória Compartilhada
O arquivo `DUELRPG_MM.dat` contém o estado compartilhado:
- ✅ Vitórias por classe
- ✅ Flag de partida ativa
- ✅ Último log

## Checklist de Testes Rápidos

- [ ] Servidor inicia corretamente
- [ ] Dois clientes conectam simultaneamente
- [ ] Seleção de classe funciona para ambos
- [ ] Partida inicia após seleções
- [ ] Turnos alternam corretamente
- [ ] Ataque básico funciona
- [ ] Skills funcionam (com MP e cooldown)
- [ ] Defesa reduz dano corretamente
- [ ] Cura funciona apenas para Mago
- [ ] Timeout aplica defesa automática
- [ ] Fim de jogo por HP funciona
- [ ] Placar global é atualizado
- [ ] Desconexão é tratada corretamente
- [ ] Logs são gerados corretamente
- [ ] Comandos especiais (help, Q, F5) funcionam

## Problemas Comuns

### Servidor não inicia
- Verifique se a porta 5050 está livre
- Verifique permissões de escrita (para criar `server.log` e `DUELRPG_MM.dat`)

### Cliente não conecta
- Verifique se o servidor está rodando
- Verifique o endereço IP (use `127.0.0.1` para localhost)
- Verifique firewall do Windows

### Partida não inicia
- Verifique se ambos os clientes selecionaram classe
- Verifique logs do servidor para erros

### Erros de compilação
- Verifique se o compilador está instalado corretamente (MSVC ou MinGW)
- Verifique se Winsock2 está disponível (já incluído no Windows SDK)
- Tente usar `build.bat` com MSVC ou GCC (MinGW)

## Próximos Passos

Após testar manualmente, você pode:
1. Criar testes automatizados usando scripts
2. Testar com múltiplas partidas simultâneas (requer modificação)
3. Testar em rede (clientes em máquinas diferentes)
4. Verificar performance com logs longos

