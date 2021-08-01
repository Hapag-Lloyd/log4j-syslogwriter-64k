package com.github.loggly.log4j.helpers;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.Charset;

import org.apache.log4j.helpers.LogLog;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * SyslogWriter64k is a wrapper around the java.net.DatagramSocket class so that
 * it behaves like a java.io.Writer.
 */
@SuppressFBWarnings(value = "IMC_IMMATURE_CLASS_NO_TOSTRING",
		justification = "Instance fields cannot be stringified nicely, therefore toString does not make that much sense.")
public class SyslogUdpWriter64k extends SyslogWriter64k {
	private final DatagramSocket socket;

	@SuppressWarnings({ "PMD.CloseResource", "PMD.GuardLogStatement" })
	public SyslogUdpWriter64k(final String syslogHost, final Charset charset) {
		super(syslogHost, charset);

		DatagramSocket udpSocket = null;
		try {
			udpSocket = new DatagramSocket();
		} catch (final SocketException e) {
			LogLog.error("Could not instantiate DatagramSocket to " + syslogHost + ". All logging will FAIL.", e);
		}
		this.socket = udpSocket;
	}

	@Override
	public void write(final char[] buf, final int off, final int len) throws IOException {
		this.write(new String(buf, off, len));
	}

	@Override
	public void write(final String string) throws IOException {
		final byte[] bytes = string.getBytes(getCharset());
		final DatagramPacket packet = new DatagramPacket(bytes, bytes.length, getSyslogHost(), getSyslogPort());

		if (this.socket != null) {
			socket.send(packet);
		}
	}

	@Override
	public void flush() {
		// nothing to flush
	}

	@Override
	@SuppressWarnings("PMD.CloseResource")
	public void close() {
		socket.close();
	}
}
