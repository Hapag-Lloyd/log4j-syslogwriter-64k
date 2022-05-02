package com.github.loggly.log4j.helpers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * SyslogWriter64k is a wrapper around the java.net.DatagramSocket class so that
 * it behaves like a java.io.Writer.
 */
@SuppressFBWarnings(value = "IMC_IMMATURE_CLASS_NO_TOSTRING",
		justification = "Instance fields cannot be stringified nicely, therefore toString does not make that much sense.")
public class SyslogTcpWriter64k extends SyslogWriter64k {
	private final Optional<SocketFactory> socketFactory;

	private final Optional<Duration> socketTimeout;

	private final AtomicReference<Socket> socket = new AtomicReference<>(null);

	private final AtomicReference<BufferedWriter> writer = new AtomicReference<>(null);

	public SyslogTcpWriter64k(final String syslogHost,
			final Charset charset,
			final Optional<SocketFactory> socketFactory,
			final Optional<Duration> socketTimeout) {
		super(syslogHost, charset);

		this.socketFactory = socketFactory;
		this.socketTimeout = socketTimeout;
	}

	@Override
	@SuppressWarnings({ "checkstyle:SuppressWarnings", "PMD.CloseResource", "resource" })
	public void flush() throws IOException {
		final BufferedWriter writerToFlush = writer.get();
		if (writerToFlush != null) {
			closeOnIOException(writerToFlush::flush);
		}
	}

	@SuppressWarnings({ "checkstyle:SuppressWarnings", "resource" })
	@SuppressFBWarnings(value = { "OI_OPTIONAL_ISSUES_USES_IMMEDIATE_EXECUTION", "UNENCRYPTED_SOCKET" },
			justification = "false-positive, as '0' is constant; Offering both: insecure TCP and secure TCP via custom SocketFactory")
	private BufferedWriter getWriter() throws IOException {
		synchronized (lock) {
			if (writer.get() == null) {
				final Socket socketToSet = socketFactory.isPresent()
						? socketFactory.get().createSocket(getSyslogHost(), getSyslogPort())
						: new Socket(getSyslogHost(), getSyslogPort());
				socketToSet
						.setSoTimeout(socketTimeout.map(duration -> (int) duration.get(ChronoUnit.MILLIS)).orElse(0));
				socket.set(socketToSet);

				writer.set(new BufferedWriter(new OutputStreamWriter(socketToSet.getOutputStream(), getCharset())));
			}
			return writer.get();
		}
	}

	@Override
	public void write(final String string) throws IOException {
		// compute syslog frame according to: https://tools.ietf.org/html/rfc6587
		final String syslogFrame = String.format("%s %s", string.length(), string);
		closeOnIOException(() -> getWriter().append(syslogFrame));
	}

	@Override
	public void write(final char[] buf, final int off, final int len) throws IOException {
		this.write(new String(buf, off, len));
	}

	@Override
	@SuppressFBWarnings(value = "AFBR_ABNORMAL_FINALLY_BLOCK_RETURN", justification = "Shouldn't matter in this case.")
	public void close() throws IOException {
		synchronized (lock) {
			try (Socket socketToClose = socket.get();
					BufferedWriter writerToClose = writer.get()) {
				// nothing
			} finally {
				writer.set(null);
				socket.set(null);
			}
		}
	}

	private void closeOnIOException(final IORunnable runnable) throws IOException {
		try {
			runnable.run();
		} catch (final IOException e) {
			try {
				close();
			} catch (@SuppressWarnings("unused") final IOException ignored) {
				// ignore because it shall not hide the original exception
			}
			throw e;
		}
	}

	@FunctionalInterface
	private interface IORunnable {
		void run() throws IOException;
	}
}
