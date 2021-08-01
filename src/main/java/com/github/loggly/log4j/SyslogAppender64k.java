package com.github.loggly.log4j;

import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

import javax.net.SocketFactory;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.SyslogQuietWriter;
import org.apache.log4j.spi.LoggingEvent;

import com.github.loggly.log4j.helpers.SyslogTcpWriter64k;
import com.github.loggly.log4j.helpers.SyslogUdpWriter64k;

import de.larssh.utils.Collectors;
import de.larssh.utils.Finals;
import de.larssh.utils.collection.Maps;
import de.larssh.utils.text.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Use SyslogAppender64k to send log messages upto 64K to a remote syslog
 * daemon.
 */
@SuppressWarnings("PMD.GodClass")
public class SyslogAppender64k extends AppenderSkeleton {
	// The following constants are extracted from a syslog.h file
	// copyrighted by the Regents of the University of California
	// I hope nobody at Berkley gets offended.
	/** Kernel messages */
	public static final int LOG_KERN = Finals.constant(0);

	/** Random user-level messages */
	public static final int LOG_USER = Finals.constant(1 << 3);

	/** Mail system */
	public static final int LOG_MAIL = Finals.constant(2 << 3);

	/** System daemons */
	public static final int LOG_DAEMON = Finals.constant(3 << 3);

	/** security/authorization messages */
	public static final int LOG_AUTH = Finals.constant(4 << 3);

	/** messages generated internally by syslogd */
	public static final int LOG_SYSLOG = Finals.constant(5 << 3);

	/** line printer subsystem */
	public static final int LOG_LPR = Finals.constant(6 << 3);

	/** network news subsystem */
	public static final int LOG_NEWS = Finals.constant(7 << 3);

	/** UUCP subsystem */
	public static final int LOG_UUCP = Finals.constant(8 << 3);

	/** clock daemon */
	public static final int LOG_CRON = Finals.constant(9 << 3);

	/** security/authorization messages (private) */
	public static final int LOG_AUTHPRIV = Finals.constant(10 << 3);

	/** ftp daemon */
	public static final int LOG_FTP = Finals.constant(11 << 3);

	// other codes through 15 reserved for system use
	/** reserved for local use */
	public static final int LOG_LOCAL0 = Finals.constant(16 << 3);

	/** reserved for local use */
	public static final int LOG_LOCAL1 = Finals.constant(17 << 3);

	/** reserved for local use */
	public static final int LOG_LOCAL2 = Finals.constant(18 << 3);

	/** reserved for local use */
	public static final int LOG_LOCAL3 = Finals.constant(19 << 3);

	/** reserved for local use */
	public static final int LOG_LOCAL4 = Finals.constant(20 << 3);

	/** reserved for local use */
	public static final int LOG_LOCAL5 = Finals.constant(21 << 3);

	/** reserved for local use */
	public static final int LOG_LOCAL6 = Finals.constant(22 << 3);

	/** reserved for local use */
	public static final int LOG_LOCAL7 = Finals.constant(23 << 3);

	protected static final int SYSLOG_HOST_OI = Finals.constant(0);

	protected static final int FACILITY_OI = Finals.constant(1);

	/**
	 * Max lengths in bytes of a message. Per RFC 5424, size limits are dictated by
	 * the syslog transport mapping in use. But, the practical upper limit of UDP
	 * over IPV4 is 65507 (65535 − 8 byte UDP header − 20 byte IP header).
	 */
	protected static final int LOWER_MAX_MSG_LENGTH = Finals.constant(480);

	protected static final int UPPER_MAX_MSG_LENGTH = Finals.constant(65507);

	private static final String PROTOCOL_TCP = "tcp";

	private static final String PROTOCOL_UDP = "udp";

	private static final String DEFAULT_PROTOCOL = PROTOCOL_UDP;

	private static final String ELLIPSIS = "...";

	private static final String TAB = "    ";

	/**
	 * Date format used if header = true.
	 */
	private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT
			= ThreadLocal.withInitial(() -> new SimpleDateFormat("MMM dd HH:mm:ss ", Locale.ENGLISH));

	/**
	 * Maps integer values to the corresponding syslog facility name.
	 */
	private static final Map<Integer, String> FACILITY_NAMES = Maps.<Integer, String>builder()
			.put(LOG_KERN, "kern")
			.put(LOG_USER, "user")
			.put(LOG_MAIL, "mail")
			.put(LOG_DAEMON, "daemon")
			.put(LOG_AUTH, "auth")
			.put(LOG_SYSLOG, "syslog")
			.put(LOG_LPR, "lpr")
			.put(LOG_NEWS, "news")
			.put(LOG_UUCP, "uucp")
			.put(LOG_CRON, "cron")
			.put(LOG_AUTHPRIV, "authpriv")
			.put(LOG_FTP, "ftp")
			.put(LOG_LOCAL0, "local0")
			.put(LOG_LOCAL1, "local1")
			.put(LOG_LOCAL2, "local2")
			.put(LOG_LOCAL3, "local3")
			.put(LOG_LOCAL4, "local4")
			.put(LOG_LOCAL5, "local5")
			.put(LOG_LOCAL6, "local6")
			.put(LOG_LOCAL7, "local7")
			.unmodifiable();

	/**
	 * Maps the names syslog facility to the corresponding integer value. The
	 * mapping is case-insensitive.
	 */
	private static final Map<String, Integer> FACILITY_VALUES = FACILITY_NAMES.entrySet()
			.stream()
			.collect(Collectors.toMap(Entry::getValue, //
					Entry::getKey,
					() -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

	/**
	 * Returns the specified syslog facility as a lower-case String, e.g. "kern",
	 * "user", etc.
	 */
	@SuppressFBWarnings(value = "OPM_OVERLY_PERMISSIVE_METHOD", justification = "Public API")
	public static String getFacilityString(final int syslogFacility) {
		return FACILITY_NAMES.get(syslogFacility);
	}

	/**
	 * Returns the integer value corresponding to the named syslog facility, or -1
	 * if it couldn't be recognized.
	 *
	 * @param facilityName one of the strings KERN, USER, MAIL, DAEMON, AUTH,
	 *                     SYSLOG, LPR, NEWS, UUCP, CRON, AUTHPRIV, FTP, LOCAL0,
	 *                     LOCAL1, LOCAL2, LOCAL3, LOCAL4, LOCAL5, LOCAL6, LOCAL7.
	 *                     The matching is case-insensitive.
	 * @since 1.1
	 */
	@SuppressFBWarnings(value = "OPM_OVERLY_PERMISSIVE_METHOD", justification = "Public API")
	public static int getFacility(final String facilityName) {
		return Optional.ofNullable(facilityName).map(String::trim).map(FACILITY_VALUES::get).orElse(-1);
	}

	private final Object lock = new Object();

	private Charset charset = StandardCharsets.UTF_8;

	// Have LOG_USER as default
	private int syslogFacility = LOG_USER;

	private String facilityString;

	private boolean facilityPrinting = false;

	private Optional<SyslogQuietWriter> syslogQuietWriter = Optional.empty();

	private String syslogHost;

	private String protocol = DEFAULT_PROTOCOL;

	private Optional<SocketFactory> tcpSocketFactory = Optional.empty();

	/**
	 * Max length in bytes of a message.
	 */
	private int maxMessageLength = UPPER_MAX_MSG_LENGTH;

	/**
	 * If true, the appender will generate the HEADER (timestamp and host name) part
	 * of the syslog packet.
	 *
	 * @since 1.2.15
	 */
	private boolean header = false;

	/**
	 * Host name used to identify messages from this appender.
	 *
	 * @since 1.2.15
	 */
	private String localHostname;

	/**
	 * Set to true after the header of the layout has been sent or if it has none.
	 */
	private boolean layoutHeaderChecked = false;

	public SyslogAppender64k() {
		this.initSyslogFacilityStr();
	}

	@SuppressFBWarnings(value = "OPM_OVERLY_PERMISSIVE_METHOD", justification = "Public API")
	public SyslogAppender64k(final Layout layout, final int syslogFacility) {
		this.layout = layout;
		this.syslogFacility = syslogFacility;
		this.initSyslogFacilityStr();
	}

	public SyslogAppender64k(final Layout layout, final String syslogHost, final int syslogFacility) {
		this(layout, syslogFacility);
		setSyslogHost(syslogHost);
	}

	/**
	 * Release any resources held by this SyslogAppender64k.
	 *
	 * @since 0.8.4
	 */
	@Override
	public void close() {
		closed = true;

		synchronized (lock) {
			syslogQuietWriter.ifPresent(syslogWriter -> {
				try (Writer writerToClose = syslogWriter) {
					if (layoutHeaderChecked && layout != null && layout.getFooter() != null) {
						sendLayoutMessage(layout.getFooter());
					}
				} catch (@SuppressWarnings("unused") final IOException ignored) {
					// ignore
				} finally {
					syslogQuietWriter = Optional.empty();
				}
			});
		}
	}

	@SuppressWarnings("PMD.GuardLogStatement")
	private void initSyslogFacilityStr() {
		facilityString = getFacilityString(this.syslogFacility);

		if (facilityString == null) {
			LogLog.warn("\"" + syslogFacility + "\" is an unknown syslog facility. Defaulting to \"USER\".");
			this.syslogFacility = LOG_USER;
			facilityString = "user:";
		} else {
			facilityString += ":";
		}
	}

	@Override
	@SuppressWarnings({ "PMD.CloseResource", "PMD.GuardLogStatement" })
	public void append(final LoggingEvent event) {
		if (!isAsSevereAsThreshold(event.getLevel())) {
			return;
		}

		synchronized (lock) {
			if (!syslogQuietWriter.isPresent()) {
				errorHandler.error("No syslog host is set for SyslogAppender named \"" + this.name + "\".");
				return;
			}
			final SyslogQuietWriter syslogWriter = syslogQuietWriter.get();

			if (!layoutHeaderChecked) {
				if (layout != null && layout.getHeader() != null) {
					sendLayoutMessage(layout.getHeader());
				}
				layoutHeaderChecked = true;
			}

			final String packetHeader = getPacketHeader(event.timeStamp);
			final String message = layout == null ? String.valueOf(event.getMessage()) : layout.format(event);
			final String packet = createPacket(packetHeader, message);

			syslogWriter.setLevel(event.getLevel().getSyslogEquivalent());
			sendPackets(packetHeader, packet);

			if (layout == null || layout.ignoresThrowable()) {
				sendThrowable(packetHeader, event);
			}

			syslogWriter.flush();
		}
	}

	/**
	 * This method returns immediately as options are activated when they are set.
	 */
	@Override
	public void activateOptions() {
		if (header) {
			// Initialize local host name
			getLocalHostname();
		}
		if (layout != null && layout.getHeader() != null) {
			synchronized (lock) {
				sendLayoutMessage(layout.getHeader());
			}
		}
		layoutHeaderChecked = true;
	}

	/**
	 * The SyslogAppender64k requires a layout. Hence, this method returns
	 * <code>true</code>.
	 *
	 * @since 0.8.4
	 */
	@Override
	public boolean requiresLayout() {
		return true;
	}

	private String createPacket(final String packetHeader, final String message) {
		if (!facilityPrinting && packetHeader.isEmpty()) {
			return message;
		}

		final StringBuilder builder = new StringBuilder(packetHeader);
		if (facilityPrinting) {
			builder.append(facilityString);
		}
		builder.append(message);
		return builder.toString();
	}

	@SuppressWarnings("PMD.CloseResource")
	private void createSyslogWriter() {
		syslogQuietWriter.ifPresent(syslogWriter -> {
			try {
				syslogWriter.close();
			} catch (@SuppressWarnings("unused") final IOException ignored) {
				// ignore
			}
		});

		switch (protocol) {
		case PROTOCOL_UDP:
			this.syslogQuietWriter = Optional.of(
					new SyslogQuietWriter(new SyslogUdpWriter64k(syslogHost, charset), syslogFacility, errorHandler));
			break;
		case PROTOCOL_TCP:
			this.syslogQuietWriter
					= Optional.of(new SyslogQuietWriter(new SyslogTcpWriter64k(syslogHost, charset, tcpSocketFactory),
							syslogFacility,
							errorHandler));
			break;
		default:
			throw new IllegalArgumentException(String.format("Unexpected protocol: %s", protocol));
		}
	}

	/**
	 * Returns the value of the <b>SyslogHost</b> option.
	 */
	public String getSyslogHost() {
		return syslogHost;
	}

	/**
	 * The <b>SyslogHost</b> option is the name of the the syslog host where log
	 * output should go. A non-default port can be specified by appending a colon
	 * and port number to a host name, an IPv4 address or an IPv6 address enclosed
	 * in square brackets. <b>WARNING</b> If the SyslogHost is not set, then this
	 * appender will fail.
	 */
	public final void setSyslogHost(final String syslogHost) {
		this.syslogHost = syslogHost;
		createSyslogWriter();
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(final String protocol) {
		final String protocolToSet = protocol == null ? DEFAULT_PROTOCOL : Strings.toNeutralLowerCase(protocol);
		switch (protocolToSet) {
		case PROTOCOL_UDP:
		case PROTOCOL_TCP:
			this.protocol = protocol;
			break;
		default:
			throw new IllegalArgumentException(String.format("Invalid protocol: %s", protocol));
		}
		createSyslogWriter();
	}

	public Charset getCharset() {
		return charset;
	}

	public void setCharset(final Charset charset) {
		this.charset = charset;
	}

	/**
	 * Set the syslog facility. This is the <b>Facility</b> option.
	 *
	 * <p>
	 * The <code>facilityName</code> parameter must be one of the strings KERN,
	 * USER, MAIL, DAEMON, AUTH, SYSLOG, LPR, NEWS, UUCP, CRON, AUTHPRIV, FTP,
	 * LOCAL0, LOCAL1, LOCAL2, LOCAL3, LOCAL4, LOCAL5, LOCAL6, LOCAL7. Case is
	 * unimportant.
	 *
	 * @since 0.8.1
	 */
	@SuppressWarnings("PMD.GuardLogStatement")
	public void setFacility(final String facilityName) {
		if (facilityName == null) {
			return;
		}

		syslogFacility = getFacility(facilityName);
		if (syslogFacility == -1) {
			LogLog.warn("[" + facilityName + "] is an unknown syslog facility. Defaulting to [USER].");
			syslogFacility = LOG_USER;
		}

		this.initSyslogFacilityStr();

		// If there is already a syslogQuietWriter, make it use the new facility.
		syslogQuietWriter.ifPresent(syslogWriter -> syslogWriter.setSyslogFacility(this.syslogFacility));
	}

	/**
	 * Returns the value of the <b>Facility</b> option.
	 */
	@SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
	@SuppressFBWarnings(value = "MOM_MISLEADING_OVERLOAD_MODEL", justification = "Existing model")
	public String getFacility() {
		return getFacilityString(syslogFacility);
	}

	/**
	 * If the <b>FacilityPrinting</b> option is set to true, the printed message
	 * will include the facility name of the application. It is <em>false</em> by
	 * default.
	 */
	public void setFacilityPrinting(final boolean facilityPrinting) {
		this.facilityPrinting = facilityPrinting;
	}

	/**
	 * Returns the value of the <b>FacilityPrinting</b> option.
	 */
	@SuppressWarnings("PMD.BooleanGetMethodName")
	public boolean getFacilityPrinting() {
		return facilityPrinting;
	}

	/**
	 * If true, the appender will generate the HEADER part (that is, timestamp and
	 * host name) of the syslog packet. Default value is false for compatibility
	 * with existing behavior, however should be true unless there is a specific
	 * justification.
	 *
	 * @since 1.2.15
	 */
	@SuppressWarnings("PMD.BooleanGetMethodName")
	public final boolean getHeader() {
		return header;
	}

	/**
	 * Returns whether the appender produces the HEADER part (that is, timestamp and
	 * host name) of the syslog packet.
	 *
	 * @since 1.2.15
	 */
	public final void setHeader(final boolean val) {
		header = val;
	}

	/**
	 * Returns the max message length in bytes.
	 *
	 * @return The max message length in bytes.
	 */
	public int getMaxMessageLength() {
		return maxMessageLength;
	}

	/**
	 * Sets the max message length in bytes.
	 *
	 * @param maxMessageLength The max message length in bytes.
	 */
	@SuppressWarnings("PMD.GuardLogStatement")
	public void setMaxMessageLength(final int maxMessageLength) {
		if (maxMessageLength >= LOWER_MAX_MSG_LENGTH && maxMessageLength <= UPPER_MAX_MSG_LENGTH) {
			this.maxMessageLength = maxMessageLength;
		} else {
			this.maxMessageLength = UPPER_MAX_MSG_LENGTH;
			LogLog.warn(
					maxMessageLength + " is an invalid message length. Defaulting to " + UPPER_MAX_MSG_LENGTH + ".");
		}
	}

	public Optional<SocketFactory> getTcpSocketFactory() {
		return tcpSocketFactory;
	}

	public void setTcpSocketFactory(final SocketFactory tcpSocketFactory) {
		this.tcpSocketFactory = Optional.ofNullable(tcpSocketFactory);
	}

	/**
	 * Get the host name used to identify this appender.
	 *
	 * @return local host name
	 * @since 1.2.15
	 */
	@SuppressFBWarnings(value = "MDM_INETADDRESS_GETLOCALHOST",
			justification = "Calling InetAddress.getLocalHost() by intention.")
	private String getLocalHostname() {
		if (localHostname == null) {
			try {
				localHostname = InetAddress.getLocalHost().getHostName();
			} catch (@SuppressWarnings("unused") final UnknownHostException ignore) {
				localHostname = "UNKNOWN_HOST";
			}
		}
		return localHostname;
	}

	/**
	 * Gets HEADER portion of packet.
	 *
	 * @param timeStamp number of milliseconds after the standard base time.
	 * @return HEADER portion of packet, will be zero-length string if header is
	 *         false.
	 * @since 1.2.15
	 */
	@SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
	private String getPacketHeader(final long timeStamp) {
		if (!header) {
			return "";
		}

		final StringBuilder builder = new StringBuilder(DATE_FORMAT.get().format(new Date(timeStamp)));
		// RFC 3164 says leading space, not leading zero on days 1-9
		if (builder.charAt(4) == '0') {
			builder.setCharAt(4, ' ');
		}
		builder.append(getLocalHostname());
		builder.append(' ');
		return builder.toString();
	}

	/**
	 * Set header or footer of layout.
	 *
	 * @param msg message body, may not be null.
	 */
	private void sendLayoutMessage(final String message) {
		syslogQuietWriter.ifPresent(syslogWriter -> {
			final String packet = createPacket(getPacketHeader(new Date().getTime()), message);
			syslogWriter.setLevel(6);
			syslogWriter.write(packet);
		});
	}

	@SuppressWarnings({ "checkstyle:SuppressWarnings", "resource" })
	private void sendPackets(final String header, final String packet) {
		final int byteCount = packet.getBytes(charset).length;

		// If packet is less than limit, then write it.
		// Else, write in chunks.
		if (byteCount <= maxMessageLength) {
			syslogQuietWriter.get().write(packet);
		} else {
			final int split = header.length() / 2 + packet.length() / 2;
			sendPackets(header, packet.substring(0, split) + ELLIPSIS);
			sendPackets(header, header + ELLIPSIS + packet.substring(split));
		}
	}

	@SuppressWarnings({ "checkstyle:SuppressWarnings", "resource" })
	private void sendThrowable(final String packetHeader, final LoggingEvent event) {
		final String[] lines = event.getThrowableStrRep();
		if (lines != null) {
			for (final String line : lines) {
				if (line.startsWith("\t")) {
					syslogQuietWriter.get().write(packetHeader + TAB + line.substring(1));
				} else {
					syslogQuietWriter.get().write(packetHeader + line);
				}
			}
		}
	}

	@Override
	public String toString() {
		return new StringBuilder("SyslogAppender64k [charset=") //
				.append(charset)
				.append(", syslogFacility=")
				.append(syslogFacility)
				.append(", facilityString=")
				.append(facilityString)
				.append(", facilityPrinting=")
				.append(facilityPrinting)
				.append(", syslogHost=")
				.append(syslogHost)
				.append(", protocol=")
				.append(protocol)
				.append(", maxMessageLength=")
				.append(maxMessageLength)
				.append(", header=")
				.append(header)
				.append(", localHostname=")
				.append(localHostname)
				.append(", layoutHeaderChecked=")
				.append(layoutHeaderChecked)
				.append(']')
				.toString();
	}
}
