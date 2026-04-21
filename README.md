# jfr-jmc

一個用來練習 **Java Flight Recorder (JFR)** 與 **Java Mission Control (JMC)** 的 Spring Boot 專案。

這個專案提供多種可重現的負載情境（CPU、記憶體、執行緒爭用、慢速操作），並搭配自定義 JFR 事件，方便你在 JMC 中觀察系統行為。

## 你可以在這個專案練習什麼

- 用 API 觸發 CPU / Memory / Thread 相關負載
- 觀察 JDK 內建事件（GC、ThreadSleep、Monitor Enter 等）
- 觀察自定義事件：
  - `tw.com.aidenmade.ServiceOperation`
  - `tw.com.aidenmade.HttpRequest`
- 用 JFR 檔案離線分析，或透過 JMX 用 JMC 即時監控

## 技術與版本

- Java 17（`pom.xml`）
- Spring Boot 4.0.5
- Maven Wrapper（`mvnw` / `mvnw.cmd`）

## 專案重點路徑

- `src/main/java/tw/com/aidenmade/jfrjmc/controller/DemoController.java`
- `src/main/java/tw/com/aidenmade/jfrjmc/jfr/ServiceOperationJfrEvent.java`
- `src/main/java/tw/com/aidenmade/jfrjmc/jfr/HttpRequestJfrEvent.java`
- `src/main/resources/jfr/custom-profile.jfc`
- `scripts/run-with-jfr.bat`
- `scripts/run-with-jfr.sh`

## 啟動方式

### 1) 一般啟動（不強制 JFR）

```powershell
cd D:\my-project\jfr-jmc
.\mvnw.cmd spring-boot:run
```

預設服務位置：`http://localhost:8080`

### 2) 先打包成 JAR（JFR 腳本模式需要）

```powershell
cd D:\my-project\jfr-jmc
.\mvnw.cmd clean package -DskipTests
```

### 3) 用腳本啟動 JFR（Windows）

```powershell
cd D:\my-project\jfr-jmc
.\scripts\run-with-jfr.bat
```

腳本提供 3 種模式：

- `1` 連續錄製（支援 JMC 即時監控，結束時輸出檔案）
- `2` 定時錄製（60 秒後自動停止）
- `3` 僅開 JMX（手動由 JMC 控制錄製）

JMX 預設連線埠：`9099`

### 4) Linux / macOS（可選）

```bash
cd /path/to/jfr-jmc
chmod +x scripts/run-with-jfr.sh
./scripts/run-with-jfr.sh
```

## Demo API

Base URL: `http://localhost:8080/demo`

- `GET /cpu/fibonacci/{n}`：遞迴費氏數列（CPU 熱點）
- `GET /cpu/primes/{limit}`：質數篩（CPU + 陣列存取）
- `GET /memory/allocate?items=10000`：大量小物件分配（GC 觀察）
- `GET /memory/large-array?mb=50`：大型陣列分配（Heap 觀察）
- `GET /thread/contention?threads=8&iterations=10000`：鎖爭用（Monitor 事件）
- `GET /thread/slow?delayMs=500`：慢速操作（ThreadSleep 事件）

範例：

```powershell
curl "http://localhost:8080/demo/cpu/fibonacci/35"
curl "http://localhost:8080/demo/memory/allocate?items=20000"
curl "http://localhost:8080/demo/thread/contention?threads=12&iterations=20000"
```

## 測試

執行全部測試：

```powershell
cd D:\my-project\jfr-jmc
.\mvnw.cmd test
```

重點測試類別：

- `JfrCustomEventTest`：驗證自定義事件可被錄製與讀取
- `JfrBuiltinEventTest`：示範 JDK 內建事件錄製
- `JfrServiceIntegrationTest`：整合測試，呼叫 Service 並驗證事件內容

## 在 JMC 可以觀察的重點

- **CPU**：`Method Profiling`、`Hot Methods`
- **Memory / GC**：`Garbage Collections`、`Heap` 趨勢
- **Thread**：`Threads`、`Java Monitor Blocked` / `Lock Instances`
- **Custom Events**：在 `Event Browser` 搜尋 `tw.com.aidenmade.*`

## JFR 輸出位置

- 預設輸出資料夾：`jfr-output/`
- 專案內已有示例檔：`jfr-output/recording.jfr`

---

如果你想，我可以下一步再幫你補一份「JMC 實際操作流程（從連線到解讀圖表）」的圖文版 README。
