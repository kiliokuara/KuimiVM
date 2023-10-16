package tencentlibfekit.vmservice;

import kotlin.coroutines.Continuation;

public interface IChannelProxy {
    public static final Object SUSPENDED = kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED();

    Object sendMessage(String remark, String command, long botUin, byte[] data, Continuation<? extends ChannelResult> continuation);

    public record ChannelResult(String command, byte[] data) {
    }
}
