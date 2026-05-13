@ECHO OFF
SETLOCAL

SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET APP_HOME=%DIRNAME%
IF "%GRADLE_USER_HOME%"=="" SET GRADLE_USER_HOME=%APP_HOME%\.gradle-user-home

IF NOT "%JAVA_HOME%"=="" GOTO findJavaFromJavaHome
SET JAVA_EXE=java.exe
GOTO execute

:findJavaFromJavaHome
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
IF EXIST "%JAVA_EXE%" GOTO execute
ECHO JAVA_HOME is set to an invalid directory: %JAVA_HOME%
EXIT /B 1

:execute
"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
