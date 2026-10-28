package com.example.dharani.masterdata.claud.master;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.example.dharani.client.DharaniClient;
import com.example.dharani.config.DharaniProperties;

@Service
public class KhataService {

    private static final Logger log = LoggerFactory.getLogger(KhataService.class);
    private static final int MAX_RETRIES = 2;

    private final DharaniClient client;
    private final DharaniProperties props;
    private final ResourceLoader resourceLoader;

    public KhataService(DharaniClient client, DharaniProperties props, ResourceLoader resourceLoader) {
        this.client = client;
        this.props = props;
        this.resourceLoader = resourceLoader;
    }

	/**
	 * Reads all survey numbers, calls the portal in parallel, keeps file order,
	 * saves CSV.
	 */
	public List<KhataResult> fetchAllFromFile() {
		List<String> surveyNos = readSurveyNumbers();
		List<KhataResult> results = fetchSurveyKhataNos(surveyNos, null);
		return results;
	}

	public List<KhataResult> fetchSurveyKhataNos(List<String> surveyNos, String villageId) {
		int total = surveyNos.size();
		long start = System.currentTimeMillis();
		log.info("Starting {} survey numbers with concurrency={}", total, props.concurrency());

		Semaphore permits = new Semaphore(props.concurrency());
		AtomicInteger done = new AtomicInteger();
		List<CompletableFuture<KhataResult>> futures = new ArrayList<>();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (String surveyNo : surveyNos) {
				futures.add(CompletableFuture.supplyAsync(() -> {
					permits.acquireUninterruptibly();
					try {
						return fetchOne(surveyNo, villageId);
					} finally {
						permits.release();
						int n = done.incrementAndGet();
						if (n % 100 == 0 || n == total) {
							log.info("Progress {}/{}", n, total);
						}
					}
				}, executor));
			}
		}

		List<KhataResult> results = futures.stream().map(CompletableFuture::join).toList();
		log.info("Finished {} in {} sec", total, (System.currentTimeMillis() - start) / 1000);
		// writeCsv(results);
		return results;
	}

	public KhataResult fetchOne(String surveyNo, String villageId) {
        Exception last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String html = client.fetchOptionsHtml(surveyNo, villageId);
                return new KhataResult(surveyNo, extractOptionValues(html), null);
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep(500L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.warn("Failed for surveyNo={}: {}", surveyNo, last == null ? "?" : last.getMessage());
        return new KhataResult(surveyNo, List.of(), last == null ? "unknown" : last.getMessage());
    }

    /** Takes all <option value="..."> values, skipping empty and "0" (-- Select --). */
    private List<String> extractOptionValues(String html) {
        List<String> values = new ArrayList<>();
        if (html == null) {
            return values;
        }
        for (Element option : Jsoup.parse(html).select("option")) {
            String value = option.attr("value").trim();
            if (!value.isEmpty() && !value.equals("0")) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> readSurveyNumbers() {
        Resource resource = resourceLoader.getResource(props.surveyFile());
        List<String> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replace("\uFEFF", "").trim();
                if (!line.isEmpty()) {
                    list.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read survey file: " + props.surveyFile(), e);
        }
        return list;
    }

    private void writeCsv(List<KhataResult> results) {
        StringBuilder sb = new StringBuilder("\uFEFFsurveyNo,khataNos,error\n");
        for (KhataResult r : results) {
            sb.append(csv(r.surveyNo())).append(',')
              .append(csv(String.join("|", r.khataNos()))).append(',')
              .append(csv(r.error() == null ? "" : r.error())).append('\n');
        }
        try {
            Path path = Path.of(props.outputFile()).toAbsolutePath();
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            log.info("Saved results to {}", path);
        } catch (IOException e) {
            log.warn("Could not write CSV: {}", e.getMessage());
        }
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}