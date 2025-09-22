package com.example.services;

import com.example.client.ledger.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CommandCompletionTracker {

    public Long nextOffset;
    public Map<String, Completion1> completions;

    public CommandCompletionTracker(Long startOffset) {
        this.nextOffset = startOffset;
        this.completions = new HashMap<>();
    }

    public CommandCompletionTracker() {
        this(0L);
    }

    public void observeCompletion(CompletionResponse streamItem) {
        Object subResponse = streamItem.getActualInstance();
        if (subResponse instanceof CompletionResponseOneOf) {
            Completion1 completion = ((CompletionResponseOneOf) subResponse)
                    .getCompletion()
                    .getValue();
            String commandId = completion.getCommandId();
            this.completions.put(commandId, completion);
        } else if (subResponse instanceof CompletionResponseOneOf1) {
            // ignore
        } else if (subResponse instanceof CompletionResponseOneOf2) {
            this.nextOffset = ((CompletionResponseOneOf2) subResponse)
                    .getOffsetCheckpoint()
                    .getValue()
                    .getOffset();
        } else {
            throw new UnsupportedOperationException("Did not know how to handle completion response item " + subResponse);
        }
    }

    public void observeCompletions(List<CompletionStreamResponse> completions) {
        for (CompletionStreamResponse completion : completions) {
            this.observeCompletion(completion.getCompletionResponse());
        }
    }

    public Optional<Status> resultCodeFor(String commandId) {

        Completion1 relevantCompletion = this.completions.get(commandId);

        if (relevantCompletion == null) {
            return Optional.empty();
        }
        Status status = relevantCompletion.getStatus();

        if (status == null) {
            return Optional.empty();
        }
        return Optional.of(status);
    }
}
