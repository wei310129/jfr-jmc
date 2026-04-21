package tw.com.aidenmade.jfrjmc.jfr;

import jdk.jfr.*;

// 穩定事件名稱可讓錄製檔、測試與 JMC 查詢使用同一個 key。
@Name("tw.com.aidenmade.ServiceOperation")
@Label("Service Operation")
@Category({"Application", "Service"})
@Description("Tracks service-level operations with input size and result")
// Service 層事件數量相對可控，保留 stack trace 有助於定位熱點來源。
@StackTrace(true)
public class ServiceOperationJfrEvent extends Event {

    // operationName 作為維度欄位，讓不同 service 方法可在同一事件類型下分組。
    @Label("Operation Name")
    public String operationName;

    // inputSize 代表負載規模，用來和 duration/GC 次數做關聯分析。
    @Label("Input Size")
    public long inputSize;

    // result 建議存摘要字串（例如 count=xxx），避免高基數內容造成事件膨脹。
    @Label("Result")
    public String result;
}
