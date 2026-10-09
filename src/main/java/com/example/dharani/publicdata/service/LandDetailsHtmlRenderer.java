package com.example.dharani.publicdata.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;

import com.example.dharani.publicdata.model.LandDetailsResult;

@Component
public class LandDetailsHtmlRenderer {

    private static final String CSS = """
            :root { --green:#00733c; --green-dark:#005a2f; --green-soft:#e6f4ec; --ink:#16241d;
                    --muted:#64766c; --line:#e2ece6; --bg1:#e8f5ff; --bg2:#d6efe2; }
            * { box-sizing:border-box; }
            body { margin:0; padding:24px 16px 48px; color:var(--ink);
                   background:linear-gradient(160deg,var(--bg1),var(--bg2)) fixed;
                   font-family:"Open Sans","Segoe UI","Noto Sans","Noto Sans Telugu","Nirmala UI","Gautami",Arial,sans-serif; }
            .page { max-width:1080px; margin:0 auto; }

            .header { background:linear-gradient(135deg,var(--green),var(--green-dark)); color:#fff;
                      border-radius:18px; padding:22px 28px; display:flex; flex-wrap:wrap;
                      gap:12px; align-items:center; justify-content:space-between;
                      box-shadow:0 10px 28px -10px rgba(0,115,60,.55); }
            .header h1 { margin:0; font-size:24px; font-weight:700; letter-spacing:.2px; }
            .header p { margin:4px 0 0; font-size:13px; opacity:.85; }
            .btn { background:#fff; color:var(--green-dark); border:0; border-radius:999px; padding:10px 20px;
                   font-weight:700; font-size:14px; cursor:pointer; box-shadow:0 2px 8px rgba(0,0,0,.15); }
            .btn:hover { background:var(--green-soft); }

            .owner { margin:22px 4px 6px; }
            .owner .small { font-size:12px; text-transform:uppercase; letter-spacing:.08em; color:var(--muted); font-weight:700; }
            .owner .name { font-size:26px; font-weight:700; margin-top:4px; line-height:1.35; }

            .stats { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:14px; margin:16px 0 8px; }
            .stat { background:#fff; border-radius:16px; padding:16px 18px; border-top:4px solid var(--green);
                    box-shadow:0 6px 18px -8px rgba(0,0,0,.2); }
            .stat .k { font-size:12px; color:var(--muted); font-weight:700; text-transform:uppercase; letter-spacing:.06em; }
            .stat .v { font-size:24px; font-weight:700; margin-top:6px; color:var(--green-dark); word-break:break-word; }

            .section-title { display:flex; align-items:center; gap:10px; margin:30px 4px 12px;
                             font-size:19px; font-weight:700; color:var(--green-dark); }
            .section-title::before { content:""; width:6px; height:22px; border-radius:3px; background:var(--green); }

            .card { background:#fff; border-radius:18px; padding:10px 22px 14px;
                    box-shadow:0 10px 26px -12px rgba(0,0,0,.28); }
            .grid { display:grid; grid-template-columns:repeat(3,1fr); column-gap:28px; }
            .cell { padding:15px 2px; border-bottom:1px solid var(--line); min-width:0; }
            .cell.full { grid-column:1 / -1; }
            .label { font-size:12px; text-transform:uppercase; letter-spacing:.05em; color:var(--muted); font-weight:700; margin-bottom:5px; }
            .value { font-size:17px; font-weight:600; line-height:1.5; word-break:break-word; }

            .chip { display:inline-block; padding:4px 12px; border-radius:999px; font-size:14px; font-weight:700; }
            .chip.ok { background:#d9f2e3; color:#0b5f33; }
            .chip.warn { background:#fff0c9; color:#8a5a00; }
            .chip.info { background:#dcecff; color:#1a4f8a; }

            .notes { margin-top:28px; background:rgba(255,255,255,.7); border-radius:14px; padding:14px 18px;
                     font-size:13px; color:#33433a; }
            .notes p { margin:6px 0; }
            .notes a { color:var(--green-dark); }
            .copy { text-align:center; font-size:13px; color:#33433a; margin-top:26px; }
            .empty { padding:22px; background:#fff3cd; border-radius:14px; font-size:16px; }

            @media (max-width:820px) { .grid { grid-template-columns:repeat(2,1fr); } }
            @media (max-width:560px) {
              .grid { grid-template-columns:1fr; }
              .header h1 { font-size:20px; }
              .owner .name { font-size:21px; }
            }
            @media print {
              body { background:#fff !important; padding:0; -webkit-print-color-adjust:exact; print-color-adjust:exact; }
              .btn { display:none; }
              .card, .stat { box-shadow:none; border:1px solid #cfdcd4; break-inside:avoid; }
              .header { box-shadow:none; }
            }
            """;

    public String render(LandDetailsResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
          .append("<title>Land Details</title><style>").append(CSS).append("</style></head><body><div class=\"page\">");

        sb.append("<div class=\"header\"><div><h1>Land Details</h1>")
          .append("<p>Bhu Bharati &middot; Record of Rights</p></div>")
          .append("<button class=\"btn\" onclick=\"window.print()\">Print / Save as PDF</button></div>");

        if (r.isEmpty()) {
            sb.append("<p class=\"empty\">No land details found in the response. ")
              .append("The session or captcha may have expired.</p>");
        } else {
            Map<String, String> p = r.pattadarDetails();
            Map<String, String> l = r.landDetails();

            sb.append("<div class=\"owner\"><div class=\"small\">Pattadar</div><div class=\"name\">")
              .append(esc(find(p, "pattadar"))).append("</div></div>");

            sb.append("<div class=\"stats\">")
              .append(stat("Khata Number", find(p, "khata number")))
              .append(stat("Survey No.", find(l, "survey no")))
              .append(stat("Extent (Ac. Gts)", find(l, "total extent")))
              .append(stat("Market Value", rupees(find(l, "market value"))))
              .append("</div>");

            appendSection(sb, "Pattadar Details", p);
            appendSection(sb, "Land Details", l);

            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            sb.append("<div class=\"notes\">")
              .append("<p>Disclaimer - The information provided online is updated, and no physical visit is required.</p>")
              .append("<p><a href=\"https://cersai.org.in/CERSAI/asstsrch.prg\" target=\"_blank\">")
              .append("Click here (https://cersai.org.in/CERSAI/asstsrch.prg)</a> for CERSAI for Asset Based Search.</p>")
              .append("<p>Note : Fetched on&nbsp;").append(today).append("</p></div>");
        }

        sb.append("<div class=\"copy\">Copyright &copy; All rights reserved with Chief Commissioner of ")
          .append("Land Administration, Govt. of Telangana.</div></div></body></html>");
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, Map<String, String> fields) {
        sb.append("<div class=\"section-title\">").append(esc(title)).append("</div>");
        sb.append("<div class=\"card\"><div class=\"grid\">");
        fields.forEach((label, value) -> {
            String lower = label.toLowerCase();
            boolean full = lower.contains("transaction status");
            sb.append("<div class=\"cell").append(full ? " full" : "").append("\">")
              .append("<div class=\"label\">").append(esc(label)).append("</div>")
              .append("<div class=\"value\">").append(valueHtml(lower, value)).append("</div></div>");
        });
        sb.append("</div></div>");
    }

    /** Status-like fields get a coloured badge; everything else is plain text. */
    private String valueHtml(String lowerLabel, String value) {
        String v = value == null || value.isBlank() ? "-" : value;
        String lv = v.toLowerCase();
        if (lowerLabel.contains("ekyc")) {
            String cls = lv.contains("completed") ? "ok" : "warn";
            return "<span class=\"chip " + cls + "\">" + esc(v) + "</span>";
        }
        if (lowerLabel.equals("land status")) {
            String cls = lv.contains("signed") ? "ok" : "info";
            return "<span class=\"chip " + cls + "\">" + esc(v) + "</span>";
        }
        return esc(v);
    }

    private String stat(String key, String value) {
        return "<div class=\"stat\"><div class=\"k\">" + esc(key) + "</div><div class=\"v\">"
                + esc(value) + "</div></div>";
    }

    /** First map value whose label contains the given text (case-insensitive), else "-". */
    private String find(Map<String, String> map, String labelPart) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().toLowerCase().contains(labelPart.toLowerCase())) {
                String v = e.getValue();
                return (v == null || v.isBlank()) ? "-" : v;
            }
        }
        return "-";
    }

    /** 675000.00 -> ₹6,75,000.00 (Indian digit grouping); falls back to the raw text. */
    private String rupees(String raw) {
        try {
            NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("en", "IN"));
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return "\u20B9" + nf.format(new BigDecimal(raw.trim()));
        } catch (Exception e) {
            return raw;
        }
    }

    private static String esc(String s) {
        return Entities.escape(s == null ? "" : s);
    }
}