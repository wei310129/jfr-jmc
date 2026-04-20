@echo off
SET APP_JAR=target\jfr-jmc-0.0.1-SNAPSHOT.jar
SET JFR_SETTINGS=src\main\resources\jfr\custom-profile.jfc
SET OUTPUT_DIR=jfr-output

IF NOT EXIST %OUTPUT_DIR% mkdir %OUTPUT_DIR%

echo Select mode:
echo   1. Continuous recording (JMC live monitoring)
echo   2. Timed recording (60s then stop)
echo   3. JMX only (manual control via JMC)
SET /P MODE="Enter (1/2/3): "

IF "%MODE%"=="2" GOTO TIMED
IF "%MODE%"=="3" GOTO JMX_ONLY

:CONTINUOUS
echo [Mode 1] Continuous recording. Press Ctrl+C to stop.
java -XX:StartFlightRecording=name=continuous,settings=%JFR_SETTINGS%,disk=true,maxage=5m,dumponexit=true,filename=%OUTPUT_DIR%\recording.jfr -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9099 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.local.only=false -jar %APP_JAR%
GOTO END

:TIMED
echo [Mode 2] Recording for 60 seconds...
java -XX:StartFlightRecording=duration=60s,settings=%JFR_SETTINGS%,filename=%OUTPUT_DIR%\recording.jfr -jar %APP_JAR%
GOTO END

:JMX_ONLY
echo [Mode 3] JMX enabled. Connect JMC to localhost:9099
java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9099 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.local.only=false -jar %APP_JAR%
GOTO END

:END
echo Done. JFR file saved to %OUTPUT_DIR%\
pause
