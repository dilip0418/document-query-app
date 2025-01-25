package com.docqry.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SummarizationService {

    private final PythonServiceClient pythonServiceClient; // Assuming Python handles summarization logic

    /**
     * Summarizes the current context and new query using an AI model.
     *
     * @param newQuery The user's latest query.
     * @return A summarized version of the updated context.
     */
    public String summarizeContext(String newQuery, List<String> relevantChunks) throws Exception {

        // Call the Python summarization service (or an API)
        var summarizedResponse = pythonServiceClient.summarizeText( newQuery, relevantChunks);

        return getSummary(summarizedResponse);
    }

    public String summarizeInitialChunks(List<String> initialChunks) {
        var summarizedResponse = pythonServiceClient.summarizeInitialChunks(initialChunks);
        return summarizedResponse.getOverviewSummary();
    }

    private String getSummary(PythonServiceClientImpl.SummarizationResponse summarizedResponse) {
        var queryFocusedSummary = summarizedResponse.getSummary();

        // may be useful if we can give the user a choice of how they want the context to be built.
        var extractiveSummary = summarizedResponse.getSummary();
        var basicSummary = summarizedResponse.getSummary();

        return queryFocusedSummary;
    }
}
