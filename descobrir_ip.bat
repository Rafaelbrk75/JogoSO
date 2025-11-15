@echo off
echo ========================================
echo   DESCOBRIR IP DA MAQUINA (SERVIDOR)
echo ========================================
echo.
echo Procurando endereco IP...
echo.
ipconfig | findstr /i "IPv4"
echo.
echo ========================================
echo Use o IP acima para conectar os clientes
echo Exemplo: client.exe 192.168.1.100
echo ========================================
pause

