# Como Resolver o Erro do pthread.h

## Problema
```
fatal error C1083: Não é possível abrir arquivo incluir: 'pthread.h': No such file or directory
```

## Soluções

### Solução 1: Usar GCC (MinGW) - RECOMENDADO ✅

A forma mais fácil é usar o GCC que já tem suporte nativo ao pthreads:

1. **Instale o MinGW-w64:**
   - Baixe de: https://www.mingw-w64.org/downloads/
   - Ou use o instalador MSYS2: https://www.msys2.org/
   - Ou use o Chocolatey: `choco install mingw`

2. **Adicione ao PATH:**
   - Adicione `C:\MinGW\bin` (ou caminho onde instalou) ao PATH do Windows

3. **Compile:**
   ```batch
   build.bat
   ```
   O script agora detecta automaticamente o GCC e usa ele.

### Solução 2: Instalar pthreads-w32 para MSVC

Se você precisa usar o MSVC (Visual Studio):

1. **Baixe o pthreads-w32:**
   - Site oficial: https://sourceware.org/pthreads-win32/
   - Ou use uma versão pré-compilada: https://github.com/GerHobbelt/pthread-win32

2. **Estrutura de diretórios necessária:**
   ```
   pthreads-w32/
   ├── include/
   │   └── pthread.h
   ├── lib/
   │   ├── pthreadVC2.lib  (ou pthreadVC2.lib)
   │   └── pthreadVC2.dll
   ```

3. **Configure o build.bat:**
   Adicione os caminhos de inclusão e biblioteca:
   ```batch
   cl /I"caminho\para\pthreads-w32\include" /nologo /W4 /Zi /D_CRT_SECURE_NO_WARNINGS /Fe:server.exe server.c game.c net.c sharedmem.c queue.c protocol.c ws2_32.lib "caminho\para\pthreads-w32\lib\pthreadVC2.lib"
   ```

4. **Copie a DLL:**
   - Copie `pthreadVC2.dll` para a pasta do projeto (já existe `pthreadVC2.dll` na pasta)

### Solução 3: Usar vcpkg (Gerenciador de Pacotes)

Se você usa Visual Studio com vcpkg:

```batch
vcpkg install pthreads
```

Depois configure o Visual Studio para usar os pacotes do vcpkg.

## Verificação

Após instalar, teste:

```batch
build.bat
```

Se usar GCC:
```batch
gcc --version
```

Se usar MSVC:
```batch
cl
```

## Recomendação

**Use o GCC (MinGW)** - é mais simples e funciona imediatamente no Windows com pthreads.

