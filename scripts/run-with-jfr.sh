#!/usr/bin/env bash
# ============================================================
# 以 JFR 錄製模式啟動 Spring Boot 應用程式
# JMC 可透過 JMX (port 9099) 連線進行即時監控
# ============================================================

APP_JAR="target/jfr-jmc-0.0.1-SNAPSHOT.jar"
JFR_SETTINGS="src/main/resources/jfr/custom-profile.jfc"
OUTPUT_DIR="jfr-output"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

mkdir -p "$OUTPUT_DIR"

echo "============================================================"
echo " 啟動模式："
echo "  1. 連續錄製（供 JMC 即時分析，Ctrl+C 時自動儲存）"
echo "  2. 定時錄製（錄製 60 秒後自動停止）"
echo "  3. 僅啟用 JMX（JMC 連線後手動控制錄製）"
echo "============================================================"
read -rp "請選擇 (1/2/3) [預設 1]: " MODE
MODE=${MODE:-1}

JMX_FLAGS=(
  "-Dcom.sun.management.jmxremote"
  "-Dcom.sun.management.jmxremote.port=9099"
  "-Dcom.sun.management.jmxremote.authenticate=false"
  "-Dcom.sun.management.jmxremote.ssl=false"
  "-Dcom.sun.management.jmxremote.local.only=false"
)

case "$MODE" in
  1)
    echo "[模式 1] 連續錄製，JFR 檔將儲存至 $OUTPUT_DIR/"
    java \
      "-XX:StartFlightRecording=name=continuous,settings=$JFR_SETTINGS,disk=true,maxage=5m,dumponexit=true,filename=$OUTPUT_DIR/recording_$TIMESTAMP.jfr" \
      "${JMX_FLAGS[@]}" \
      -jar "$APP_JAR"
    ;;
  2)
    echo "[模式 2] 錄製 60 秒後自動停止..."
    java \
      "-XX:StartFlightRecording=duration=60s,settings=$JFR_SETTINGS,filename=$OUTPUT_DIR/recording_$TIMESTAMP.jfr" \
      -jar "$APP_JAR"
    ;;
  3)
    echo "[模式 3] JMX 啟用，請用 JMC 連線到 localhost:9099 ..."
    java \
      "${JMX_FLAGS[@]}" \
      -jar "$APP_JAR"
    ;;
esac

echo "應用程式已停止。JFR 檔案位於 $OUTPUT_DIR/"
