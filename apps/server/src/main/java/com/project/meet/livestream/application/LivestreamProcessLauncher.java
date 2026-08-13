package com.project.meet.livestream.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Seam between "start a livestream" and "actually spawn a transcoder
 * process" — {@code FfmpegProcessLauncher} (package {@code livestream.infrastructure})
 * is the real implementation; tests substitute a fake so the suite never
 * needs a real `ffmpeg` binary on the machine running it (mirrors how
 * {@code LiveKitMediaServer}'s token issuance is tested without a live
 * LiveKit server).
 */
public interface LivestreamProcessLauncher {

	LivestreamProcessHandle launch(UUID meetingId, Path playlistPath) throws IOException;
}
