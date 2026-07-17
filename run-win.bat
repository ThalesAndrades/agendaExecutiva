@echo off
REM ============================================================================
REM  Agenda Executiva - atalho de execucao no Windows (rodar do codigo-fonte)
REM  Basta dar duplo clique. Requer o JDK 21+ instalado uma unica vez.
REM ============================================================================
setlocal
cd /d "%~dp0"
chcp 65001 >nul

echo(
echo ==================================================
echo   Agenda Executiva - iniciando...
echo ==================================================
echo(

where java >nul 2>nul
if errorlevel 1 goto :no_java

echo Compilando e abrindo o aplicativo.
echo (A primeira execucao baixa dependencias e pode demorar alguns minutos.)
echo(
call mvnw.cmd -q compile javafx:run
if errorlevel 1 goto :run_error

echo(
echo Aplicativo encerrado. Seus dados ficam salvos em:
echo   %USERPROFILE%\.agenda-pessoal\agenda.db
pause
exit /b 0

:no_java
echo [ERRO] Java nao encontrado no seu PC.
echo(
echo Instale o JDK 21 (gratuito, Eclipse Temurin):
echo   https://adoptium.net/temurin/releases/?version=21
echo(
echo Depois de instalar, feche esta janela e abra o run-win.bat novamente.
pause
exit /b 1

:run_error
echo(
echo [ERRO] Nao foi possivel iniciar. Confirme que o JDK 21 ou superior
echo        esta instalado (execute:  java -version).
pause
exit /b 1
