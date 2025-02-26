package com.github.loggly.log4j.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import de.larssh.utils.annotations.PackagePrivate;

@PackagePrivate
class SyslogUdpWriter64kTest {
	@PackagePrivate
	SyslogUdpWriter64kTest() {
		// nothing to initialize
	}

	@Test
	@PackagePrivate
	void createWriterWithPort() throws IOException {
		// given
		try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("localhost", 5514));
				Writer writer = new SyslogUdpWriter64k("localhost:5514", StandardCharsets.UTF_8)) {
			// when
			writer.write("abc");

			// then
			final byte[] buffer = new byte[3];
			final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			socket.receive(packet);
			assertThat(buffer).isEqualTo("abc".getBytes(StandardCharsets.US_ASCII));
		}
	}

	@Test
	@PackagePrivate
	void createWriterWithoutPort() throws IOException {
		// given
		try (SyslogUdpWriter64k writer = new SyslogUdpWriter64k("localhost", StandardCharsets.UTF_8)) {
			// when
			writer.write("abc");

			// then
			assertThat(writer.getSyslogPort()).isEqualTo(SyslogWriter64k.DEFAULT_SYSLOG_PORT);
		}
	}
}
