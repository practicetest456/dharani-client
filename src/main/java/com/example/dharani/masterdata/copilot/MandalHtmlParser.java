package com.example.dharani.masterdata.copilot;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.dharani.masterdata.copilot.MandalMaster.Mandal;

/**
 * Turns the portal's dropdown fragment
 * {@code <option value="632">Akbarpet-Bhoompally|అక్బర్‌పేట-భూంపల్లి</option>} into {@link Mandal}s.
 * The "Please Select" entry (value 0) is skipped.
 */
final class MandalHtmlParser {

	private static final Pattern OPTION = Pattern.compile(
			"<option\\b[^>]*?\\bvalue\\s*=\\s*[\"']?(\\d+)[\"']?[^>]*>(.*?)</option>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos|nbsp);");

	private MandalHtmlParser() {
	}

	static List<Mandal> parse(String html) {
		List<Mandal> mandals = new ArrayList<>();
		if (html == null) {
			return mandals;
		}
		Matcher m = OPTION.matcher(html);
		while (m.find()) {
			int id = Integer.parseInt(m.group(1));
			if (id == 0) {
				continue; // "Please Select"
			}
			String label = unescape(m.group(2)).trim();
			int bar = label.indexOf('|');
			String english = (bar >= 0 ? label.substring(0, bar) : label).trim();
			String telugu = (bar >= 0 ? label.substring(bar + 1) : "").trim();
			if (!english.isEmpty() || !telugu.isEmpty()) {
				mandals.add(new Mandal(id, english, telugu));
			}
		}
		return mandals;
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
