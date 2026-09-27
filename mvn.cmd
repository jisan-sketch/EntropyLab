@echo off
if not defined JAVA_HOME set "JAVA_HOME=C:\Users\HP\.jdks\ms-21.0.12.1"
"E:\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" %*
