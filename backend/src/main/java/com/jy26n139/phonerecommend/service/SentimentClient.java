package com.jy26n139.phonerecommend.service;

import com.jy26n139.phonerecommend.dto.SentimentRequest;
import com.jy26n139.phonerecommend.dto.SentimentResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SentimentClient {
    private final RestClient restClient;

    public SentimentClient(RestClient sentimentRestClient) {
        this.restClient = sentimentRestClient;
    }

    public SentimentResponse analyze(Double rating, String content) {
        try {
            return restClient.post()
                    .uri("/api/sentiment/analyze")
                    .body(new SentimentRequest(rating == null ? 3.0 : rating, content == null ? "" : content))
                    .retrieve()
                    .body(SentimentResponse.class);
        } catch (Exception ex) {
            double base = ((rating == null ? 3.0 : rating) - 1.0) / 4.0;
            String label = base >= 0.75 ? "positive" : base >= 0.5 ? "neutral" : "negative";
            return new SentimentResponse(Math.max(0.0, Math.min(1.0, base)), label);
        }
    }
}
