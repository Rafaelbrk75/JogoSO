# 🌐 Como Jogar em Máquinas Diferentes

Sim! O jogo **DUEL RPG ONLINE** suporta jogar em máquinas diferentes na mesma rede. O servidor já está configurado para aceitar conexões de qualquer IP.

## 📋 Pré-requisitos

1. **Máquinas na mesma rede**: Todas as máquinas devem estar na mesma rede local (Wi-Fi ou cabo)
2. **Firewall**: O firewall do Windows pode bloquear conexões. Veja como configurar abaixo
3. **IP do servidor**: Você precisa saber o endereço IP da máquina que vai rodar o servidor

## 🖥️ Configuração

### Passo 1: Descobrir o IP da máquina do servidor

Na máquina que vai rodar o **servidor**, abra o PowerShell ou CMD e execute:

```cmd
ipconfig
```

Procure por "Endereço IPv4" na seção da sua conexão de rede. Exemplo:
```
Endereço IPv4. . . . . . . . . . . . . . : 192.168.1.100
```

**Anote este IP!** Este será o endereço que os clientes usarão para conectar.

### Passo 2: Configurar o Firewall do Windows

O firewall pode bloquear conexões de entrada. Você precisa permitir o servidor:

#### Opção A: Permitir via Firewall do Windows (Recomendado)

1. Abra o **Firewall do Windows Defender**
2. Clique em **Configurações Avançadas**
3. Clique em **Regras de Entrada** → **Nova Regra**
4. Selecione **Porta** → **Próximo**
5. Selecione **TCP** e digite a porta: **5050** → **Próximo**
6. Selecione **Permitir a conexão** → **Próximo**
7. Marque todas as opções (Domínio, Privada, Pública) → **Próximo**
8. Dê um nome: "DUEL RPG Server" → **Concluir**

#### Opção B: Desabilitar temporariamente (Apenas para teste)

⚠️ **Não recomendado para uso permanente**

1. Abra o **Firewall do Windows Defender**
2. Clique em **Ativar ou desativar o Firewall do Windows Defender**
3. Desative temporariamente para ambas as redes (Privada e Pública)
4. **Lembre-se de reativar depois!**

### Passo 3: Executar o servidor

Na máquina do servidor, execute normalmente:

```cmd
server.exe
```

O servidor ficará aguardando conexões na porta **5050**.

### Passo 4: Executar os clientes

#### Na máquina do Jogador 1:

```cmd
client.exe 192.168.1.100
```

(Substitua `192.168.1.100` pelo IP real da máquina do servidor)

#### Na máquina do Jogador 2:

```cmd
client.exe 192.168.1.100
```

(Use o mesmo IP do servidor)

## 📝 Exemplo Completo

**Cenário**: 
- Servidor na máquina com IP `192.168.1.100`
- Jogador 1 na máquina `192.168.1.101`
- Jogador 2 na máquina `192.168.1.102`

**Máquina do Servidor (192.168.1.100)**:
```cmd
C:\jogoSO> server.exe
Servidor iniciado na porta 5050
Aguardando clientes...
```

**Máquina do Jogador 1 (192.168.1.101)**:
```cmd
C:\jogoSO> client.exe 192.168.1.100
Conectando ao servidor 192.168.1.100:5050...
✅ Conectado como Jogador 1
```

**Máquina do Jogador 2 (192.168.1.102)**:
```cmd
C:\jogoSO> client.exe 192.168.1.100
Conectando ao servidor 192.168.1.100:5050...
✅ Conectado como Jogador 2
```

## 🔧 Solução de Problemas

### Erro: "Falha ao conectar"

**Possíveis causas:**
1. **IP incorreto**: Verifique se digitou o IP correto do servidor
2. **Firewall bloqueando**: Configure o firewall (veja Passo 2)
3. **Servidor não está rodando**: Certifique-se de que o servidor está executando
4. **Máquinas em redes diferentes**: Certifique-se de que estão na mesma rede

### Como verificar se o servidor está acessível

Na máquina do cliente, teste a conexão:

```cmd
telnet 192.168.1.100 5050
```

Se conectar (tela preta), o servidor está acessível. Pressione `Ctrl+C` para sair.

**Nota**: Se `telnet` não estiver disponível, você pode usar PowerShell:

```powershell
Test-NetConnection -ComputerName 192.168.1.100 -Port 5050
```

### Erro: "Porta já em uso"

Isso significa que o servidor já está rodando ou outra aplicação está usando a porta 5050.

**Solução**: 
- Feche o servidor anterior
- Ou mude a porta no código (não recomendado)

## 🌍 Jogar pela Internet (Avançado)

Para jogar pela Internet (não apenas rede local), você precisaria:

1. **IP Público**: Ter um IP público ou usar um serviço de túnel (Hamachi, ZeroTier, etc.)
2. **Port Forwarding**: Configurar o roteador para redirecionar a porta 5050
3. **Segurança**: Considerar autenticação e criptografia

⚠️ **Atenção**: O jogo atual não tem segurança para uso na Internet pública. Use apenas em redes confiáveis.

## ✅ Resumo Rápido

1. Descubra o IP da máquina do servidor (`ipconfig`)
2. Configure o firewall para permitir porta 5050
3. Execute `server.exe` na máquina do servidor
4. Execute `client.exe <IP_DO_SERVIDOR>` em cada máquina cliente

**Exemplo**: `client.exe 192.168.1.100`

Pronto! Agora você pode jogar com seus amigos na mesma rede! 🎮

