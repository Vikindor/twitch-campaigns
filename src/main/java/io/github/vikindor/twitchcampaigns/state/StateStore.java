package io.github.vikindor.twitchcampaigns.state;

import java.io.IOException;

public interface StateStore<T> {
    T load() throws IOException, InterruptedException;

    void save(T state) throws IOException, InterruptedException;
}
