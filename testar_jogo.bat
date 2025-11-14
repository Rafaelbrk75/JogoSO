@echo off
echo ========================================
echo   TESTE AUTOMATIZADO - Duel RPG Online
echo ========================================
echo.

REM Verificar se os executáveis existem
if not exist "server.exe" (
    echo [ERRO] server.exe nao encontrado!
    echo Execute build.bat primeiro para compilar.
    pause
    exit /b 1
)

if not exist "client.exe" (
    echo [ERRO] client.exe nao encontrado!
    echo Execute build.bat primeiro para compilar.
    pause
    exit /b 1
)

echo [OK] Executaveis encontrados.
echo.

REM Verificar se pthreadVC2.dll existe
if not exist "pthreadVC2.dll" (
    echo [AVISO] pthreadVC2.dll nao encontrado na pasta atual.
    echo Certifique-se de que esta no PATH ou na mesma pasta dos executaveis.
    echo.
)

echo ========================================
echo   INSTRUCOES PARA TESTE MANUAL
echo ========================================
echo.
echo 1. Abra TRES terminais separados
echo.
echo 2. No Terminal 1, execute:
echo    server.exe
echo.
echo 3. No Terminal 2, execute:
echo    client.exe
echo.
echo 4. No Terminal 3, execute:
echo    client.exe
echo.
echo 5. Siga as instrucoes na tela:
echo    - Selecione classes (G/M/R)
echo    - Jogue os turnos (A/S/D/H)
echo    - Teste comandos (F5 para placar, Q para sair)
echo.
echo ========================================
echo   VERIFICACOES POS-TESTE
echo ========================================
echo.
echo Apos testar, verifique:
echo - server.log foi criado/atualizado
echo - DUELRPG_MM.dat foi criado (memoria compartilhada)
echo - Nenhum erro foi exibido nos terminais
echo.
echo Para mais detalhes, consulte GUIA_TESTES.md
echo.
pause

