package com.github.loggly.log4j.helpers;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class SyslogUdpWriter64kTest {
	public SyslogUdpWriter64kTest() {
		// nothing to initialize
	}

	@Test
	public void createWriterWithPort() throws IOException {
		final Writer writer = new SyslogUdpWriter64k("localhost:5514", StandardCharsets.UTF_8);
		writer.write("abc");
		writer.close();
	}

	@Test
	public void createWriterWithoutPort() throws IOException {
		final Writer writer = new SyslogUdpWriter64k("localhost", StandardCharsets.UTF_8);
		writer.write("abc");
		writer.close();
	}
}
