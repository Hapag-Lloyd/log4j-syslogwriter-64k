package com.github.loggly.log4j.helpers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * SyslogWriter64k is a wrapper around the java.net.DatagramSocket class so that
 * it behaves like a java.io.Writer.
 */
public class SyslogTcpWriter64k extends SyslogWriter64k {
	private volatile Optional<Socket> socket = Optional.empty();

	private volatile Optional<BufferedWriter> writer = Optional.empty();

	private final Object lock = new Object();

	public SyslogTcpWriter64k(final String syslogHost) {
		this(syslogHost, StandardCharsets.UTF_8);
	}

	public SyslogTcpWriter64k(final String syslogHost, final Charset charset) {
		super(syslogHost, charset);
	}

	@Override
	public void flush() throws IOException {
		final Optional<BufferedWriter> writerToFlush = writer;
		if (writerToFlush.isPresent()) {
			closeOnIOException(() -> writerToFlush.get().flush());
		}
	}

	private BufferedWriter getWriter() throws IOException {
		synchronized (lock) {
			if (!writer.isPresent()) {
				socket = Optional.of(new Socket(getSyslogHost(), getSyslogPort()));
				writer = Optional
						.of(new BufferedWriter(new OutputStreamWriter(socket.get().getOutputStream(), getCharset())));
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
	public void close() throws IOException {
		final Optional<BufferedWriter> writerToClose;
		final Optional<Socket> socketToClose;

		synchronized (lock) {
			writerToClose = writer;
			writer = Optional.empty();

			socketToClose = socket;
			socket = Optional.empty();
		}

		try {
			if (writerToClose.isPresent()) {
				writerToClose.get().close();
			}
		} finally {
			if (socketToClose.isPresent()) {
				socketToClose.get().close();
			}
		}
	}

	private void closeOnIOException(final IORunnable runnable) throws IOException {
		try {
			runnable.run();
		} catch (final IOException e) {
			try {
				close();
			} catch (final IOException ignore) {
				// ignore because it should not hide the original exception
			}
			throw e;
		}
	}

	@FunctionalInterface
	private interface IORunnable {
		void run() throws IOException;
	}
}
