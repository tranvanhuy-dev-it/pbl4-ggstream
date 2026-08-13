package com.project.meet.livestream.application;

import java.io.OutputStream;

/** One running transcode process (real FFmpeg in production, a fake in tests — see LivestreamProcessLauncher). */
public interface LivestreamProcessHandle {

	/** Encoded chunks from the browser are written here — this is FFmpeg's `pipe:0` input. */
	OutputStream stdin();

	/** Closes stdin (lets FFmpeg flush and exit cleanly) and force-terminates if it doesn't. */
	void stop();
}
