package io.oryxos.tool.notify;

public interface NotifyChannelAdapter {

    String channelType();

    void send(NotifyTarget target, String content);
}
