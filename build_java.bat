@echo off
setlocal

set SRC_DIR=%~dp0java\src
set OUT_DIR=%~dp0java\build

if not exist "%SRC_DIR%" (
    echo Pasta java\src nao encontrada.
    goto :END
)

if exist "%OUT_DIR%" (
    rmdir /S /Q "%OUT_DIR%"
)
mkdir "%OUT_DIR%"

echo Compilando fontes Java...
javac -encoding UTF-8 -d "%OUT_DIR%" ^
    "%SRC_DIR%\common\ActionType.java" ^
    "%SRC_DIR%\common\PlayerClass.java" ^
    "%SRC_DIR%\common\TurnCommand.java" ^
    "%SRC_DIR%\common\Protocol.java" ^
    "%SRC_DIR%\common\SharedState.java" ^
    "%SRC_DIR%\server\PlayerState.java" ^
    "%SRC_DIR%\server\GameState.java" ^
    "%SRC_DIR%\server\TurnOutcome.java" ^
    "%SRC_DIR%\server\SharedMemoryManager.java" ^
    "%SRC_DIR%\server\ClientSession.java" ^
    "%SRC_DIR%\server\ServerLogger.java" ^
    "%SRC_DIR%\server\DuelServer.java" ^
    "%SRC_DIR%\client\ClientState.java" ^
    "%SRC_DIR%\client\ImageAssets.java" ^
    "%SRC_DIR%\client\PlayerPanel.java" ^
    "%SRC_DIR%\client\DuelClient.java" ^
    "%SRC_DIR%\client\DuelClientGUI.java"

if errorlevel 1 (
    echo Compilacao falhou.
    goto :END
)

if "%JAVAFX_HOME%"=="" (
    echo [AVISO] Variavel JAVAFX_HOME nao definida. Cliente JavaFX nao sera compilado.
) else (
    if not exist "%JAVAFX_HOME%\lib" (
        echo [AVISO] Caminho "%JAVAFX_HOME%\lib" inexistente. Cliente JavaFX nao sera compilado.
    ) else (
        echo Compilando cliente JavaFX...
        javac -encoding UTF-8 -d "%OUT_DIR%" -cp "%OUT_DIR%" ^
            --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls,javafx.graphics ^
            "%SRC_DIR%\clientfx\FxImageAssets.java" ^
            "%SRC_DIR%\clientfx\FxPlayerCard.java" ^
            "%SRC_DIR%\clientfx\DuelClientFX.java"
        if errorlevel 1 (
            echo Compilacao do cliente JavaFX falhou.
            goto :END
        )
    )
)

set RES_DIR=%~dp0java\resources
if exist "%RES_DIR%" (
    echo Copiando recursos...
    xcopy "%RES_DIR%" "%OUT_DIR%" /E /I /Y >nul
)

echo.
echo Binarios gerados em java\build.
echo Servidor: java -cp java\build server.DuelServer
echo Cliente CLI:  java -cp java\build client.DuelClient [endereco]
echo Cliente GUI Swing:  java -cp java\build client.DuelClientGUI [endereco]
if not "%JAVAFX_HOME%"=="" if exist "%JAVAFX_HOME%\lib" (
    echo Cliente JavaFX: java --module-path "%%JAVAFX_HOME%%\lib" --add-modules javafx.controls,javafx.graphics -cp java\build clientfx.DuelClientFX [endereco]
)

:END
endlocal

