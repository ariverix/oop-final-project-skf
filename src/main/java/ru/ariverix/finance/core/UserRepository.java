package ru.ariverix.finance.core;

import java.io.IOException;

public interface UserRepository {
    void register(String login, String password) throws IOException;

    boolean authenticate(String login, String password);

    boolean exists(String login);
}
