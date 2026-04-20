package tw.com.aidenmade.jfrjmc.jfr;

import jdk.jfr.*;

@Name("tw.com.aidenmade.HttpRequest")
@Label("HTTP Request")
@Category({"Application", "HTTP"})
@Description("Tracks HTTP request processing time and result")
@StackTrace(false)
public class HttpRequestJfrEvent extends Event {

    @Label("HTTP Method")
    public String method;

    @Label("Request URI")
    public String uri;

    @Label("Status Code")
    public int statusCode;
}
