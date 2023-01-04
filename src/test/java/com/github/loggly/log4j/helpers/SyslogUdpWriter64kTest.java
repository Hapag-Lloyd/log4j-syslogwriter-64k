package com.github.loggly.log4j.helpers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class SyslogUdpWriter64kTest {
	public SyslogUdpWriter64kTest() {
		// nothing to initialize
	}

	@Test
	public void createWriterWithPort() throws IOException {
		// given
		try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("localhost", 5514));
				Writer writer = new SyslogUdpWriter64k("localhost:5514", StandardCharsets.UTF_8)) {
			// when
			writer.write("abc");

			// then
			final byte[] buffer = new byte[3];
			final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			socket.receive(packet);
			assertArrayEquals("abc".getBytes(StandardCharsets.US_ASCII), buffer);
		}
	}

	@Test
	public void createWriterWithoutPort() throws IOException {
		// given
		try (SyslogUdpWriter64k writer = new SyslogUdpWriter64k("localhost", StandardCharsets.UTF_8)) {
			// when
			writer.write("abc");

			// then
			assertEquals(SyslogWriter64k.DEFAULT_SYSLOG_PORT, writer.getSyslogPort());
		}
	}
}
