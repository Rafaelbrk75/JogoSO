@echo off
echo Fechando processos do jogo...
taskkill /F /IM server.exe 2>nul
taskkill /F /IM client.exe 2>nul
echo Processos fechados.
pause

