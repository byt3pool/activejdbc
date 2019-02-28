package org.javalite.logging;

import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import static org.javalite.common.JsonHelper.escapeControlChars;
import static org.javalite.common.JsonHelper.sanitize;
import org.javalite.common.Util;

@Plugin(name = "JsonLog4j2Layout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class JsonLog4j2Layout extends AbstractStringLayout {

  public static abstract class Builder<B extends Builder<B>> extends AbstractStringLayout.Builder<B> {

    @PluginBuilderAttribute
    private SimpleDateFormat dateFormatPattern;

    public SimpleDateFormat getDateFormatPattern() {
      return dateFormatPattern;
    }

    public B setDateFormatPattern(String dateFormatPattern) {
      try {
        this.dateFormatPattern = new SimpleDateFormat(dateFormatPattern);
      } catch (Exception e) {
        throw new IllegalArgumentException("Incorrect date pattern. "
                + "Ensure to use formats provided in https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html", e);
      }
      return asBuilder();
    }
  }

  protected final ObjectWriter objectWriter;

  protected final SimpleDateFormat dateFormatPattern;

  protected JsonLog4j2Layout(final Configuration config, final ObjectWriter objectWriter, final Charset charset,
                             final Serializer headerSerializer, final Serializer footerSerializer,
                             final SimpleDateFormat dateFormatPattern) {
    super(config, charset, headerSerializer, footerSerializer);
    this.objectWriter = objectWriter;
    this.dateFormatPattern = dateFormatPattern;
  }

  /**
   * Formats a {@link org.apache.logging.log4j.core.LogEvent}.
   *
   * @param event The LogEvent.
   * @return The XML representation of the LogEvent.
   */
  @Override
  public String toSerializable(final LogEvent event) {
    String loggerName = event.getLoggerName();
    String level = event.getLevel().toString();
    String message = event.getMessage().toString().trim();
    if (!message.startsWith("{") && !message.startsWith("[")) {
      message = "\"" + message + "\"";
    }
    String threadName = event.getThreadName();
    Date timeStamp = new Date(event.getTimeMillis());
    String context = Context.toJSON();

    Throwable throwable = event.getThrown();

    String exception = "";
    if (throwable != null) {
      exception = ",\"exception\":{\"message\":\"";
      String exceptionMessage = throwable.getMessage() != null ? throwable.getMessage() : "";
      //need to be careful here, sanitizing, since the message may already contain a chunk of JSON, so escaping or cleaning double quotes is not prudent:)
      exception += sanitize(exceptionMessage, false, '\n', '\t', '\r') + "\",\"stacktrace\":\"" + escapeControlChars(Util.getStackTraceString(throwable)) + "\"}";
    }

    String contextJson = context != null ? ",\"context\":" + context : "";

    String timestampString = this.dateFormatPattern == null ? timeStamp.toString() : dateFormatPattern.format(timeStamp);

    return "{\"level\":\"" + level + "\",\"timestamp\":\"" + timestampString
            + "\",\"thread\":\"" + threadName + "\",\"logger\":\"" + loggerName + "\",\"message\":"
            + message + contextJson + exception + "}" + System.getProperty("line.separator");
  }
  
  public void toSerializable(final LogEvent event, final Writer writer) throws IOException {
    objectWriter.writeValue(writer, event);
    markEvent();
  }
}
