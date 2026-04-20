package tw.com.aidenmade.jfrjmc.jfr;

import jdk.jfr.*;

@Name("tw.com.aidenmade.ServiceOperation")
@Label("Service Operation")
@Category({"Application", "Service"})
@Description("Tracks service-level operations with input size and result")
@StackTrace(true)
public class ServiceOperationJfrEvent extends Event {

    @Label("Operation Name")
    public String operationName;

    @Label("Input Size")
    public long inputSize;

    @Label("Result")
    public String result;
}
