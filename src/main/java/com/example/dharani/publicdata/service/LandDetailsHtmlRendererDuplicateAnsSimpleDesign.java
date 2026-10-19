package com.example.dharani.publicdata.service;

import java.util.Map;

import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;

import com.example.dharani.common.model.Ror1BLandDetails;

/** Simple UI design for land details. */

@Component
public class LandDetailsHtmlRendererDuplicateAnsSimpleDesign {

    public String render(Ror1BLandDetails r) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Land Details</title>
                <style>
                  :root { --green:#00733c; --text:#1d2b24; --muted:#6b7a72; --bg:#f3f6f4; }
                  * { box-sizing:border-box; }
                  body { margin:0; padding:24px; background:var(--bg); color:var(--text);
                         font-family:"Segoe UI","Noto Sans Telugu","Nirmala UI","Gautami",Arial,sans-serif; }
                  .wrap { max-width:1000px; margin:0 auto; }
                  .banner { background:var(--green); color:#fff; font-size:20px; font-weight:600;
                            padding:14px 20px; border-radius:10px 10px 0 0; }
                  h2 { color:var(--green); font-size:19px; margin:26px 0 10px 4px; }
                  .card { background:#fff; border-radius:12px; padding:8px 18px;
                          box-shadow:0 2px 12px rgba(0,0,0,.08); border-left:5px solid var(--green); }
                  .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); }
                  .cell { padding:14px 10px; border-bottom:1px solid #e3ebe6; }
                  .label { font-size:12px; text-transform:uppercase; letter-spacing:.04em;
                           color:var(--muted); margin-bottom:4px; }
                  .value { font-size:16px; font-weight:600; line-height:1.4; word-break:break-word; }
                  .empty { padding:20px; background:#fff3cd; border-radius:10px; }
                  @media print { body { background:#fff; } .card { box-shadow:none; border:1px solid #ccc; } }
                </style>
                </head>
                <body><div class="wrap">
                <div class="banner">Land Details</div>
                """);

        if (r.isEmpty()) {
            sb.append("<p class=\"empty\">No land details found in the response. "
                    + "The session or captcha may have expired.</p>");
        } else {
            appendSection(sb, "Pattadar Details", r.pattadarDetails());
            appendSection(sb, "Land Details", r.landDetails());
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, Map<String, String> fields) {
        sb.append("<h2>").append(esc(title)).append("</h2><div class=\"card\"><div class=\"grid\">");
        fields.forEach((label, value) -> sb
                .append("<div class=\"cell\"><div class=\"label\">").append(esc(label))
                .append("</div><div class=\"value\">").append(esc(value.isBlank() ? "-" : value))
                .append("</div></div>"));
        sb.append("</div></div>");
    }

    private static String esc(String s) {
        return Entities.escape(s);
    }
}