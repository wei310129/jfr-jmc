package tw.com.aidenmade.jfrjmc.jfr;

import jdk.jfr.*;

// 統一事件名稱可讓測試與 JMC 查詢穩定，不受類別搬移影響。
@Name("tw.com.aidenmade.HttpRequest")
@Label("HTTP Request")
// 放在 Application/HTTP 分類，便於在 Event Browser 依領域過濾。
@Category({"Application", "HTTP"})
@Description("Tracks HTTP request processing time and result")
// HTTP 事件通常數量多，關閉 stack trace 可降低錄製成本與檔案體積。
@StackTrace(false)
public class HttpRequestJfrEvent extends Event {

    // method 用來區分 GET/POST 等路由操作型態。
    @Label("HTTP Method")
    public String method;

    // uri 用來對應實際端點，方便與 controller latency 對照。
    @Label("Request URI")
    public String uri;

    // statusCode 協助把慢請求與錯誤請求（4xx/5xx）關聯分析。
    @Label("Status Code")
    public int statusCode;
}
