package tencentlibfekit.vmservice;

import tencentlibfekit.proto.DeviceInfoProto;

import java.util.Map;

public interface IVMService {
    VMSignResult sign(int seqId, String cmdName, byte[] data, Map<String, Object> extArgs);

    byte[] tlv(int type, Map<String, Object> extArgs, byte[] content);

    void initialize(Map<String, Object> args, DeviceInfoProto deviceInfoProto, IChannelProxy channelProxy);
}
