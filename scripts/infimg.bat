@echo off
rem Windows start script. Assumes "java" is on PATH.
set DIR=%~dp0..
java -jar "%DIR%\target\infimg-1.3-jar-with-dependencies.jar" %*
