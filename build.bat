@echo off
setlocal

REM Tenta usar GCC primeiro
where gcc >nul 2>&1
if errorlevel 1 goto TRY_MSVC

echo Compilando com GCC (MinGW)...
gcc -Wall -Wextra -O2 -o server.exe server.c game.c net.c sharedmem.c queue.c protocol.c -lws2_32
if errorlevel 1 goto END
gcc -Wall -Wextra -O2 -o client.exe client.c net.c protocol.c -lws2_32
if errorlevel 1 goto END
goto SUCCESS

:TRY_MSVC
REM Se GCC nao encontrado, tenta MSVC
where cl >nul 2>&1
if errorlevel 1 goto NO_COMPILER

echo Compilando com CL (MSVC)...
echo.

cl /nologo /W4 /Zi /D_CRT_SECURE_NO_WARNINGS /Fe:server.exe server.c game.c net.c sharedmem.c queue.c protocol.c ws2_32.lib
if errorlevel 1 (
    echo.
    echo [ERRO] Falha na compilacao do servidor com MSVC.
    goto END
)

cl /nologo /W4 /Zi /D_CRT_SECURE_NO_WARNINGS /Fe:client.exe client.c net.c protocol.c ws2_32.lib
if errorlevel 1 goto END
goto SUCCESS

:NO_COMPILER

echo [ERRO] Nenhum compilador encontrado!
echo Instale MinGW (GCC) ou Visual Studio (MSVC)
goto END

:SUCCESS
echo.
echo [OK] Compilacao concluida com sucesso!
echo [NOTA] Este jogo nao usa pthreads, apenas Winsock e memoria compartilhada.
echo.

:END
endlocal