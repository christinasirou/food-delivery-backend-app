@echo off

REM
taskkill /f /fi "WINDOWTITLE eq Reducer*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Master*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Worker-*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Manager*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Client*" >nul 2>&1
