# jfr-jmc — JVM Profiling、JFR 與 JMC 實作練習

以 Spring Boot 提供 CPU、記憶體配置、執行緒鎖爭用與慢速操作的負載入口，練習使用 **Java Flight Recorder (JFR)** 錄製事件，再用 **Java Mission Control (JMC)** 分析 JVM 行為。Service 自訂事件將操作名稱、輸入規模與結果連結到 profiling 資料，方便對照應用程式與 JVM 層的觀察。

## 工程重點與驗證入口

| 想回答的問題 | 實作與觀察方式 |
| --- | --- |
| 延遲來自 CPU、配置、鎖還是等待？ | 以不同負載 API 控制觸發條件，再對照 CPU sample、GC／Heap、Monitor 與 Thread Sleep；不能只從單一事件推論根因 |
| 如何把 JVM 行為連回業務操作？ | ServiceOperation 自訂事件帶入操作名稱、輸入規模、結果及 duration，搭配 Service 測試讀取錄製結果 |
| 如何取得適合問題的錄製資料？ | 自訂 profile、保留最近 5 分鐘、60 秒定時與 JMX 手動錄製三種模式，再以 JMC Event Browser 核對 |
| 哪些驗證已自動化？ | 自訂事件錄製／讀取與 Service 整合測試；JMC 圖形介面操作及 HTTP 請求事件自動產生未包含在內 |

使用流程：選一種負載 → 選錄製模式 → 觸發 API → 核對操作事件與 JVM 事件 → 記錄觀察與下一個假設。Repository 未附效能改善幅度或示例 .jfr，不將可觸發負載直接當成調校成果。

## 專案範圍

- 透過 HTTP API 觸發不同負載，觀察 CPU 取樣、物件配置、GC、鎖爭用與 Thread Sleep。
- 在 Service 中實際錄製 `ServiceOperation` 自訂事件，並透過測試讀取事件欄位與 duration。
- 使用自訂 JFR profile，以及連續錄製、60 秒定時錄製和 JMX 三種啟動模式。
- 使用 JMC 離線開啟 `.jfr`，或連線至 JMX 觀察運行中的 JVM。

### 自訂事件的實作狀態

| 事件名稱 | 欄位 | 目前狀態 |
|---|---|---|
| `tw.com.aidenmade.ServiceOperation` | `operationName`、`inputSize`、`result`，以及事件 duration | CPU、Memory、Thread Service 已使用 `begin()` / `end()` / `commit()` 錄製；有事件讀取與 Service 整合測試 |
| `tw.com.aidenmade.HttpRequest` | `method`、`uri`、`statusCode` | 事件類別已定義，也列在 JFR profile；尚未接入 HTTP Filter / Interceptor 等請求流程，呼叫 API 不會自動產生此事件 |

## 技術與環境

| 項目 | 設定 |
|---|---|
| Java | 17（`pom.xml` 編譯目標） |
| Spring Boot | 4.0.5 |
| Build | Maven Wrapper：`mvnw` / `mvnw.cmd` |
| JVM 分析 | JDK 內建 JFR；JMC 需另外安裝 |
| HTTP | 預設 `http://localhost:8080` |
| JMX | 腳本模式 1、3 使用 `localhost:9099` |

專案同時引入 WebFlux 與 WebMVC starter；負載範例刻意執行 CPU、配置與等待操作，不能據此視為完整的非阻塞服務設計。

## 啟動與錄製

以下指令皆從專案根目錄執行。

### 一般啟動

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS：`./mvnw spring-boot:run`。此方式未透過啟動腳本自動開始 JFR 錄製。

### 打包並執行 JFR 腳本

Windows PowerShell：

```powershell
.\mvnw.cmd clean package
.\scripts\run-with-jfr.bat
```

Linux / macOS：

```bash
./mvnw clean package
chmod +x scripts/run-with-jfr.sh
./scripts/run-with-jfr.sh
```

腳本使用 `target/jfr-jmc-0.0.1-SNAPSHOT.jar` 與 `src/main/resources/jfr/custom-profile.jfc`，提供下列模式：

| 模式 | 錄製行為 | JMX |
|---|---|---|
| `1` 連續錄製 | `disk=true`、`maxage=5m`，保留最近 5 分鐘資料；JVM 結束時輸出檔案 | 啟用 9099 |
| `2` 定時錄製 | 錄製 60 秒後輸出檔案；只有錄製停止，Spring Boot 應用仍繼續運行 | 腳本未啟用 |
| `3` 僅開 JMX | 不會自動開始錄製；由 JMC 手動開始錄製或匯出 | 啟用 9099 |

現有腳本的 JMX 關閉驗證與 TLS，且 `local.only=false`；請用於受控的本機練習，不要將 9099 開放至外網。正式遠端連線需另行配置驗證、TLS 與網路存取限制。

## Demo API 與觀察方向

Base URL：`http://localhost:8080/demo`。先從較小參數開始，再增加負載或重複呼叫，對照錄製時間與事件數量。

| HTTP GET 路徑 | 負載情境 | JMC 觀察方向 |
|---|---|---|
| `/cpu/fibonacci/{n}` | 遞迴費氏數列 | CPU 取樣、Hot Methods |
| `/cpu/primes/{limit}` | 質數計算與陣列存取 | CPU 取樣、配置活動 |
| `/memory/allocate?items=10000` | 大量小物件配置 | Allocation、GC、Heap 變化 |
| `/memory/large-array?mb=50` | 大型陣列配置 | 配置規模、Heap / GC |
| `/thread/contention?threads=8&iterations=10000` | 多執行緒鎖爭用 | `jdk.JavaMonitorEnter`、Blocked Threads |
| `/thread/slow?delayMs=500` | 等待操作 | `jdk.ThreadSleep`、ServiceOperation duration |

PowerShell 範例（在另一個終端機執行）：

```powershell
Invoke-RestMethod 'http://localhost:8080/demo/cpu/fibonacci/30'
Invoke-RestMethod 'http://localhost:8080/demo/memory/allocate?items=20000'
Invoke-RestMethod 'http://localhost:8080/demo/thread/contention?threads=8&iterations=10000'
Invoke-RestMethod 'http://localhost:8080/demo/thread/slow?delayMs=500'
```

Linux / macOS 可使用 `curl 'http://localhost:8080/demo/cpu/fibonacci/30'`，其餘路徑同上。

JFR 事件受 profile 開關、取樣週期與 duration threshold 影響；短時間或較小負載不保證出現 GC、CPU 取樣或鎖爭用事件。分析時應確認負載發生在錄製期間，並查看實際事件數量。

## JMC 操作流程

### 離線分析 `.jfr`

1. 打包後執行腳本，選擇模式 `2`。
2. 在另一個終端機於 60 秒錄製期間呼叫 Demo API。
3. 錄製完成後，到 `jfr-output/` 找到輸出的 `.jfr`；應用程式此時仍在運行，需要時自行停止。
4. 用 JMC 開啟錄製檔，查看 CPU / Method Profiling、Memory / GC 與 Threads。
5. 在 Event Browser 搜尋 `tw.com.aidenmade.ServiceOperation`，檢查操作名稱、輸入規模、結果與 duration，對照剛才呼叫的 API。

### 即時連線

1. 執行腳本，選擇模式 `1` 或 `3`。
2. 在 JMC 新增 JMX 連線至 `localhost:9099`。
3. 模式 `1` 已啟動 JFR，可查看運行中的 JVM，並依需求取得錄製資料；模式 `3` 需在 JMC 手動開始 Flight Recording。
4. 執行負載 API，再查看 JVM 與事件變化。分析自訂事件時，確認錄製設定已啟用對應事件；可參考專案的 `custom-profile.jfc`。

JMC 版本不同時，頁籤名稱可能略有差異，可使用 Event Browser 核對事件類型與欄位。

## 錄製檔位置

- Windows 腳本：`jfr-output/recording.jfr`，使用固定檔名，後續執行應留意同名輸出。
- Linux / macOS 腳本：`jfr-output/recording_YYYYMMDD_HHMMSS.jfr`。
- 模式 `3` 不會自動輸出錄製檔；需自行開始錄製並匯出。
- Repository 未附示例 `.jfr`；`jfr-output/` 和 `*.jfr` 已在 `.gitignore` 中排除。

## 自動化測試

```powershell
.\mvnw.cmd test
```

Linux / macOS：`./mvnw test`。

| 測試類別 | 範圍 |
|---|---|
| `JfrCustomEventTest` | ServiceOperation 自訂事件的錄製、讀取、欄位與 duration |
| `JfrBuiltinEventTest` | GC、Thread Sleep、CPU Load 與 Monitor 事件的錄製示範及部分斷言 |
| `JfrServiceIntegrationTest` | 呼叫 Spring Service，錄製並驗證 ServiceOperation 事件 |
| `JfrJmcApplicationTests` | Spring Boot context 啟動 |

現有整合測試以 Service 層為入口，沒有驗證 HTTP 請求事件的自動產生，也沒有自動化操作 JMC 圖形介面。

## 專案重點路徑

```text
src/main/java/tw/com/aidenmade/jfrjmc/
  controller/DemoController.java           # 負載 API
  service/                                # CPU、Memory、Thread 負載與事件錄製
  jfr/ServiceOperationJfrEvent.java        # 已接入 Service 的事件
  jfr/HttpRequestJfrEvent.java             # 已定義、尚未接入請求流程的事件
src/main/resources/jfr/custom-profile.jfc # JFR 錄製設定
src/test/java/tw/com/aidenmade/jfrjmc/     # 事件與 Service 測試
scripts/run-with-jfr.bat                  # Windows 啟動腳本
scripts/run-with-jfr.sh                   # Linux / macOS 啟動腳本
```

