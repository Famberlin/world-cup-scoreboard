package com.sportradar.scoreboard.store;

class InMemoryMatchStoreTest extends MatchStoreContractTest {

    @Override
    protected MatchStore createStore() {
        return new InMemoryMatchStore();
    }
}
