package kfc.vivo50.code45;

import net.mamoe.mirai.console.data.Value;
import net.mamoe.mirai.console.data.java.JavaAutoSavePluginConfig;

public class PluginConfig extends JavaAutoSavePluginConfig {
    public static final PluginConfig INSTANCE = new PluginConfig();

    public PluginConfig() {
        super("code45_config");
    }

    public final Value<String> serverHost = value("serverHost", "localhost");
    public final Value<Integer> serverPort = value("serverPort", 8888);
    public final Value<String> authKey = value("authKey", "11451419198101145141919810114514");
    public final Value<String> serverIdentityKey = value("serverIdentityKey", "11451419198101145141919810114514");

}
