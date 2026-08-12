package com.project.meet.rtc.api;

import java.util.List;

public record IceServersResponse(List<IceServer> iceServers) {
}
