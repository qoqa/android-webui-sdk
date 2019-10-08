package com.queue_it.androidsdk;

import androidx.annotation.NonNull;

public interface QueueListener {
    void onQueuePassed(QueuePassedInfo queuePassedInfo);
    void onQueueViewWillOpen();
    void onQueueDisabled();
    void onQueueItUnavailable();
    void onQueueIdChanged(@NonNull String queueId);
    void onError(Error error, String errorMessage);
}
