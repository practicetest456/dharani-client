package com.example.dharani.masterdata.claud.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the dropdown fragments the portal answers with, e.g.
 * {@code <option value="632">Akbarpet-Bhoompally|అక్బర్‌పేట-భూంపల్లి</option>}.
 * The "Please select" entry (value 0, or no value) is skipped.
 */
public final class HtmlOptions {

	/** One dropdown entry: the {@code value} the portal expects back, and the text shown to the user. */
	public record Option(String value, String label) {
	}

	private static final Pattern OPTION = Pattern.compile("<option\\b([^>]*)>(.*?)</option>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern VALUE = Pattern.compile("\\bvalue\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos|nbsp);");

	private HtmlOptions() {
	}

	public static List<Option> parse(String html) {
		List<Option> options = new ArrayList<>();
		if (html == null) {
			return options;
		}
		Matcher m = OPTION.matcher(html);
		while (m.find()) {
			String label = unescape(m.group(2)).trim();
			Matcher v = VALUE.matcher(m.group(1));
			String value = label; // a browser uses the text when there is no value attribute
			if (v.find()) {
				value = unescape(v.group(1) != null ? v.group(1) : v.group(2) != null ? v.group(2) : v.group(3)).trim();
			}
			if (value.isEmpty() || value.equals("0")) {
				continue; // "Please select"
			}
			options.add(new Option(value, label));
		}
		return options;
	}

	/** Decodes the few HTML entities that can appear in names (&amp; &#39; ...). */
	static String unescape(String s) {
		if (s.indexOf('&') < 0) {
			return s;
		}
		Matcher m = ENTITY.matcher(s);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String e = m.group(1);
			String replacement = switch (e) {
			case "amp" -> "&";
			case "lt" -> "<";
			case "gt" -> ">";
			case "quot" -> "\"";
			case "apos" -> "'";
			case "nbsp" -> " ";
			default -> new String(Character.toChars(
					e.charAt(1) == 'x' ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1))));
			};
			m.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(out);
		return out.toString();
	}
}
