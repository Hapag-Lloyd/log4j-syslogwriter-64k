package com.github.loggly.log4j.helpers;

import java.io.IOException;
import java.io.Writer;

import org.junit.Test;

public class SyslogWriter64kTest {
	@Test
	public void createWriterWithPort() throws IOException {
		Writer writer = new SyslogWriter64k("localhost:5514");
		writer.write("abc");
		writer.close();
	}
	
	@Test
	public void createWriterWithoutPort() throws IOException {
		Writer writer = new SyslogWriter64k("localhost");
		writer.write("abc");
		writer.close();
	}
}
