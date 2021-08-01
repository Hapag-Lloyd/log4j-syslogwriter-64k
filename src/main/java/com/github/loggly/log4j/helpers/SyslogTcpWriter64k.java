package com.github.loggly.log4j.helpers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Optional;

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

	private volatile Optional<Socket> socket = Optional.empty();

	private volatile Optional<BufferedWriter> writer = Optional.empty();

	public SyslogTcpWriter64k(final String syslogHost,
			final Charset charset,
			final Optional<SocketFactory> socketFactory) {
		super(syslogHost, charset);

		this.socketFactory = socketFactory;
	}

	@Override
	public void flush() throws IOException {
		final Optional<BufferedWriter> writerToFlush = writer;
		if (writerToFlush.isPresent()) {
			closeOnIOException(() -> writerToFlush.get().flush());
		}
	}

	@SuppressWarnings({ "checkstyle:SuppressWarnings", "resource" })
	@SuppressFBWarnings(value = "UNENCRYPTED_SOCKET",
			justification = "Offering both: insecure TCP and TCP via custom SocketFactory")
	private BufferedWriter getWriter() throws IOException {
		synchronized (lock) {
			if (!writer.isPresent()) {
				final Socket socketToSet = socketFactory.isPresent()
						? socketFactory.get().createSocket(getSyslogHost(), getSyslogPort())
						: new Socket(getSyslogHost(), getSyslogPort());
				socket = Optional.of(socketToSet);

				writer = Optional
						.of(new BufferedWriter(new OutputStreamWriter(socketToSet.getOutputStream(), getCharset())));
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
			} catch (@SuppressWarnings("unused") final IOException ignored) {
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
